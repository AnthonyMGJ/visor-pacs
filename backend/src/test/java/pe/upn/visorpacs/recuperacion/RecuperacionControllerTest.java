package pe.upn.visorpacs.recuperacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;
import pe.upn.visorpacs.web.GlobalExceptionHandler;
import pe.upn.visorpacs.web.RecursoNoEncontradoException;

class RecuperacionControllerTest {

    private final RecuperacionClaveService servicio = mock(RecuperacionClaveService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new RecuperacionController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static String json(String c) {
        return c;
    }

    @Test
    void olvideResponde202ConElMensajeGenerico() throws Exception {
        mvc.perform(post("/api/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"  ana@clinica.pe \"}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(RecuperacionController.MENSAJE_GENERICO));
        verify(servicio).solicitar(eq("ana@clinica.pe"), any());
    }

    @Test
    void laRespuestaEsIdenticaParaUnCorreoQueExisteYUnoQueNo() throws Exception {
        // El servicio no devuelve nada ni lanza error en ninguno de los dos casos, así que el cuerpo es el mismo.
        String a = mvc.perform(post("/api/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"existe@clinica.pe\"}"))).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"no-existe@clinica.pe\"}"))).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(a, b);
    }

    @Test
    void unCorreoMalFormadoRecibeLaMismaRespuestaGenerica() throws Exception {
        mvc.perform(post("/api/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"esto-no-es-un-correo\"}")))
                .andExpect(status().isAccepted());
    }

    @Test
    void sinCorreoEs400() throws Exception {
        mvc.perform(post("/api/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON).content(json("{\"email\":\"\"}")))
                .andExpect(status().isBadRequest());
        verify(servicio, never()).solicitar(any(), any());
    }

    @Test
    void funcionApagadaEs404ConFormatoProblem() throws Exception {
        doThrow(new RecursoNoEncontradoException("Recurso no encontrado.")).when(servicio).solicitar(any(), any());
        mvc.perform(post("/api/auth/olvide-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"a@b.pe\"}")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void restablecerOkResponde200() throws Exception {
        mvc.perform(post("/api/auth/restablecer-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"token\":\" abc \",\"claveNueva\":\"ClaveNueva-67890\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());
        verify(servicio).restablecer(eq("abc"), eq("ClaveNueva-67890"), any());
    }

    @Test
    void enlaceInvalidoEs400ConMensajeEnEspanol() throws Exception {
        doThrow(new TokenRecuperacionInvalidoException()).when(servicio).restablecer(any(), any(), any());
        mvc.perform(post("/api/auth/restablecer-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"token\":\"x\",\"claveNueva\":\"ClaveNueva-67890\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Enlace no válido"))
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ya venció. Solicita uno nuevo."));
    }

    @Test
    void claveQueNoCumpleLaPoliticaEs400() throws Exception {
        doThrow(new UsuarioInvalidoException("La clave debe tener al menos 12 caracteres."))
                .when(servicio).restablecer(any(), any(), any());
        mvc.perform(post("/api/auth/restablecer-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"token\":\"x\",\"claveNueva\":\"corta\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La clave debe tener al menos 12 caracteres."));
    }

    @Test
    void faltaElTokenEs400() throws Exception {
        mvc.perform(post("/api/auth/restablecer-clave").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"claveNueva\":\"ClaveNueva-67890\"}")))
                .andExpect(status().isBadRequest());
    }
}
