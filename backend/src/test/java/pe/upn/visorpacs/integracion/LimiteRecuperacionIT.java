package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Límite por IP de RF-09 con la aplicación completa (contexto propio con máximo bajo). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.recuperacion.max-por-ip=3", "app.recuperacion.ventana-ip-segundos=3600"})
class LimiteRecuperacionIT extends IntegracionBase {

    private int pedir(String ip, String email) throws Exception {
        return mvc.perform(post("/api/auth/olvide-clave").contentType(JSON).with(r -> { r.setRemoteAddr(ip); return r; })
                        .content(cuerpo(Map.of("email", email))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void laCuartaSolicitudDesdeLaMismaIpRecibe429YOtraIpNo() throws Exception {
        String ip = "203.0.113.7";
        for (int i = 0; i < 3; i++) {
            assertEquals(202, pedir(ip, "a" + i + "@correo.pe"));
        }
        assertEquals(429, pedir(ip, "otro@correo.pe"));
        assertEquals(202, pedir("203.0.113.8", "otro@correo.pe"));
    }
}
