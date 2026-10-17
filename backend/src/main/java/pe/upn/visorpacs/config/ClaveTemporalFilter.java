package pe.upn.visorpacs.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.upn.visorpacs.sesion.SesionTokenValidator;

/**
 * Una cuenta con clave temporal (restablecida por el ADMIN) solo puede cambiarla, ver su perfil o cerrar sesión.
 * Todo lo demás responde 403 con codigo CLAVE_TEMPORAL. Va en la cadena de seguridad, después de leer el token.
 * No es un @Component a propósito: así no se registra además como filtro suelto del contenedor.
 */
public class ClaveTemporalFilter extends OncePerRequestFilter {

    private static final Set<String> PERMITIDAS = Set.of(
            "POST /api/auth/cambiar-clave", "POST /api/auth/logout", "GET /api/auth/me");

    private final ErrorSeguridadHandler errores;

    public ClaveTemporalFilter(ErrorSeguridadHandler errores) {
        this.errores = errores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaims().get(SesionTokenValidator.CLAIM_CLAVE_TEMPORAL))
                && !PERMITIDAS.contains(request.getMethod() + " " + request.getRequestURI())) {
            errores.claveTemporal(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
