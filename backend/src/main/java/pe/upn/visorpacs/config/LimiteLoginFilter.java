package pe.upn.visorpacs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita las solicitudes POST /api/auth/login por IP (429 con Retry-After). La IP es la que ve el servidor:
 * detrás de Caddy, Tomcat toma X-Forwarded-For solo de proxies de la red interna (forward-headers-strategy
 * native), así que un cliente externo no puede falsearla con esa cabecera.
 */
@Component
public class LimiteLoginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteLoginFilter.class);

    private final LimiteIntentosPorIp limite;
    private final ObjectMapper mapper;

    public LimiteLoginFilter(LimiteLoginProperties props, Clock clock, ObjectMapper mapper) {
        this.limite = new LimiteIntentosPorIp(clock, props.maxSolicitudes(), props.ventanaSegundos());
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !limite.activo() || !"POST".equals(request.getMethod())
                || !"/api/auth/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long espera = limite.registrar(request.getRemoteAddr());
        if (espera == 0) {
            chain.doFilter(request, response);
            return;
        }
        if (limite.totalBloqueadas() % 50 == 1) { // no inundar el log durante un ataque
            log.warn("Límite de inicios de sesión superado desde {} ({} rechazos acumulados)",
                    request.getRemoteAddr(), limite.totalBloqueadas());
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", "Demasiados intentos");
        cuerpo.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        cuerpo.put("detail", "Demasiados intentos de inicio de sesión desde tu red. Espera " + espera + " segundos e inténtalo de nuevo.");
        cuerpo.put("instance", request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(espera));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
