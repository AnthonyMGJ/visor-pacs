package pe.upn.visorpacs.dicomweb;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Roles;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.sesion.SesionTokenValidator;
import pe.upn.visorpacs.sesion.SinPermisoException;
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
 * aquí se valida el JWT (SecurityConfig), se comprueba el permiso (médico) o la propiedad del estudio
 * (paciente), se audita la apertura (HU10) y se reenvía la petición por la red interna.
 */
@RestController
@RequestMapping("/api/dicomweb")
@Tag(name = "DICOMweb", description = "Proxy de lectura hacia Orthanc (QIDO-RS / WADO-RS). Roles MEDICO y PACIENTE")
public class DicomWebProxyController {
    private static final String PREFIJO = "/api/dicomweb";
    private static final String RAIZ_ORTHANC = "/dicom-web";

    private final RestClient orthanc;
    private final String baseUrl;
    private final PatientStudyAccess acceso;
    private final EstadoCuentaCache cuentas;
    private final AuditoriaService auditoria;

    public DicomWebProxyController(RestClient orthancRestClient, AppProperties props,
                                   PatientStudyAccess acceso, EstadoCuentaCache cuentas,
                                   AuditoriaService auditoria) {
        this.acceso = acceso;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
        this.orthanc = orthancRestClient;
        String url = props.orthanc().baseUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @GetMapping("/**")
    @Operation(summary = "Reenvía una consulta DICOMweb de solo lectura",
            description = "Ruta tras /api/dicomweb: /studies, /studies/{uid}/series, .../metadata, .../frames/1, etc. "
                    + "403 si el médico no tiene ACCESO_ESTUDIOS o el paciente pide un estudio ajeno; 400 si la ruta no es de lectura DICOMweb.")
    public void reenviar(HttpServletRequest request, HttpServletResponse response,
                         @AuthenticationPrincipal Jwt jwt) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length() + PREFIJO.length());
        if (!DicomWebPathValidator.esPermitida(ruta)) {
            throw new InvalidDicomWebPathException();
        }
        String consulta = autorizar(jwt, ruta, request.getQueryString());
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

    /**
     * MEDICO con permiso ACCESO_ESTUDIOS ve todo. PACIENTE queda limitado a los estudios de su PatientID.
     * Cualquier otro caso se deniega. Los accesos a un estudio (y los intentos denegados) se auditan.
     */
    private String autorizar(Jwt jwt, String ruta, String consulta) {
        Optional<String> estudio = DicomWebPatientScope.estudioDeLaRuta(ruta);
        List<String> roles = jwt.getClaimAsStringList("roles");
        try {
            String resultado;
            if (roles != null && roles.contains(Roles.MEDICO)) {
                exigirPermisoDeEstudios(jwt);
                resultado = consulta;
            } else {
                resultado = ajustarParaPaciente(jwt, ruta, consulta, estudio);
            }
            estudio.ifPresent(uid -> auditar(TipoEvento.APERTURA_ESTUDIO, ResultadoEvento.EXITO, jwt, uid, null));
            return resultado;
        } catch (StudyAccessDeniedException | SinPermisoException e) {
            auditar(TipoEvento.ACCESO_ESTUDIO_DENEGADO, ResultadoEvento.FALLO, jwt, estudio.orElse(null),
                    e instanceof SinPermisoException ? "sin permiso " + Permisos.ACCESO_ESTUDIOS : "estudio ajeno o ruta no permitida");
            throw e;
        }
    }

    /** Se lee del estado vigente de la cuenta (no del token), así revocar el permiso surte efecto en la siguiente solicitud. */
    private void exigirPermisoDeEstudios(Jwt jwt) {
        int uid = ((Number) jwt.getClaims().get(SesionTokenValidator.CLAIM_USUARIO)).intValue();
        boolean permitido = cuentas.obtener(uid).filter(c -> c.tienePermiso(Permisos.ACCESO_ESTUDIOS)).isPresent();
        if (!permitido) {
            throw new SinPermisoException(Permisos.ACCESO_ESTUDIOS);
        }
    }

    private String ajustarParaPaciente(Jwt jwt, String ruta, String consulta, Optional<String> estudio) {
        String pacienteId = jwt.getClaimAsString("patientId");
        if (pacienteId == null || pacienteId.isBlank()) {
            throw new StudyAccessDeniedException();
        }
        if (DicomWebPatientScope.esListadoDeEstudios(ruta)) {
            return DicomWebPatientScope.consultaConPacienteForzado(consulta, pacienteId);
        }
        String uid = estudio.orElseThrow(StudyAccessDeniedException::new); // /series e /instances sueltos: no permitidos
        if (!acceso.pacienteEsDueno(pacienteId, uid)) {
            throw new StudyAccessDeniedException();
        }
        return consulta;
    }

    private void auditar(TipoEvento tipo, ResultadoEvento resultado, Jwt jwt, String estudio, String detalle) {
        Object uid = jwt.getClaims().get(SesionTokenValidator.CLAIM_USUARIO);
        List<String> roles = jwt.getClaimAsStringList("roles");
        auditoria.registrarAccesoEstudio(tipo, resultado, uid instanceof Number n ? n.intValue() : null,
                jwt.getSubject(), roles == null || roles.isEmpty() ? null : roles.get(0), estudio, detalle);
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
