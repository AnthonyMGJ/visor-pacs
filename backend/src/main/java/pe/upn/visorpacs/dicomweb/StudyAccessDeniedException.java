package pe.upn.visorpacs.dicomweb;

/** El paciente intentó acceder a un recurso DICOMweb que no es suyo. */
public class StudyAccessDeniedException extends RuntimeException {
    public StudyAccessDeniedException() {
        super("Acceso denegado al estudio solicitado.");
    }
}
