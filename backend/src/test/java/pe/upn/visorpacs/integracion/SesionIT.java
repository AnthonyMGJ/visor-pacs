package pe.upn.visorpacs.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** HU12: la sesión caduca y se revoca. Incluye tokens fabricados para cubrir vencimiento y claims faltantes. */
@SpringBootTest
@AutoConfigureMockMvc
class SesionIT extends IntegracionBase {

    @Autowired JwtEncoder encoder;

    private String fabricar(Cuenta c, Instant emitido, Instant vence, Integer uid, Integer version, String emisor) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(emisor).issuedAt(emitido).expiresAt(vence)
                .subject(c.username()).claim("roles", List.of("MEDICO"));
        if (uid != null) claims.claim("uid", uid);
        if (version != null) claims.claim("ver", version);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private void esperar401(String token) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginDeclaraQueElTokenDuraQuinceMinutos() throws Exception {
        Cuenta m = medico();
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.rol").value("MEDICO"));
    }

    @Test
    void meDevuelveElPerfilDeLaCuentaAutenticada() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(m.username()))
                .andExpect(jsonPath("$.rol").value("MEDICO"))
                .andExpect(jsonPath("$.permisos[0]").value("ACCESO_ESTUDIOS"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void cerrarSesionRevocaElTokenYTambienLosOtrosDeLaMismaCuenta() throws Exception {
        Cuenta m = medico();
        String t1 = login(m.username());
        String t2 = login(m.username()); // otro dispositivo

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(t1))).andExpect(status().isNoContent());

        esperar401(t1);
        esperar401(t2);
        // y se puede volver a entrar
        String t3 = login(m.username());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(t3))).andExpect(status().isOk());
    }

    @Test
    void renovarEntregaUnTokenNuevoYUtil() throws Exception {
        Cuenta m = medico();
        String viejo = login(m.username());

        String respuesta = mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(viejo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String nuevo = JsonPath.read(respuesta, "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(nuevo))).andExpect(status().isOk());
    }

    @Test
    void noSePuedeRenovarConUnTokenRevocadoNiVencido() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(t))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(t))).andExpect(status().isUnauthorized());

        // CA12.1: pasado el tiempo sin renovar, el token venció y hay que iniciar sesión de nuevo.
        Instant ahora = Instant.now();
        String vencido = fabricar(m, ahora.minusSeconds(7200), ahora.minusSeconds(3600), m.id(), 0, "visor-pacs");
        mvc.perform(post("/api/auth/renovar").header("Authorization", bearer(vencido))).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenVencidoSeRechazaAunqueLaFirmaSeaValida() throws Exception {
        Cuenta m = medico();
        Instant ahora = Instant.now();
        esperar401(fabricar(m, ahora.minusSeconds(7200), ahora.minusSeconds(3600), m.id(), 0, "visor-pacs"));
        // el mismo token, vigente, sí entra: lo que cambia es solo el vencimiento
        mvc.perform(get("/api/auth/me").header("Authorization",
                        bearer(fabricar(m, ahora, ahora.plusSeconds(900), m.id(), 0, "visor-pacs"))))
                .andExpect(status().isOk());
    }

    @Test
    void sinIdOSinVersionDeSesionElTokenNoSirve() throws Exception {
        Cuenta m = medico();
        Instant ahora = Instant.now();
        esperar401(fabricar(m, ahora, ahora.plusSeconds(900), null, 0, "visor-pacs"));
        esperar401(fabricar(m, ahora, ahora.plusSeconds(900), m.id(), null, "visor-pacs"));
    }

    @Test
    void conVersionDeSesionDistintaOEmisorAjenoElTokenNoSirve() throws Exception {
        Cuenta m = medico();
        Instant ahora = Instant.now();
        esperar401(fabricar(m, ahora, ahora.plusSeconds(900), m.id(), 5, "visor-pacs"));
        esperar401(fabricar(m, ahora, ahora.plusSeconds(900), m.id(), 0, "otro-emisor"));
    }

    @Test
    void unTokenDeUnaCuentaInexistenteNoSirve() throws Exception {
        Cuenta m = medico();
        Instant ahora = Instant.now();
        esperar401(fabricar(m, ahora, ahora.plusSeconds(900), 2147483000, 0, "visor-pacs"));
    }
}
