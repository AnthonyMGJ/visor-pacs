package pe.upn.visorpacs.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.upn.visorpacs.config.AppProperties;

/** Comprueba en Orthanc (QIDO-RS) que un estudio pertenece al paciente. Cachea los aciertos 5 min. */
@Component
public class PatientStudyAccess {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TTL_MS = 5 * 60_000L;
    private static final int MAX_ENTRADAS = 10_000;

    private final RestClient orthanc;
    private final String baseUrl;
    private final ConcurrentHashMap<String, Long> permitidos = new ConcurrentHashMap<>();

    public PatientStudyAccess(RestClient orthancRestClient, AppProperties props) {
        this.orthanc = orthancRestClient;
        String url = props.orthanc().baseUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public boolean pacienteEsDueno(String pacienteId, String estudioUid) {
        String clave = pacienteId + "|" + estudioUid;
        long ahora = System.currentTimeMillis();
        Long vence = permitidos.get(clave);
        if (vence != null && vence > ahora) {
            return true;
        }
        URI uri = URI.create(baseUrl + "/dicom-web/studies?StudyInstanceUID=" + enc(estudioUid)
                + "&PatientID=" + enc(pacienteId));
        String cuerpo = orthanc.get().uri(uri)
                .header(HttpHeaders.ACCEPT, "application/dicom+json")
                .retrieve()
                .body(String.class);
        boolean ok = tieneResultados(cuerpo, pacienteId);
        if (ok) {
            if (permitidos.size() >= MAX_ENTRADAS) {
                permitidos.clear();
            }
            permitidos.put(clave, ahora + TTL_MS);
        }
        return ok;
    }

    /** True si algún estudio devuelto tiene exactamente ese PatientID (0010,0020). Falla cerrado. */
    static boolean tieneResultados(String cuerpo, String pacienteId) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return false;
        }
        try {
            JsonNode raiz = MAPPER.readTree(cuerpo);
            if (!raiz.isArray()) {
                return false;
            }
            for (JsonNode estudio : raiz) {
                JsonNode valores = estudio.path("00100020").path("Value");
                if (valores.isArray() && valores.size() > 0 && pacienteId.equals(valores.get(0).asText())) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
