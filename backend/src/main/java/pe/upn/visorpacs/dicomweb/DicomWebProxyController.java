package pe.upn.visorpacs.dicomweb;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.upn.visorpacs.domain.Roles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import pe.upn.visorpacs.config.AppProperties;

/**
 * Proxy DICOMweb (QIDO-RS / WADO-RS) hacia Orthanc. El navegador solo habla con este backend:
 * aquí se valida el JWT (SecurityConfig) y se reenvía la petición por la red interna.
 * TODO Sprint 3 (HU10): registrar cada apertura de estudio en el historial de auditoría.
 */
@RestController
@RequestMapping("/api/dicomweb")
public class DicomWebProxyController {
    private static final String PREFIJO = "/api/dicomweb";
    private static final String RAIZ_ORTHANC = "/dicom-web";

    private final RestClient orthanc;
    private final String baseUrl;
    private final PatientStudyAccess acceso;

    public DicomWebProxyController(RestClient orthancRestClient, AppProperties props,
                                   PatientStudyAccess acceso) {
        this.acceso = acceso;
        this.orthanc = orthancRestClient;
        String url = props.orthanc().baseUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @GetMapping("/**")
    public void reenviar(HttpServletRequest request, HttpServletResponse response,
                         @AuthenticationPrincipal Jwt jwt) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length() + PREFIJO.length());
        if (!DicomWebPathValidator.esPermitida(ruta)) {
            throw new InvalidDicomWebPathException();
        }
        String consulta = ajustarConsulta(jwt, ruta, request.getQueryString());
        URI destino;
        try {
            destino = URI.create(baseUrl + RAIZ_ORTHANC + ruta + (consulta == null ? "" : "?" + consulta));
        } catch (IllegalArgumentException e) {
            throw new InvalidDicomWebPathException();
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        orthanc.get().uri(destino)
                .header(HttpHeaders.ACCEPT, accept == null ? "*/*" : accept)
                .exchange((req, res) -> {
                    copiar(res, response);
                    return null;
                });
    }

    /** MEDICO ve todo. Cualquier otro rol (PACIENTE) queda limitado a los estudios de su PatientID. */
    private String ajustarConsulta(Jwt jwt, String ruta, String consulta) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && roles.contains(Roles.MEDICO)) {
            return consulta;
        }
        String pacienteId = jwt.getClaimAsString("patientId");
        if (pacienteId == null || pacienteId.isBlank()) {
            throw new StudyAccessDeniedException();
        }
        if (DicomWebPatientScope.esListadoDeEstudios(ruta)) {
            return DicomWebPatientScope.consultaConPacienteForzado(consulta, pacienteId);
        }
        String estudio = DicomWebPatientScope.estudioDeLaRuta(ruta)
                .orElseThrow(StudyAccessDeniedException::new); // /series e /instances sueltos: no permitidos
        if (!acceso.pacienteEsDueno(pacienteId, estudio)) {
            throw new StudyAccessDeniedException();
        }
        return consulta;
    }

    private void copiar(ClientHttpResponse res, HttpServletResponse out) throws IOException {
        int estado = res.getStatusCode().value();
        if (estado == 401 || estado == 403) {
            estado = 502; // credenciales del backend hacia Orthanc mal configuradas: no confundir con el token del médico
        }
        out.setStatus(estado);
        MediaType tipo = res.getHeaders().getContentType();
        if (tipo != null) {
            out.setContentType(tipo.toString());
        }
        long longitud = res.getHeaders().getContentLength();
        if (longitud >= 0) {
            out.setContentLengthLong(longitud);
        }
        out.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); // imágenes clínicas: sin caché en disco
        try (InputStream in = res.getBody()) {
            in.transferTo(out.getOutputStream());
        }
    }
}
