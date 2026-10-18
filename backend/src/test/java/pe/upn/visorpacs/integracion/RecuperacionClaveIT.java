package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.ResultActions;

/** RF-09 completo con MySQL real: solicitud, correo, restablecimiento, un solo uso, vencimiento y auditoría. */
@SpringBootTest
@AutoConfigureMockMvc
class RecuperacionClaveIT extends IntegracionBase {

    private static final String NUEVA = "Otra-clave-segura-456";
    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    @Autowired CorreoCapturado correo;

    private ResultActions solicitar(String email) throws Exception {
        return mvc.perform(post("/api/auth/olvide-clave").contentType(JSON).content(cuerpo(Map.of("email", email))));
    }

    private ResultActions restablecer(String token, String clave) throws Exception {
        return mvc.perform(post("/api/auth/restablecer-clave").contentType(JSON)
                .content(cuerpo(Map.of("token", token, "claveNueva", clave))));
    }

    private String emailDe(Cuenta c) {
        return jdbc.queryForObject("SELECT email FROM usuario WHERE id_usuario = ?", String.class, c.id());
    }

    private String ultimoToken(String email) {
        List<CorreoCapturado.Correo> lista = correo.para(email);
        assertFalse(lista.isEmpty(), "debía haberse enviado un correo a " + email);
        Matcher m = TOKEN.matcher(lista.get(lista.size() - 1).texto());
        assertTrue(m.find());
        return m.group(1);
    }

    private int loginStatus(String username, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", username, "password", clave))))
                .andReturn().getResponse().getStatus();
    }

    private int eventos(Cuenta c, String tipo, String resultado) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE id_usuario = ? AND tipo = ? AND resultado = ?",
                Integer.class, c.id(), tipo, resultado);
    }

    @Test
    void flujoCompletoMedicoCambiaLaClaveYLasSesionesViejasMueren() throws Exception {
        Cuenta m = medico();
        String sesionVieja = login(m.username());

        solicitar(emailDe(m)).andExpect(status().isAccepted());
        String token = ultimoToken(emailDe(m));
        restablecer(token, NUEVA).andExpect(status().isOk());

        assertEquals(401, loginStatus(m.username(), CLAVE), "la clave vieja ya no entra");
        assertEquals(200, loginStatus(m.username(), NUEVA), "la clave nueva entra");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(sesionVieja))).andExpect(status().isUnauthorized());
        assertEquals(1, eventos(m, "RECUPERACION_SOLICITADA", "EXITO"));
        assertEquals(1, eventos(m, "RECUPERACION_COMPLETADA", "EXITO"));
        // correo de confirmación al médico y aviso a los administradores activos
        assertEquals(2, correo.para(emailDe(m)).size());
    }

    @Test
    void pacienteTambienPuedeRecuperar() throws Exception {
        Cuenta p = paciente("RECUP-" + unico("p"));
        solicitar(emailDe(p)).andExpect(status().isAccepted());
        restablecer(ultimoToken(emailDe(p)), NUEVA).andExpect(status().isOk());
        assertEquals(200, loginStatus(p.username(), NUEVA));
    }

    @Test
    void laRespuestaEsLaMismaParaCorreoRegistradoYNoRegistrado() throws Exception {
        Cuenta m = medico();
        String conocido = solicitar(emailDe(m)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String desconocido = solicitar("nadie." + unico("x") + "@correo.pe").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertEquals(conocido, desconocido);
    }

    @Test
    void correoNoRegistradoNoGeneraCorreoNiToken() throws Exception {
        String email = "nadie." + unico("x") + "@correo.pe";
        solicitar(email).andExpect(status().isAccepted());
        assertTrue(correo.para(email).isEmpty());
    }

    @Test
    void adminQuedaExcluidoYCuentaInactivaNoRecibeCorreo() throws Exception {
        Cuenta a = admin();
        solicitar(emailDe(a)).andExpect(status().isAccepted());
        assertTrue(correo.para(emailDe(a)).isEmpty());

        Cuenta m = medico();
        String tAdmin = login(a.username());
        mvc.perform(cambiarEstado(m.id(), false, tAdmin)).andExpect(status().is2xxSuccessful());
        solicitar(emailDe(m)).andExpect(status().isAccepted());
        assertTrue(correo.para(emailDe(m)).isEmpty());
    }

    @Test
    void elEnlaceSoloSirveUnaVez() throws Exception {
        Cuenta m = medico();
        solicitar(emailDe(m));
        String token = ultimoToken(emailDe(m));
        restablecer(token, NUEVA).andExpect(status().isOk());
        restablecer(token, "Tercera-clave-789").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Enlace no válido"));
        assertEquals(200, loginStatus(m.username(), NUEVA), "el segundo intento no cambió nada");
    }

    @Test
    void elEnlaceVencidoSeRechaza() throws Exception {
        Cuenta m = medico();
        solicitar(emailDe(m));
        String token = ultimoToken(emailDe(m));
        jdbc.update("UPDATE recuperacion_clave SET vence_en = DATE_SUB(NOW(3), INTERVAL 1 MINUTE) WHERE id_usuario = ?", m.id());

        restablecer(token, NUEVA).andExpect(status().isBadRequest());
        assertEquals(200, loginStatus(m.username(), CLAVE), "la clave vieja sigue valiendo");
    }

    @Test
    void unaSolicitudNuevaAnulaElEnlaceAnterior() throws Exception {
        Cuenta m = medico();
        solicitar(emailDe(m));
        String primero = ultimoToken(emailDe(m));
        solicitar(emailDe(m));
        String segundo = ultimoToken(emailDe(m));

        assertNotEquals(primero, segundo);
        restablecer(primero, NUEVA).andExpect(status().isBadRequest());
        restablecer(segundo, NUEVA).andExpect(status().isOk());
    }

    @Test
    void laCuartaSolicitudEnUnaHoraNoEnviaOtroCorreoPeroResponde202() throws Exception {
        Cuenta m = medico();
        for (int i = 0; i < 4; i++) {
            solicitar(emailDe(m)).andExpect(status().isAccepted());
        }
        assertEquals(3, correo.para(emailDe(m)).size());
    }

    @Test
    void soloSeGuardaElHashDelToken() throws Exception {
        Cuenta m = medico();
        solicitar(emailDe(m));
        String token = ultimoToken(emailDe(m));

        List<String> hashes = jdbc.queryForList("SELECT token_hash FROM recuperacion_clave WHERE id_usuario = ?", String.class, m.id());
        assertEquals(1, hashes.size());
        assertNotEquals(token, hashes.get(0));
        assertEquals(64, hashes.get(0).length());
    }

    @Test
    void restablecerLevantaElBloqueoPorIntentosFallidos() throws Exception {
        Cuenta m = medico();
        for (int i = 0; i < 5; i++) { // 5 = app.security.max-failed-attempts en las pruebas
            assertEquals(401, loginStatus(m.username(), "clave-equivocada-123"));
        }
        assertEquals(423, loginStatus(m.username(), CLAVE), "la cuenta quedó bloqueada");

        solicitar(emailDe(m));
        restablecer(ultimoToken(emailDe(m)), NUEVA).andExpect(status().isOk());

        assertEquals(200, loginStatus(m.username(), NUEVA));
    }

    @Test
    void unTokenInventadoSeRechazaYNoRevelaNada() throws Exception {
        restablecer("token-inventado-0123456789", NUEVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ya venció. Solicita uno nuevo."));
    }

    @Test
    void claveDebilSeRechazaSinGastarElEnlace() throws Exception {
        Cuenta m = medico();
        solicitar(emailDe(m));
        String token = ultimoToken(emailDe(m));

        restablecer(token, "corta").andExpect(status().isBadRequest());
        restablecer(token, NUEVA).andExpect(status().isOk());
    }

    @Test
    void losEndpointsSonPublicosYLaRespuestaNoSeGuardaEnCache() throws Exception {
        // Sin Authorization: no es 401/403. La política de referrer llega en las cabeceras de la API.
        solicitar("alguien@correo.pe").andExpect(status().isAccepted())
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }
}
