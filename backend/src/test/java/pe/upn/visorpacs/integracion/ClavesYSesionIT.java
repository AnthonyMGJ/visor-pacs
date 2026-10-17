package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * HU12 completa: cambio de clave, restablecimiento por el ADMIN (clave temporal), cierre de un solo token
 * y tope absoluto de la sesión, con MySQL real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClavesYSesionIT extends IntegracionBase {

    private static final String NUEVA = "Otra-clave-segura-456";

    @Autowired JwtEncoder encoder;

    private void esperar401(String token) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    private void esperarValido(String token) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    private String cambiarClave(String token, String actual, String nueva) throws Exception {
        return mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(token))
                        .content(cuerpo(Map.of("claveActual", actual, "claveNueva", nueva))))
                .andReturn().getResponse().getContentAsString();
    }

    private String loginCon(String username, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", username, "password", clave))))
                .andReturn().getResponse().getContentAsString();
    }

    // ---- cierre de un solo token ----

    @Test
    void cerrarSoloEstaSesionDejaVigenteElOtroDispositivo() throws Exception {
        Cuenta m = medico();
        String t1 = login(m.username());
        String t2 = login(m.username());

        mvc.perform(post("/api/auth/logout").param("soloEsta", "true").header("Authorization", bearer(t1)))
                .andExpect(status().isNoContent());

        esperar401(t1);
        esperarValido(t2);
    }

    @Test
    void laRevocacionIndividualQuedaEnLaBaseParaSobrevivirAUnReinicio() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        mvc.perform(post("/api/auth/logout").param("soloEsta", "true").header("Authorization", bearer(t)))
                .andExpect(status().isNoContent());

        Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM token_revocado WHERE id_usuario = ?", Integer.class, m.id());
        assertEquals(1, filas);
    }

    // ---- cambio de clave ----

    @Test
    void cambiarLaClaveCierraLasOtrasSesionesYDaUnTokenNuevo() throws Exception {
        Cuenta m = medico();
        String viejo = login(m.username());
        String otroDispositivo = login(m.username());

        String respuesta = cambiarClave(viejo, CLAVE, NUEVA);
        String nuevo = JsonPath.read(respuesta, "$.accessToken");

        esperar401(viejo);
        esperar401(otroDispositivo);
        esperarValido(nuevo);
        // la clave anterior ya no entra y la nueva sí
        assertEquals(401, mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(cuerpo(Map.of("username", m.username(), "password", CLAVE)))).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(cuerpo(Map.of("username", m.username(), "password", NUEVA)))).andReturn().getResponse().getStatus());
    }

    @Test
    void conLaClaveActualIncorrectaDa400YLaSesionSigueVigente() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());

        mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(t))
                        .content(cuerpo(Map.of("claveActual", "no-es-esta-clave", "claveNueva", NUEVA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Clave actual incorrecta"));

        esperarValido(t); // 400, no 401: el cliente no debe sacar al usuario de su sesión
        assertEquals(1, jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id_usuario = ?", Integer.class, m.id()));
    }

    @Test
    void repetirLaClaveActualIncorrectaBloqueaLaCuenta() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(t))
                    .content(cuerpo(Map.of("claveActual", "no-es-esta-clave", "claveNueva", NUEVA))));
        }
        mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(t))
                        .content(cuerpo(Map.of("claveActual", CLAVE, "claveNueva", NUEVA))))
                .andExpect(status().isLocked());
    }

    @Test
    void rechazaClavesNuevasCortasOIguales() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(t))
                        .content(cuerpo(Map.of("claveActual", CLAVE, "claveNueva", "corta"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(t))
                        .content(cuerpo(Map.of("claveActual", CLAVE, "claveNueva", CLAVE))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON).header("Authorization", bearer(t))
                        .content("{}"))
                .andExpect(status().isBadRequest());
        esperarValido(t);
    }

    @Test
    void sinTokenNoSePuedeCambiarLaClave() throws Exception {
        mvc.perform(post("/api/auth/cambiar-clave").contentType(JSON)
                        .content(cuerpo(Map.of("claveActual", CLAVE, "claveNueva", NUEVA))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elCambioDeClaveQuedaEnLaAuditoriaSinLaClave() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        cambiarClave(t, CLAVE, NUEVA);

        List<Map<String, Object>> eventos = jdbc.queryForList(
                "SELECT tipo, resultado, detalle FROM evento_auditoria WHERE username = ? AND tipo = 'CLAVE_CAMBIADA'", m.username());
        assertEquals(1, eventos.size());
        assertEquals("EXITO", eventos.get(0).get("resultado"));
        String todo = eventos.toString();
        assertFalse(todo.contains(CLAVE) || todo.contains(NUEVA));
    }

    // ---- restablecimiento por el ADMIN ----

    private int idDe(Cuenta c) {
        return c.id();
    }

    private String restablecer(String tokenAdmin, int id) throws Exception {
        String r = mvc.perform(post("/api/admin/usuarios/" + id + "/restablecer-clave")
                        .header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claveTemporal").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.claveTemporal");
    }

    @Test
    void laClaveTemporalObligaACambiarlaAntesDeUsarCualquierOtraCosa() throws Exception {
        Cuenta m = medico();
        String tAdmin = login(admin().username());
        String sesionAbierta = login(m.username());

        String temporal = restablecer(tAdmin, idDe(m));

        esperar401(sesionAbierta); // el restablecimiento cierra las sesiones abiertas
        assertEquals(401, mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(cuerpo(Map.of("username", m.username(), "password", CLAVE)))).andReturn().getResponse().getStatus());

        String loginTemporal = loginCon(m.username(), temporal);
        assertTrue((Boolean) JsonPath.read(loginTemporal, "$.debeCambiarClave"));
        String tTemporal = JsonPath.read(loginTemporal, "$.accessToken");

        // con la clave temporal solo se puede ver el perfil, cambiar la clave o salir
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tTemporal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CLAVE_TEMPORAL"));
        mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(tTemporal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CLAVE_TEMPORAL"));
        esperarValido(tTemporal);

        // al cambiarla, el token nuevo ya no lleva la marca y funciona con normalidad
        String definitivo = JsonPath.read(cambiarClave(tTemporal, temporal, NUEVA), "$.accessToken");
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(definitivo)))
                .andExpect(status().isOk());
        assertFalse((Boolean) JsonPath.read(loginCon(m.username(), NUEVA), "$.debeCambiarClave"));
    }

    @Test
    void restablecerLevantaElBloqueoDeLaCuenta() throws Exception {
        Cuenta m = medico();
        String tAdmin = login(admin().username());
        for (int i = 0; i < 5; i++) {
            loginCon(m.username(), "clave-equivocada-123");
        }
        assertEquals(423, mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(cuerpo(Map.of("username", m.username(), "password", CLAVE)))).andReturn().getResponse().getStatus());

        String temporal = restablecer(tAdmin, idDe(m));

        assertEquals(200, mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(cuerpo(Map.of("username", m.username(), "password", temporal)))).andReturn().getResponse().getStatus());
    }

    @Test
    void soloUnAdminPuedeRestablecerYNuncaSuPropiaClave() throws Exception {
        Cuenta m = medico();
        Cuenta otro = medico();
        Cuenta a = admin();
        String tMedico = login(m.username());
        String tAdmin = login(a.username());

        mvc.perform(post("/api/admin/usuarios/" + idDe(otro) + "/restablecer-clave").header("Authorization", bearer(tMedico)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/usuarios/" + idDe(otro) + "/restablecer-clave"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/usuarios/" + idDe(a) + "/restablecer-clave").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/usuarios/2147483000/restablecer-clave").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elRestablecimientoQuedaAuditadoSinLaClaveTemporal() throws Exception {
        Cuenta m = medico();
        Cuenta a = admin();
        String temporal = restablecer(login(a.username()), idDe(m));

        List<Map<String, Object>> eventos = jdbc.queryForList(
                "SELECT username, detalle FROM evento_auditoria WHERE tipo = 'CLAVE_RESTABLECIDA' AND username = ?", a.username());
        assertEquals(1, eventos.size());
        assertEquals("cuenta=" + m.username(), eventos.get(0).get("detalle"));
        assertFalse(eventos.toString().contains(temporal));
    }

    // ---- tope absoluto de la sesión ----

    private String fabricarConInicio(Cuenta c, Instant inicio) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("visor-pacs").issuedAt(ahora).expiresAt(ahora.plusSeconds(900))
                .subject(c.username()).id(java.util.UUID.randomUUID().toString()).claim("roles", List.of("MEDICO"))
                .claim("uid", c.id()).claim("ver", 0).claim("ses", inicio.getEpochSecond()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void pasadasLasOchoHorasDeSesionYaNoSePuedeRenovar() throws Exception {
        Cuenta m = medico();
        String reciente = fabricarConInicio(m, Instant.now().minusSeconds(7 * 3600));
        String excedido = fabricarConInicio(m, Instant.now().minusSeconds(9 * 3600));

        // el token sigue siendo válido para trabajar hasta que venza (15 min), pero no se renueva
        esperarValido(excedido);
        mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(excedido))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(reciente)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("accessToken")));
    }

    @Test
    void renovarConservaElInicioDeLaSesionOriginal() throws Exception {
        Cuenta m = medico();
        Instant inicio = Instant.now().minusSeconds(7 * 3600);
        String viejo = fabricarConInicio(m, inicio);

        String nuevo = JsonPath.read(mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(viejo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");

        // el token renovado también conserva el inicio: tras otra hora de renovar ya no se podría
        String cuerpoJwt = new String(java.util.Base64.getUrlDecoder().decode(nuevo.split("\\.")[1]));
        long ses = ((Number) JsonPath.read(cuerpoJwt, "$.ses")).longValue();
        assertEquals(inicio.getEpochSecond(), ses);
    }
}
