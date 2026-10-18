package pe.upn.visorpacs.recuperacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteRecuperacionFilterTest {

    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();

    private LimiteRecuperacionFilter filtro(boolean habilitada, int max) {
        return new LimiteRecuperacionFilter(new RecuperacionProperties(habilitada, 15, 3, max, 3600,
                "http://x", "r@x"), reloj, mapper);
    }

    /** Devuelve el estado; 200 si la petición llegó a la aplicación. */
    private int enviar(LimiteRecuperacionFilter f, String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        f.doFilter(req, res, cadena);
        return cadena.getRequest() == null ? res.getStatus() : 200;
    }

    @Test
    void despuesDelMaximoResponde429ConRetryAfterYProblemJson() throws Exception {
        LimiteRecuperacionFilter f = filtro(true, 2);
        assertEquals(200, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));
        assertEquals(200, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/olvide-clave");
        req.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());

        assertEquals(429, res.getStatus());
        assertEquals("3600", res.getHeader("Retry-After"));
        assertTrue(res.getContentType().startsWith("application/problem+json"));
        assertEquals("Demasiados intentos", mapper.readTree(res.getContentAsString()).get("title").asText());
    }

    @Test
    void cadaEndpointTieneSuPropioContador() throws Exception {
        LimiteRecuperacionFilter f = filtro(true, 1);
        assertEquals(200, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));
        assertEquals(429, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));
        assertEquals(200, enviar(f, "POST", "/api/auth/restablecer-clave", "9.9.9.9"));
        assertEquals(429, enviar(f, "POST", "/api/auth/restablecer-clave", "9.9.9.9"));
    }

    @Test
    void otraIpNoSeVeAfectada() throws Exception {
        LimiteRecuperacionFilter f = filtro(true, 1);
        enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9");
        assertEquals(429, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));
        assertEquals(200, enviar(f, "POST", "/api/auth/olvide-clave", "8.8.8.8"));
    }

    @Test
    void conLaFuncionApagadaNoInterviene() throws Exception {
        LimiteRecuperacionFilter f = filtro(false, 1);
        for (int i = 0; i < 5; i++) {
            assertEquals(200, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));
        }
    }

    @Test
    void soloLimitaPostALasDosRutas() throws Exception {
        LimiteRecuperacionFilter f = filtro(true, 1);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, enviar(f, "GET", "/api/auth/olvide-clave", "9.9.9.9"));
            assertEquals(200, enviar(f, "POST", "/api/auth/login", "9.9.9.9"));
        }
    }

    @Test
    void maximoCeroDesactivaElLimite() throws Exception {
        LimiteRecuperacionFilter f = filtro(true, 0);
        for (int i = 0; i < 5; i++) {
            assertEquals(200, enviar(f, "POST", "/api/auth/olvide-clave", "9.9.9.9"));
        }
    }
}
