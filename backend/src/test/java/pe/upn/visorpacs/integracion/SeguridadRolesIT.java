package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** Roles y permisos de extremo a extremo (HU01, HU05, HU08/CA08.4, HU09, HU12): seguridad real, MySQL real. */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadRolesIT extends IntegracionBase {

    private static final String PROBLEMA = "application/problem+json";

    // ---- sin sesión ----

    @Test
    void sinTokenDa401ConCuerpoJsonUniforme() throws Exception {
        mvc.perform(get("/api/dicomweb/studies"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void tokenBasuraDa401() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer esto.no.es.un.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA));
    }

    @Test
    void elLoginEsPublicoYValidaElCuerpo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void credencialesIncorrectasDanElMismoMensajeParaUsuarioInexistenteYClaveErronea() throws Exception {
        Cuenta m = medico();
        String sinUsuario = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", "no.existe." + UUID.randomUUID(), "password", CLAVE))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String claveMala = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", "otra-clave-cualquiera"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertTrue(sinUsuario.contains("Usuario o contraseña incorrectos."));
        assertTrue(claveMala.contains("Usuario o contraseña incorrectos."));
    }

    @Test
    void tras5IntentosFallidosLaCuentaQuedaBloqueadaAunConLaClaveCorrecta() throws Exception {
        Cuenta m = medico();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(JSON)
                            .content(cuerpo(Map.of("username", m.username(), "password", "incorrecta-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.title").value("Cuenta bloqueada"));
    }

    // ---- área de administración (CA08.4) ----

    @Test
    void soloElAdministradorEntraAlAreaDeAdministracion() throws Exception {
        String tMedico = login(medico().username());
        String tPaciente = login(paciente("PAC-" + UUID.randomUUID()).username());
        String tAdmin = login(admin().username());

        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(tMedico)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(tPaciente)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk());
    }

    // ---- el ADMIN no ve imágenes ----

    @Test
    void elAdministradorNuncaVeImagenesYLaPeticionNoLlegaAOrthanc() throws Exception {
        String tAdmin = login(admin().username());
        int antes = ORTHANC.peticiones().size();

        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isForbidden());

        assertTrue(ORTHANC.peticiones().size() == antes, "el proxy no debe consultar Orthanc para un ADMIN");
    }

    // ---- MEDICO ----

    @Test
    void elMedicoConPermisoListaEstudiosYElProxyUsaLasCredencialesDelBackend() throws Exception {
        String t = login(medico().username());
        String estudio = "1.2.826." + UUID.randomUUID().toString().replace("-", "");
        ORTHANC.registrarEstudio(estudio, "PAC-" + UUID.randomUUID());

        mvc.perform(get("/api/dicomweb/studies").queryParam("StudyInstanceUID", estudio)
                        .header("Authorization", bearer(t)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0]['0020000D'].Value[0]").value(estudio));

        assertTrue(ORTHANC.autorizaciones().stream().anyMatch(a -> a.startsWith("Basic ")));
        assertFalse(ORTHANC.autorizaciones().stream().anyMatch(a -> a.contains(t)),
                "el token del médico nunca se reenvía a Orthanc");
    }

    @Test
    void siSeLeQuitaElPermisoDeEstudiosLaSiguienteSolicitudRecibe403() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        jdbc.update("DELETE FROM usuario_permiso WHERE id_usuario = ?", m.id());

        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(t)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA));
    }

    @Test
    void unMedicoSinPermisoNoPuedeIniciarSesion() throws Exception {
        Cuenta m = medico();
        jdbc.update("DELETE FROM usuario_permiso WHERE id_usuario = ?", m.id());

        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))))
                .andExpect(status().isUnauthorized());
    }

    // ---- PACIENTE (HU09) ----

    @Test
    void elPacienteSoloVeSusEstudiosYNoPuedeCambiarElFiltro() throws Exception {
        String propioId = "PAC-" + UUID.randomUUID();
        String ajenoId = "PAC-" + UUID.randomUUID();
        String propio = "1.2.826." + UUID.randomUUID().toString().replace("-", "");
        String ajeno = "1.2.826." + UUID.randomUUID().toString().replace("-", "");
        ORTHANC.registrarEstudio(propio, propioId);
        ORTHANC.registrarEstudio(ajeno, ajenoId);
        String t = login(paciente(propioId).username());

        // CA09.1: aunque pida el PatientID de otro, el proxy fuerza el suyo.
        mvc.perform(get("/api/dicomweb/studies").queryParam("PatientID", ajenoId).header("Authorization", bearer(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]['0020000D'].Value[0]").value(propio));
        String ultima = ORTHANC.peticiones().get(ORTHANC.peticiones().size() - 1);
        assertTrue(ultima.contains("PatientID=" + propioId));
        assertFalse(ultima.contains(ajenoId));

        // CA09.3: su propio estudio se abre.
        mvc.perform(get("/api/dicomweb/studies/" + propio + "/series").header("Authorization", bearer(t)))
                .andExpect(status().isOk());
        // CA09.2: el de otra persona, acceso denegado.
        mvc.perform(get("/api/dicomweb/studies/" + ajeno + "/series").header("Authorization", bearer(t)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA));
        // /series e /instances sueltos permitirían saltarse el filtro por estudio.
        mvc.perform(get("/api/dicomweb/series").header("Authorization", bearer(t)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dicomweb/instances").header("Authorization", bearer(t)))
                .andExpect(status().isForbidden());
    }

    // ---- errores uniformes de ruta y método ----

    @Test
    void unaRutaInexistenteDa404EnFormatoJson() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/admin/no-existe").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unMetodoNoSoportadoDa405EnFormatoJson() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(delete("/api/admin/usuarios").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unaRutaDicomwebQueNoEsDeLecturaDa400() throws Exception {
        String t = login(medico().username());
        mvc.perform(get("/api/dicomweb/patients").header("Authorization", bearer(t)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ruta no permitida"));
    }

    @Test
    void laDocumentacionOpenApiEstaDisponibleSinSesion() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/usuarios']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/auditoria']").exists());
    }

    @Test
    void elHealthEsPublicoYReflejaAOrthancYLaBase() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
