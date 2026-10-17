package pe.upn.visorpacs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ErrorSeguridadHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ErrorSeguridadHandler handler = new ErrorSeguridadHandler(mapper);

    @Test
    void sinTokenOTokenInvalidoDa401ConCuerpoJson() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/usuarios");
        req.setRequestURI("/api/admin/usuarios");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.commence(req, res, new BadCredentialsException("token vencido por dentro"));

        assertEquals(401, res.getStatus());
        assertEquals("Bearer", res.getHeader("WWW-Authenticate"));
        assertTrue(res.getContentType().startsWith("application/problem+json"));
        JsonNode cuerpo = mapper.readTree(res.getContentAsString());
        assertEquals("No autenticado", cuerpo.get("title").asText());
        assertEquals(401, cuerpo.get("status").asInt());
        assertEquals("/api/admin/usuarios", cuerpo.get("instance").asText());
        assertTrue(!res.getContentAsString().contains("por dentro"), "no debe filtrar el motivo interno");
    }

    @Test
    void faltaDePermisoDa403ConCuerpoJson() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/usuarios");
        req.setRequestURI("/api/admin/usuarios");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.handle(req, res, new AccessDeniedException("falta ROLE_ADMIN"));

        assertEquals(403, res.getStatus());
        JsonNode cuerpo = mapper.readTree(res.getContentAsString());
        assertEquals("Acceso denegado", cuerpo.get("title").asText());
        assertEquals(403, cuerpo.get("status").asInt());
        assertTrue(!res.getContentAsString().contains("ROLE_ADMIN"));
    }
}
