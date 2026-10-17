package pe.upn.visorpacs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Errores de seguridad (401 y 403) con el mismo formato JSON que el resto de la API
 * (application/problem+json), en lugar de una respuesta vacía. Nunca revela el motivo exacto del rechazo.
 */
@Component
public class ErrorSeguridadHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ErrorSeguridadHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(request, response, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Falta el token, es inválido o venció, o la sesión ya no está vigente.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para realizar esta operación.");
    }

    /** 403 porque la cuenta debe cambiar su clave temporal; el cliente reconoce el código CLAVE_TEMPORAL. */
    public void claveTemporal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN, "Cambio de clave requerido",
                "Tu clave es temporal: cámbiala para continuar.", "CLAVE_TEMPORAL");
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado,
                          String titulo, String detalle) throws IOException {
        escribir(request, response, estado, titulo, detalle, null);
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado,
                          String titulo, String detalle, String codigo) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", titulo);
        cuerpo.put("status", estado.value());
        cuerpo.put("detail", detalle);
        cuerpo.put("instance", request.getRequestURI());
        if (codigo != null) {
            cuerpo.put("codigo", codigo);
        }
        response.setStatus(estado.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        mapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
