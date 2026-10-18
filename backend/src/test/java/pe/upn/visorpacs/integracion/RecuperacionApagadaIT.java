package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Con RECUPERACION_HABILITADA=false (valor por defecto del producto) la función no existe: 404 y ningún correo. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.recuperacion.habilitada=false")
class RecuperacionApagadaIT extends IntegracionBase {

    @Autowired CorreoCapturado correo;

    @Test
    void ambosEndpointsResponden404YNoSeEnviaNada() throws Exception {
        Cuenta m = medico();
        String email = jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario = ?", String.class, m.id());

        mvc.perform(post("/api/auth/olvide-clave").contentType(JSON).content(cuerpo(Map.of("email", email))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/restablecer-clave").contentType(JSON)
                        .content(cuerpo(Map.of("token", "x", "claveNueva", "Otra-clave-segura-456"))))
                .andExpect(status().isNotFound());

        assertTrue(correo.para(email).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recuperacion_clave WHERE id_usuario = ?", Integer.class, m.id()));
    }
}
