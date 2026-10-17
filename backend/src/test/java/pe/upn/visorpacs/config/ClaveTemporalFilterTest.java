package pe.upn.visorpacs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ClaveTemporalFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaveTemporalFilter filtro = new ClaveTemporalFilter(new ErrorSeguridadHandler(mapper));

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(boolean claveTemporal) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("medico.demo")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("uid", 7).claim("ver", 0);
        if (claveTemporal) b.claim("cc", true);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority("ROLE_MEDICO"))));
    }

    private MockHttpServletResponse enviar(String metodo, String ruta, MockFilterChain cadena) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest(metodo, ruta), res, cadena);
        return res;
    }

    @Test
    void conClaveTemporalBloqueaTodoLoQueNoSeaCambiarClavePerfilOSalir() throws Exception {
        autenticar(true);
        for (String[] r : new String[][] {
                {"GET", "/api/dicomweb/studies"}, {"GET", "/api/admin/usuarios"}, {"POST", "/api/auth/renovar"},
                {"GET", "/api/auth/cambiar-clave"}, {"POST", "/api/auth/me"}}) {
            MockFilterChain cadena = new MockFilterChain();
            MockHttpServletResponse res = enviar(r[0], r[1], cadena);

            assertEquals(403, res.getStatus(), r[0] + " " + r[1]);
            assertEquals("CLAVE_TEMPORAL", mapper.readTree(res.getContentAsString()).get("codigo").asText());
            assertNull(cadena.getRequest(), "la petición no debe llegar a la aplicación");
        }
    }

    @Test
    void conClaveTemporalPermiteCambiarClaveVerElPerfilYCerrarSesion() throws Exception {
        autenticar(true);
        for (String[] r : new String[][] {
                {"POST", "/api/auth/cambiar-clave"}, {"GET", "/api/auth/me"}, {"POST", "/api/auth/logout"}}) {
            MockFilterChain cadena = new MockFilterChain();
            MockHttpServletResponse res = enviar(r[0], r[1], cadena);

            assertEquals(200, res.getStatus(), r[0] + " " + r[1]);
            assertNotNull(cadena.getRequest());
        }
    }

    @Test
    void sinClaveTemporalNoInterfiere() throws Exception {
        autenticar(false);
        MockFilterChain cadena = new MockFilterChain();
        assertEquals(200, enviar("GET", "/api/dicomweb/studies", cadena).getStatus());
        assertNotNull(cadena.getRequest());
    }

    @Test
    void sinAutenticacionNoInterfiere() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        assertEquals(200, enviar("POST", "/api/auth/login", cadena).getStatus());
        assertNotNull(cadena.getRequest());
    }
}
