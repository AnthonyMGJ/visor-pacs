package pe.upn.visorpacs.dicomweb;

import java.util.regex.Pattern;

/** Solo deja pasar rutas DICOMweb de lectura (/studies, /series, /instances y sus subrutas). */
public final class DicomWebPathValidator {
    private static final Pattern PERMITIDA =
            Pattern.compile("^/(studies|series|instances)(/[A-Za-z0-9._,\\-]+)*/?$");

    private DicomWebPathValidator() {}

    public static boolean esPermitida(String ruta) {
        if (ruta == null || ruta.length() > 512 || !PERMITIDA.matcher(ruta).matches()) {
            return false;
        }
        for (String segmento : ruta.split("/")) {
            if (segmento.equals(".") || segmento.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
