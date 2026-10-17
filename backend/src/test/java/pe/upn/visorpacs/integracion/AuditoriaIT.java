package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.upn.visorpacs.auditoria.AuditoriaRepository;

/** HU10: quién accede, cuándo y desde dónde; consulta para el administrador; registro inmutable. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditoriaIT extends IntegracionBase {

    private static final String IP = "198.51.100.7";

    private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder b, String ip) {
        return b.with(r -> { r.setRemoteAddr(ip); return r; });
    }

    private MockHttpServletRequestBuilder consulta(String tokenAdmin, String... paresParametro) {
        MockHttpServletRequestBuilder b = get("/api/admin/auditoria").header("Authorization", bearer(tokenAdmin));
        for (int i = 0; i < paresParametro.length; i += 2) {
            b = b.param(paresParametro[i], paresParametro[i + 1]);
        }
        return b;
    }

    // ---- CA10.2: inicios de sesión, exitosos y fallidos ----

    @Test
    void cadaInicioDeSesionExitosoOFallidoQuedaRegistradoConSuIp() throws Exception {
        Cuenta m = medico();
        mvc.perform(desde(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", "incorrecta-1234"))), IP))
                .andExpect(status().isUnauthorized());
        mvc.perform(desde(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))), IP))
                .andExpect(status().isOk());
        String tAdmin = login(admin().username());

        mvc.perform(consulta(tAdmin, "usuario", m.username(), "tipo", "INICIO_SESION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                // más reciente primero: el éxito, luego el fallo
                .andExpect(jsonPath("$.contenido[0].resultado").value("EXITO"))
                .andExpect(jsonPath("$.contenido[0].ip").value(IP))
                .andExpect(jsonPath("$.contenido[0].idUsuario").value(m.id()))
                .andExpect(jsonPath("$.contenido[0].rol").value("MEDICO"))
                .andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty())
                .andExpect(jsonPath("$.contenido[1].resultado").value("FALLO"))
                .andExpect(jsonPath("$.contenido[1].detalle").value("clave incorrecta"));
    }

    @Test
    void unIntentoConUnUsuarioInexistenteTambienQuedaRegistrado() throws Exception {
        String fantasma = unico("fantasma");
        mvc.perform(desde(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", fantasma, "password", CLAVE))), IP))
                .andExpect(status().isUnauthorized());
        String tAdmin = login(admin().username());

        mvc.perform(consulta(tAdmin, "usuario", fantasma))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].resultado").value("FALLO"))
                .andExpect(jsonPath("$.contenido[0].idUsuario").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].ip").value(IP));
    }

    @Test
    void cerrarSesionQuedaRegistrado() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(t))).andExpect(status().isNoContent());
        String tAdmin = login(admin().username());

        mvc.perform(consulta(tAdmin, "usuario", m.username(), "tipo", "CIERRE_SESION"))
                .andExpect(jsonPath("$.total").value(1));
    }

    // ---- CA10.1: apertura de estudio ----

    @Test
    void laAperturaDeUnEstudioGuardaUsuarioEstudioFechaEIpUnaSolaVezAunqueHayaMuchasPeticiones() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        String estudio = "1.2.826." + UUID.randomUUID().toString().replace("-", "");
        ORTHANC.registrarEstudio(estudio, "PAC-" + UUID.randomUUID());

        for (String ruta : new String[] {"/series", "/series", "/series/1.2.3/instances/1.2.3.4/frames/1", "/series"}) {
            mvc.perform(desde(get("/api/dicomweb/studies/" + estudio + ruta).header("Authorization", bearer(t)), IP))
                    .andExpect(status().isOk());
        }
        String tAdmin = login(admin().username());

        mvc.perform(consulta(tAdmin, "estudio", estudio))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("APERTURA_ESTUDIO"))
                .andExpect(jsonPath("$.contenido[0].resultado").value("EXITO"))
                .andExpect(jsonPath("$.contenido[0].username").value(m.username()))
                .andExpect(jsonPath("$.contenido[0].estudio").value(estudio))
                .andExpect(jsonPath("$.contenido[0].ip").value(IP))
                .andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty());
    }

    @Test
    void listarEstudiosNoCuentaComoAbrirUnEstudio() throws Exception {
        Cuenta m = medico();
        String t = login(m.username());
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(t))).andExpect(status().isOk());
        String tAdmin = login(admin().username());

        mvc.perform(consulta(tAdmin, "usuario", m.username(), "tipo", "APERTURA_ESTUDIO"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void elIntentoDeUnPacienteSobreUnEstudioAjenoQuedaRegistradoComoFallo() throws Exception {
        String propioId = "PAC-" + UUID.randomUUID();
        String ajeno = "1.2.826." + UUID.randomUUID().toString().replace("-", "");
        ORTHANC.registrarEstudio(ajeno, "PAC-" + UUID.randomUUID());
        Cuenta p = paciente(propioId);
        String t = login(p.username());
        mvc.perform(get("/api/dicomweb/studies/" + ajeno + "/series").header("Authorization", bearer(t)))
                .andExpect(status().isForbidden());
        String tAdmin = login(admin().username());

        mvc.perform(consulta(tAdmin, "estudio", ajeno))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("ACCESO_ESTUDIO_DENEGADO"))
                .andExpect(jsonPath("$.contenido[0].resultado").value("FALLO"))
                .andExpect(jsonPath("$.contenido[0].username").value(p.username()));
    }

    // ---- las acciones del administrador también dejan huella ----

    @Test
    void crearYDesactivarCuentasQuedaAuditadoConElNombreDelAdministrador() throws Exception {
        Cuenta a = admin();
        String tAdmin = login(a.username());
        Cuenta objetivo = medico();

        mvc.perform(cambiarEstado(objetivo.id(), false, tAdmin)).andExpect(status().isOk());

        mvc.perform(consulta(tAdmin, "usuario", a.username(), "tipo", "USUARIO_DESACTIVADO"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].rol").value("ADMIN"))
                .andExpect(jsonPath("$.contenido[0].detalle").value("cuenta=" + objetivo.username()));
    }

    // ---- CA10.3: filtros por usuario y fecha ----

    @Test
    void elAdminFiltraPorUsuarioTipoResultadoYFechasYPagina() throws Exception {
        Cuenta m = medico();
        login(m.username());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                .content(cuerpo(Map.of("username", m.username(), "password", "incorrecta-1234"))));
        String tAdmin = login(admin().username());
        Instant ahora = Instant.now();

        mvc.perform(consulta(tAdmin, "usuario", m.username(), "desde", ahora.minusSeconds(3600).toString()))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(consulta(tAdmin, "usuario", m.username(), "desde", ahora.plusSeconds(3600).toString()))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(consulta(tAdmin, "usuario", m.username(), "hasta", ahora.minusSeconds(3600).toString()))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(consulta(tAdmin, "usuario", m.username(), "resultado", "FALLO"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(consulta(tAdmin, "usuario", m.username(), "tamano", "1"))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(consulta(tAdmin, "usuario", "otro.usuario.sin.eventos"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void unFiltroConValoresInvalidosDa400() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(consulta(tAdmin, "tipo", "NO_ES_UN_TIPO")).andExpect(status().isBadRequest());
        mvc.perform(consulta(tAdmin, "desde", "ayer")).andExpect(status().isBadRequest());
    }

    // ---- solo el administrador consulta ----

    @Test
    void soloElAdministradorConsultaLaAuditoria() throws Exception {
        String tMedico = login(medico().username());
        String tPaciente = login(paciente("PAC-" + UUID.randomUUID()).username());
        mvc.perform(get("/api/admin/auditoria").header("Authorization", bearer(tMedico))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria").header("Authorization", bearer(tPaciente))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria")).andExpect(status().isUnauthorized());
    }

    // ---- CA10.4: no existe forma de editar ni borrar ----

    @Test
    void laApiNoOfreceEscrituraSobreLaAuditoria() throws Exception {
        String tAdmin = login(admin().username());
        String h = bearer(tAdmin);
        mvc.perform(post("/api/admin/auditoria").header("Authorization", h)).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/admin/auditoria").header("Authorization", h)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/admin/auditoria").header("Authorization", h)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/admin/auditoria").header("Authorization", h)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/admin/auditoria/1").header("Authorization", h)).andExpect(status().is4xxClientError());
    }

    @Test
    void elRepositorioDeAuditoriaNoTieneOperacionesDeBorrado() {
        for (Method m : AuditoriaRepository.class.getMethods()) {
            String n = m.getName().toLowerCase();
            assertFalse(n.startsWith("delete") || n.startsWith("remove") || n.contains("truncate"),
                    "no debe existir " + m.getName());
        }
        assertTrue(Arrays.stream(AuditoriaRepository.class.getMethods()).anyMatch(m -> m.getName().equals("save")));
    }

    @Test
    void laBaseDeDatosRechazaUpdateYDeleteDeUnEvento() throws Exception {
        login(medico().username()); // garantiza al menos un evento
        Long id = jdbc.queryForObject("SELECT MAX(id_evento) FROM evento_auditoria", Long.class);
        String tipoAntes = jdbc.queryForObject("SELECT tipo FROM evento_auditoria WHERE id_evento = ?", String.class, id);

        DataAccessException alActualizar = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE evento_auditoria SET tipo = 'MANIPULADO' WHERE id_evento = ?", id));
        assertTrue(alActualizar.getMostSpecificCause().getMessage().contains("solo insercion"));
        DataAccessException alBorrar = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM evento_auditoria WHERE id_evento = ?", id));
        assertTrue(alBorrar.getMostSpecificCause().getMessage().contains("solo insercion"));

        assertEquals(tipoAntes, jdbc.queryForObject("SELECT tipo FROM evento_auditoria WHERE id_evento = ?", String.class, id));
    }
}
