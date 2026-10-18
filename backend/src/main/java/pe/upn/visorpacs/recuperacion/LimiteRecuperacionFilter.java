package pe.upn.visorpacs.recuperacion;

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
import pe.upn.visorpacs.config.LimiteIntentosPorIp;

/**
 * Límite por IP para POST /api/auth/olvide-clave y /api/auth/restablecer-clave (429 con Retry-After).
 * Cada endpoint tiene su propio contador. Si la función está apagada no interviene: el endpoint responde 404.
 */
@Component
public class LimiteRecuperacionFilter extends OncePerRequestFilter {

    static final String RUTA_SOLICITUD = "/api/auth/olvide-clave";
    static final String RUTA_RESTABLECER = "/api/auth/restablecer-clave";

    private static final Logger log = LoggerFactory.getLogger(LimiteRecuperacionFilter.class);

    private final boolean habilitada;
    private final LimiteIntentosPorIp solicitud;
    private final LimiteIntentosPorIp restablecer;
    private final ObjectMapper mapper;

    public LimiteRecuperacionFilter(RecuperacionProperties props, Clock clock, ObjectMapper mapper) {
        this.habilitada = props.habilitada();
        this.solicitud = new LimiteIntentosPorIp(clock, props.maxPorIp(), props.ventanaIpSegundos());
        this.restablecer = new LimiteIntentosPorIp(clock, props.maxPorIp(), props.ventanaIpSegundos());
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !habilitada || !"POST".equals(request.getMethod())
                || !(RUTA_SOLICITUD.equals(request.getRequestURI()) || RUTA_RESTABLECER.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        LimiteIntentosPorIp limite = RUTA_SOLICITUD.equals(request.getRequestURI()) ? solicitud : restablecer;
        long espera = limite.registrar(request.getRemoteAddr());
        if (espera == 0) {
            chain.doFilter(request, response);
            return;
        }
        if (limite.totalBloqueadas() % 50 == 1) {
            log.warn("Límite de recuperación de clave superado desde {} en {}", request.getRemoteAddr(), request.getRequestURI());
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", "Demasiados intentos");
        cuerpo.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        cuerpo.put("detail", "Demasiadas solicitudes desde tu red. Espera " + espera + " segundos e inténtalo de nuevo.");
        cuerpo.put("instance", request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(espera));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
