package pe.upn.visorpacs.dicomweb;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reglas para limitar al rol PACIENTE a sus propios estudios en el proxy DICOMweb. */
public final class DicomWebPatientScope {

    private static final Pattern ESTUDIO = Pattern.compile("^/studies/([A-Za-z0-9._,\\-]+)(/.*)?$");

    private DicomWebPatientScope() {}

    /** True si la ruta es la búsqueda de estudios (QIDO-RS: GET /studies). */
    public static boolean esListadoDeEstudios(String ruta) {
        return "/studies".equals(ruta) || "/studies/".equals(ruta);
    }

    /** StudyInstanceUID de rutas /studies/{uid}[/...]; vacío para /series, /instances u otras. */
    public static Optional<String> estudioDeLaRuta(String ruta) {
        Matcher m = ESTUDIO.matcher(ruta == null ? "" : ruta);
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Quita cualquier filtro de PatientID enviado por el cliente y fija el del token. */
    public static String consultaConPacienteForzado(String consulta, String pacienteId) {
        StringBuilder sb = new StringBuilder();
        if (consulta != null && !consulta.isBlank()) {
            for (String par : consulta.split("&")) {
                if (par.isEmpty()) {
                    continue;
                }
                int igual = par.indexOf('=');
                String nombre = igual < 0 ? par : par.substring(0, igual);
                String normalizado;
                try {
                    normalizado = URLDecoder.decode(nombre, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                } catch (IllegalArgumentException e) {
                    throw new InvalidDicomWebPathException();
                }
                if (normalizado.equals("patientid") || normalizado.equals("00100020")) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(par);
            }
        }
        if (sb.length() > 0) {
            sb.append('&');
        }
        return sb.append("PatientID=").append(URLEncoder.encode(pacienteId, StandardCharsets.UTF_8)).toString();
    }
}
