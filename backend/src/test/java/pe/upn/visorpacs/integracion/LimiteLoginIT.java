package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Límite de inicios de sesión por IP con la aplicación completa. Usa su propio contexto con un máximo bajo
 * (el resto de las pruebas de integración trabajan con el límite apagado para no chocar entre sí).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.limite-login.max-solicitudes=4", "app.limite-login.ventana-segundos=60"})
class LimiteLoginIT extends IntegracionBase {

    private int intentar(String ip, String usuario) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(JSON).with(r -> { r.setRemoteAddr(ip); return r; })
                        .content(cuerpo(Map.of("username", usuario, "password", "clave-equivocada-123"))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void despuesDelMaximoDeSolicitudesLaIpRecibe429ConRetryAfter() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertEquals(401, intentar("203.0.113.7", "no.existe." + i)); // cuentas distintas: no es el bloqueo por cuenta
        }
        mvc.perform(post("/api/auth/login").contentType(JSON).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                        .content(cuerpo(Map.of("username", "otra.cuenta", "password", "x"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Demasiados intentos"));
    }

    @Test
    void otraIpSigueEntrandoYElRestoDeLaApiNoSeLimita() throws Exception {
        for (int i = 0; i < 6; i++) {
            intentar("203.0.113.50", "no.existe");
        }
        assertEquals(401, intentar("198.51.100.20", "no.existe")); // otra IP: responde lo normal, no 429
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/actuator/health").with(r -> { r.setRemoteAddr("203.0.113.50"); return r; }))
                    .andExpect(status().is2xxSuccessful());
        }
    }
}
