package pe.upn.visorpacs.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import pe.upn.visorpacs.auth.AccountLockedException;
import pe.upn.visorpacs.auth.ClaveActualIncorrectaException;
import pe.upn.visorpacs.auth.InvalidCredentialsException;
import pe.upn.visorpacs.auth.SesionNoVigenteException;
import pe.upn.visorpacs.dicomweb.InvalidDicomWebPathException;
import pe.upn.visorpacs.dicomweb.StudyAccessDeniedException;
import pe.upn.visorpacs.sesion.SinPermisoException;
import pe.upn.visorpacs.usuario.UsuarioDuplicadoException;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;

/** Cada excepción de la aplicación debe salir como application/problem+json con el estado correcto. */
class GlobalExceptionHandlerTest {

    record Cuerpo(@NotBlank String nombre, @Size(min = 12) String clave) {}

    @RestController
    @RequestMapping("/t")
    static class ControladorDePrueba {
        @GetMapping("/credenciales") void credenciales() { throw new InvalidCredentialsException(); }
        @GetMapping("/bloqueada") void bloqueada() { throw new AccountLockedException(); }
        @GetMapping("/claveactual") void claveActual() { throw new ClaveActualIncorrectaException(); }
        @GetMapping("/sesion") void sesion() { throw new SesionNoVigenteException(); }
        @GetMapping("/estudio") void estudio() { throw new StudyAccessDeniedException(); }
        @GetMapping("/permiso") void permiso() { throw new SinPermisoException("ACCESO_ESTUDIOS"); }
        @GetMapping("/ruta") void ruta() { throw new InvalidDicomWebPathException(); }
        @GetMapping("/duplicado") void duplicado() { throw new UsuarioDuplicadoException("Ya existe un usuario con ese correo."); }
        @GetMapping("/invalido") void invalido() { throw new UsuarioInvalidoException("La clave debe tener al menos 12 caracteres."); }
        @GetMapping("/noexiste") void noExiste() { throw new RecursoNoEncontradoException("El usuario no existe."); }
        @GetMapping("/conflicto") void conflicto() { throw new OperacionNoPermitidaException("No puedes desactivar tu propia cuenta."); }
        @GetMapping("/integridad") void integridad() { throw new DataIntegrityViolationException("Duplicate entry 'x' for key 'uk'"); }
        @GetMapping("/pacs") void pacs() { throw new ResourceAccessException("connect timed out"); }
        @GetMapping("/num/{n}") void numero(@PathVariable int n) {}
        @PostMapping("/validar") void validar(@Valid @RequestBody Cuerpo c) {}
    }

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void esperar(String ruta, int estado, String titulo) throws Exception {
        mvc.perform(get(ruta))
                .andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value(titulo))
                .andExpect(jsonPath("$.status").value(estado))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void credencialesInvalidasDan401() throws Exception { esperar("/t/credenciales", 401, "Credenciales inválidas"); }
    @Test void cuentaBloqueadaDa423() throws Exception { esperar("/t/bloqueada", 423, "Cuenta bloqueada"); }
    @Test void claveActualIncorrectaDa400NoUnAuthorized() throws Exception { esperar("/t/claveactual", 400, "Clave actual incorrecta"); }
    @Test void sesionNoVigenteDa401() throws Exception { esperar("/t/sesion", 401, "Sesión no vigente"); }
    @Test void estudioAjenoDa403() throws Exception { esperar("/t/estudio", 403, "Acceso denegado"); }
    @Test void sinPermisoDa403() throws Exception { esperar("/t/permiso", 403, "Acceso denegado"); }
    @Test void rutaDicomwebInvalidaDa400() throws Exception { esperar("/t/ruta", 400, "Ruta no permitida"); }
    @Test void duplicadoDa409() throws Exception { esperar("/t/duplicado", 409, "Cuenta duplicada"); }
    @Test void usuarioInvalidoDa400ConSuMensaje() throws Exception {
        esperar("/t/invalido", 400, "Datos inválidos");
        mvc.perform(get("/t/invalido")).andExpect(jsonPath("$.detail").value("La clave debe tener al menos 12 caracteres."));
    }
    @Test void noEncontradoDa404() throws Exception { esperar("/t/noexiste", 404, "No encontrado"); }
    @Test void operacionNoPermitidaDa409() throws Exception { esperar("/t/conflicto", 409, "Operación no permitida"); }
    @Test void pacsCaidoDa502() throws Exception { esperar("/t/pacs", 502, "PACS no disponible"); }

    @Test
    void violacionDeIntegridadDa409SinFiltrarElMensajeDeLaBase() throws Exception {
        String cuerpo = mvc.perform(get("/t/integridad"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertFalse(cuerpo.contains("Duplicate entry"));
    }

    @Test
    void validacionDa400ConCampoYReglaPeroSinElValorRechazado() throws Exception {
        String cuerpo = mvc.perform(post("/t/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"clave\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertFalse(cuerpo.contains("corta"), "no debe repetir el valor enviado");
    }

    @Test
    void jsonMalFormadoDa400() throws Exception {
        mvc.perform(post("/t/validar").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"));
    }

    @Test
    void parametroDeTipoIncorrectoDa400() throws Exception {
        mvc.perform(get("/t/num/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"));
    }

    @Test
    void metodoNoPermitidoDa405() throws Exception {
        mvc.perform(post("/t/credenciales"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }

    @Test
    void tipoDeContenidoNoSoportadoDa415() throws Exception {
        mvc.perform(post("/t/validar").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"));
    }
}
