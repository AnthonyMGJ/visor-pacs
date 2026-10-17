package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** HU08 (gestión de médicos y permisos) y HU12 (revocación al desactivar), con base de datos real. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUsuariosIT extends IntegracionBase {

    private Map<String, Object> alta(String username, String rol) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", username);
        m.put("clave", CLAVE);
        m.put("nombres", "Carlos");
        m.put("apellidos", "Mendoza");
        m.put("email", username + "@clinica.pe");
        m.put("rol", rol);
        return m;
    }

    private int registrar(String tokenAdmin, Map<String, Object> alta) throws Exception {
        String respuesta = mvc.perform(post("/api/admin/usuarios").contentType(JSON)
                        .header("Authorization", bearer(tokenAdmin)).content(cuerpo(alta)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(respuesta, "$.id")).intValue();
    }

    // ---- CA08.1: registrar, editar, desactivar ----

    @Test
    void elAdminRegistraUnMedicoQueLuegoPuedeIniciarSesion() throws Exception {
        String tAdmin = login(admin().username());
        String nuevo = unico("nuevo.medico");

        mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(alta(nuevo, "medico"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(nuevo))
                .andExpect(jsonPath("$.rol").value("MEDICO"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.permisos[0]").value("ACCESO_ESTUDIOS"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.clave").doesNotExist());

        login(nuevo); // CA08.1: el cambio quedó guardado y la cuenta funciona
    }

    @Test
    void elAdminRegistraUnPacienteConSuPatientId() throws Exception {
        String tAdmin = login(admin().username());
        String nuevo = unico("nuevo.paciente");
        Map<String, Object> alta = alta(nuevo, "PACIENTE");

        // sin PatientID no se puede enlazar a sus estudios
        mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(alta)))
                .andExpect(status().isBadRequest());

        alta.put("dicomPacienteId", "PAC-" + UUID.randomUUID());
        mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(alta)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("PACIENTE"))
                .andExpect(jsonPath("$.dicomPacienteId").value(alta.get("dicomPacienteId")))
                .andExpect(jsonPath("$.permisos.length()").value(0));
    }

    @Test
    void usuarioOCorreoRepetidosDan409() throws Exception {
        String tAdmin = login(admin().username());
        String nuevo = unico("repetido");
        registrar(tAdmin, alta(nuevo, "MEDICO"));

        mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(alta(nuevo, "MEDICO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Cuenta duplicada"));

        Map<String, Object> mismoCorreo = alta(unico("otro"), "MEDICO");
        mismoCorreo.put("email", nuevo + "@clinica.pe");
        mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(mismoCorreo)))
                .andExpect(status().isConflict());
    }

    @Test
    void datosInvalidosDan400ConElCampoYSinRepetirLaClave() throws Exception {
        String tAdmin = login(admin().username());
        Map<String, Object> mala = alta(unico("mala"), "MEDICO");
        mala.put("clave", "corta");

        String cuerpoRespuesta = mvc.perform(post("/api/admin/usuarios").contentType(JSON)
                        .header("Authorization", bearer(tAdmin)).content(cuerpo(mala)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errores[0].campo").value("clave"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(cuerpoRespuesta.contains("\"corta\""));

        // un ADMIN no se crea por la API (evita escalada de privilegios)
        mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(alta(unico("otro.admin"), "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAdminEditaNombresYCorreoYNoPuedeUsarUnCorreoAjeno() throws Exception {
        String tAdmin = login(admin().username());
        String a = unico("edita.a");
        String b = unico("edita.b");
        int idA = registrar(tAdmin, alta(a, "MEDICO"));
        registrar(tAdmin, alta(b, "MEDICO"));

        mvc.perform(put("/api/admin/usuarios/" + idA).contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(Map.of("nombres", "Carla", "apellidos", "Ríos", "email", a + "@nuevo.pe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Carla"))
                .andExpect(jsonPath("$.email").value(a + "@nuevo.pe"));

        mvc.perform(put("/api/admin/usuarios/" + idA).contentType(JSON).header("Authorization", bearer(tAdmin))
                        .content(cuerpo(Map.of("nombres", "Carla", "apellidos", "Ríos", "email", b + "@clinica.pe"))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/admin/usuarios/" + idA).header("Authorization", bearer(tAdmin)))
                .andExpect(jsonPath("$.email").value(a + "@nuevo.pe"));
    }

    // ---- CA12.2 y CA08.3: desactivar rechaza la siguiente solicitud ----

    @Test
    void desactivarAUnMedicoRechazaSuSiguienteSolicitudAunConElTokenVigente() throws Exception {
        Cuenta m = medico();
        String tMedico = login(m.username());
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tMedico))).andExpect(status().isOk());

        mvc.perform(cambiarEstado(m.id(), false, tAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // CA12.2: el token sigue sin vencer, pero ya no sirve.
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tMedico)))
                .andExpect(status().isUnauthorized());
        // CA08.3: y no puede volver a autenticarse.
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))))
                .andExpect(status().isUnauthorized());

        // Reactivar devuelve el acceso con un login nuevo; el token viejo sigue revocado.
        mvc.perform(cambiarEstado(m.id(), true, tAdmin)).andExpect(status().isOk());
        String nuevoToken = login(m.username());
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(nuevoToken))).andExpect(status().isOk());
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tMedico))).andExpect(status().isUnauthorized());
    }

    @Test
    void unAdministradorNoPuedeDesactivarseASiMismo() throws Exception {
        Cuenta yo = admin();
        String t = login(yo.username());
        mvc.perform(cambiarEstado(yo.id(), false, t))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operación no permitida"));
        mvc.perform(get("/api/admin/usuarios/" + yo.id()).header("Authorization", bearer(t)))
                .andExpect(jsonPath("$.activo").value(true));
    }

    // ---- CA08.2: permisos ----

    @Test
    void revocarYAsignarElPermisoCambiaElAccesoEnLaSiguienteSolicitud() throws Exception {
        Cuenta m = medico();
        String tMedico = login(m.username());
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tMedico))).andExpect(status().isOk());

        mvc.perform(delete("/api/admin/usuarios/" + m.id() + "/permisos/ACCESO_ESTUDIOS")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos.length()").value(0));

        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tMedico)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))))
                .andExpect(status().isUnauthorized()); // CA08.3: sin permisos no se autentica

        mvc.perform(put("/api/admin/usuarios/" + m.id() + "/permisos/ACCESO_ESTUDIOS")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos[0]").value("ACCESO_ESTUDIOS"));
        mvc.perform(get("/api/dicomweb/studies").header("Authorization", bearer(tMedico)))
                .andExpect(status().isOk());
    }

    @Test
    void elCatalogoListaLosPermisosAsignables() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/admin/permisos").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='ACCESO_ESTUDIOS')]").exists());
    }

    @Test
    void permisosSoloParaMedicosYSoloLosQueExisten() throws Exception {
        Cuenta p = paciente("PAC-" + UUID.randomUUID());
        Cuenta m = medico();
        String tAdmin = login(admin().username());

        mvc.perform(put("/api/admin/usuarios/" + p.id() + "/permisos/ACCESO_ESTUDIOS")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/usuarios/" + m.id() + "/permisos/NO_EXISTE")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/usuarios/2147483647/permisos/ACCESO_ESTUDIOS")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(status().isNotFound());
    }

    // ---- CA08.4: un no administrador recibe acceso denegado en TODAS las funciones de gestión ----

    @Test
    void ningunaFuncionDeGestionAceptaAUnNoAdministrador() throws Exception {
        Cuenta objetivo = medico();
        String id = String.valueOf(objetivo.id());
        for (String token : new String[] {login(medico().username()),
                login(paciente("PAC-" + UUID.randomUUID()).username())}) {
            String h = bearer(token);
            mvc.perform(get("/api/admin/usuarios").header("Authorization", h)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/usuarios/" + id).header("Authorization", h)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/usuarios").contentType(JSON).header("Authorization", h)
                    .content(cuerpo(alta(unico("intruso"), "MEDICO")))).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/usuarios/" + id).contentType(JSON).header("Authorization", h)
                    .content(cuerpo(Map.of("nombres", "X", "apellidos", "Y", "email", "x@y.pe"))))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/usuarios/" + id + "/estado").contentType(JSON).header("Authorization", h)
                    .content("{\"activo\":false}")).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/usuarios/" + id + "/permisos/ACCESO_ESTUDIOS").header("Authorization", h))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/admin/usuarios/" + id + "/permisos/ACCESO_ESTUDIOS").header("Authorization", h))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/permisos").header("Authorization", h)).andExpect(status().isForbidden());
        }
        // y nada cambió
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/admin/usuarios/" + id).header("Authorization", bearer(tAdmin)))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.permisos[0]").value("ACCESO_ESTUDIOS"));
    }

    // ---- listado: filtros y paginación ----

    @Test
    void elListadoFiltraPorTextoRolYEstadoYPagina() throws Exception {
        String tAdmin = login(admin().username());
        String marca = UUID.randomUUID().toString().substring(0, 8);
        int primero = 0;
        for (int i = 0; i < 3; i++) {
            Map<String, Object> a = alta("lista" + i + "." + marca, "MEDICO");
            a.put("apellidos", "Marca" + marca);
            int id = registrar(tAdmin, a);
            if (i == 0) primero = id;
        }

        mvc.perform(get("/api/admin/usuarios").param("q", "marca" + marca).param("rol", "MEDICO")
                        .param("tamano", "2").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.pagina").value(0));
        mvc.perform(get("/api/admin/usuarios").param("q", "marca" + marca).param("tamano", "2").param("pagina", "1")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(jsonPath("$.contenido.length()").value(1));

        mvc.perform(cambiarEstado(primero, false, tAdmin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/usuarios").param("q", marca).param("activo", "false")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(primero));
        mvc.perform(get("/api/admin/usuarios").param("q", marca).param("rol", "PACIENTE")
                        .header("Authorization", bearer(tAdmin)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void elTextoDeBusquedaNoSeInterpretaComoComodinSql() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/admin/usuarios").param("q", "%").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void elTamanoDePaginaTieneUnTopeDe100() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/admin/usuarios").param("tamano", "5000").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(100));
    }

    @Test
    void unaCuentaInexistenteDa404() throws Exception {
        String tAdmin = login(admin().username());
        mvc.perform(get("/api/admin/usuarios/2147483647").header("Authorization", bearer(tAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }
}
