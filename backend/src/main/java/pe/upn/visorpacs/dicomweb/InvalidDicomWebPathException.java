package pe.upn.visorpacs.dicomweb;

public class InvalidDicomWebPathException extends RuntimeException {
    public InvalidDicomWebPathException() {
        super("Ruta DICOMweb no permitida");
    }
}
