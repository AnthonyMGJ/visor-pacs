package pe.upn.visorpacs.web;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.upn.visorpacs.auth.AccountLockedException;
import pe.upn.visorpacs.recuperacion.TokenRecuperacionInvalidoException;
import pe.upn.visorpacs.auth.ClaveActualIncorrectaException;
import pe.upn.visorpacs.auth.InvalidCredentialsException;
import pe.upn.visorpacs.auth.SesionNoVigenteException;
import pe.upn.visorpacs.dicomweb.InvalidDicomWebPathException;
import pe.upn.visorpacs.dicomweb.StudyAccessDeniedException;
import pe.upn.visorpacs.sesion.SinPermisoException;
import pe.upn.visorpacs.usuario.UsuarioDuplicadoException;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;

/**
 * Todos los errores de la API salen como application/problem+json con title, status y detail en español.
 * Extiende ResponseEntityExceptionHandler para cubrir también los errores propios de Spring MVC
 * (400 de lectura, 404 de ruta, 405 de método, 415 de tipo de contenido). Los 401/403 de seguridad
 * los escribe ErrorSeguridadHandler con el mismo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail credencialesInvalidas() {
        return problema(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", "Usuario o contraseña incorrectos.");
    }

    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail cuentaBloqueada() {
        return problema(HttpStatus.LOCKED, "Cuenta bloqueada",
                "La cuenta está bloqueada temporalmente. Intente nuevamente más tarde.");
    }

    @ExceptionHandler(ClaveActualIncorrectaException.class)
    public ProblemDetail claveActualIncorrecta(ClaveActualIncorrectaException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Clave actual incorrecta", ex.getMessage());
    }

    @ExceptionHandler(TokenRecuperacionInvalidoException.class)
    public ProblemDetail enlaceRecuperacionInvalido(TokenRecuperacionInvalidoException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Enlace no válido", ex.getMessage());
    }

    @ExceptionHandler(SesionNoVigenteException.class)
    public ProblemDetail sesionNoVigente() {
        return problema(HttpStatus.UNAUTHORIZED, "Sesión no vigente", "Inicia sesión nuevamente.");
    }

    @ExceptionHandler(InvalidDicomWebPathException.class)
    public ProblemDetail rutaInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "Ruta no permitida", "La ruta DICOMweb solicitada no es válida.");
    }

    @ExceptionHandler(StudyAccessDeniedException.class)
    public ProblemDetail accesoDenegado() {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para ver este estudio.");
    }

    @ExceptionHandler(SinPermisoException.class)
    public ProblemDetail sinPermiso() {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "Tu cuenta no tiene permiso para ver estudios.");
    }

    @ExceptionHandler(UsuarioDuplicadoException.class)
    public ProblemDetail usuarioDuplicado(UsuarioDuplicadoException ex) {
        return problema(HttpStatus.CONFLICT, "Cuenta duplicada", ex.getMessage());
    }

    // Los mensajes de UsuarioInvalidoException se redactan para mostrarse (nunca incluyen la clave).
    @ExceptionHandler(UsuarioInvalidoException.class)
    public ProblemDetail usuarioInvalido(UsuarioInvalidoException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ProblemDetail operacionNoPermitida(OperacionNoPermitidaException ex) {
        return problema(HttpStatus.CONFLICT, "Operación no permitida", ex.getMessage());
    }

    // Carrera entre dos altas con el mismo usuario/correo: la restricción única de la base responde por nosotros.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflictoDeDatos(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto de datos", "Los datos entran en conflicto con un registro existente.");
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail pacsNoDisponible(RestClientException ex) {
        log.error("Fallo al consultar Orthanc: {}", ex.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, "PACS no disponible",
                "No se pudo obtener la información del servidor de imágenes.");
    }

    // ---- Errores propios de Spring MVC, en español y con el mismo formato ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Se informan campo y regla, nunca el valor rechazado (podría ser una clave).
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("campo", e.getField(),
                        "mensaje", e.getDefaultMessage() == null ? "no válido" : e.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Los datos enviados no son válidos.");
        pd.setProperty("errores", errores);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respuesta(ex, headers, HttpStatus.BAD_REQUEST, request, "Datos inválidos",
                "El cuerpo de la petición falta o no es un JSON válido.");
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respuesta(ex, headers, HttpStatus.BAD_REQUEST, request, "Datos inválidos",
                "Falta el parámetro «" + ex.getParameterName() + "».");
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respuesta(ex, headers, HttpStatus.BAD_REQUEST, request, "Datos inválidos",
                "El valor del parámetro «" + ex.getPropertyName() + "» no es válido.");
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respuesta(ex, headers, HttpStatus.NOT_FOUND, request, "No encontrado", "El recurso solicitado no existe.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respuesta(ex, headers, HttpStatus.METHOD_NOT_ALLOWED, request, "Método no permitido",
                "El método HTTP no está permitido en esta ruta.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respuesta(ex, headers, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request, "Tipo de contenido no soportado",
                "Envía el cuerpo como application/json.");
    }

    private ResponseEntity<Object> respuesta(Exception ex, HttpHeaders headers, HttpStatus status,
                                             WebRequest request, String titulo, String detalle) {
        return handleExceptionInternal(ex, problema(status, titulo, detalle), headers, status, request);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(titulo);
        return pd;
    }
}
