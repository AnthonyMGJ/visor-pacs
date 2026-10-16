package pe.upn.visorpacs.web;

import pe.upn.visorpacs.dicomweb.StudyAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import pe.upn.visorpacs.auth.AccountLockedException;
import pe.upn.visorpacs.auth.InvalidCredentialsException;
import pe.upn.visorpacs.dicomweb.InvalidDicomWebPathException;

@RestControllerAdvice
public class GlobalExceptionHandler {
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail datosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Los datos enviados no son válidos.");
    }

    @ExceptionHandler(InvalidDicomWebPathException.class)
    public ProblemDetail rutaInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "Ruta no permitida", "La ruta DICOMweb solicitada no es válida.");
    }

    @ExceptionHandler(StudyAccessDeniedException.class)
    public ProblemDetail accesoDenegado() {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para ver este estudio.");
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail pacsNoDisponible(RestClientException ex) {
        log.error("Fallo al consultar Orthanc: {}", ex.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, "PACS no disponible",
                "No se pudo obtener la información del servidor de imágenes.");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(titulo);
        return pd;
    }
}
