package pe.upn.visorpacs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteLoginFilterTest {

    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();

    private LimiteLoginFilter filtro(int max) {
        return new LimiteLoginFilter(new LimiteLoginProperties(max, 60), reloj, mapper);
    }

    private MockHttpServletResponse enviar(LimiteLoginFilter f, String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        f.doFilter(req, res, cadena);
        res.setCommitted(false);
        if (cadena.getRequest() == null) {
            res.setHeader("X-Detenido", "si");
        }
        return res;
    }

    @Test
    void despuesDelMaximoResponde429ConRetryAfterYProblemJson() throws Exception {
        LimiteLoginFilter f = filtro(2);
        assertEquals(200, enviar(f, "POST", "/api/auth/login", "9.9.9.9").getStatus());
        assertEquals(200, enviar(f, "POST", "/api/auth/login", "9.9.9.9").getStatus());

        MockHttpServletResponse res = enviar(f, "POST", "/api/auth/login", "9.9.9.9");

        assertEquals(429, res.getStatus());
        assertEquals("60", res.getHeader("Retry-After"));
        assertTrue(res.getContentType().startsWith("application/problem+json"));
        assertEquals("Demasiados intentos", mapper.readTree(res.getContentAsString()).get("title").asText());
        assertEquals("si", res.getHeader("X-Detenido")); // la petición no llegó a la aplicación
    }

    @Test
    void otraIpNoSeVeAfectada() throws Exception {
        LimiteLoginFilter f = filtro(1);
        enviar(f, "POST", "/api/auth/login", "9.9.9.9");
        assertEquals(429, enviar(f, "POST", "/api/auth/login", "9.9.9.9").getStatus());
        assertEquals(200, enviar(f, "POST", "/api/auth/login", "8.8.8.8").getStatus());
    }

    @Test
    void soloLimitaElInicioDeSesion() throws Exception {
        LimiteLoginFilter f = filtro(1);
        for (int i = 0; i < 5; i++) {
            assertEquals(200, enviar(f, "GET", "/api/auth/me", "9.9.9.9").getStatus());
            assertEquals(200, enviar(f, "POST", "/api/auth/renovar", "9.9.9.9").getStatus());
            assertEquals(200, enviar(f, "GET", "/api/auth/login", "9.9.9.9").getStatus()); // otro método
        }
        assertNull(enviar(f, "GET", "/api/auth/me", "9.9.9.9").getHeader("Retry-After"));
    }

    @Test
    void conMaximoCeroNoLimitaNada() throws Exception {
        LimiteLoginFilter f = filtro(0);
        for (int i = 0; i < 50; i++) {
            assertEquals(200, enviar(f, "POST", "/api/auth/login", "9.9.9.9").getStatus());
        }
        assertNotNull(f);
    }
}
