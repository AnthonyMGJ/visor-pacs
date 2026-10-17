package pe.upn.visorpacs.integracion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Orthanc de mentira para las pruebas de integración: responde lo mínimo de DICOMweb (QIDO-RS y un frame)
 * y anota cada petición que recibe, para comprobar qué reenvía realmente el proxy del backend.
 */
final class OrthancSimulado implements AutoCloseable {

    private final HttpServer servidor;
    private final Map<String, String> estudios = new ConcurrentHashMap<>(); // StudyInstanceUID -> PatientID
    private final List<String> peticiones = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();

    private OrthancSimulado(HttpServer servidor) {
        this.servidor = servidor;
    }

    static OrthancSimulado iniciar() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            OrthancSimulado o = new OrthancSimulado(s);
            s.createContext("/system", o::sistema);
            s.createContext("/dicom-web", o::dicomWeb);
            s.setExecutor(Executors.newFixedThreadPool(4));
            s.start();
            return o;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar el Orthanc simulado", e);
        }
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    void registrarEstudio(String studyUid, String pacienteId) {
        estudios.put(studyUid, pacienteId);
    }

    /** Peticiones recibidas (ruta + consulta), de la más antigua a la más reciente. */
    List<String> peticiones() {
        return List.copyOf(peticiones);
    }

    List<String> autorizaciones() {
        return List.copyOf(autorizaciones);
    }

    private void sistema(HttpExchange ex) throws IOException {
        responder(ex, 200, "application/json", "{\"Name\":\"orthanc-simulado\"}".getBytes(StandardCharsets.UTF_8));
    }

    private void dicomWeb(HttpExchange ex) throws IOException {
        peticiones.add(ex.getRequestURI().toString());
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        autorizaciones.add(auth == null ? "" : auth);
        String ruta = ex.getRequestURI().getRawPath().substring("/dicom-web".length());
        Map<String, String> consulta = parsear(ex.getRequestURI().getRawQuery());

        if (ruta.equals("/studies")) {
            StringBuilder sb = new StringBuilder("[");
            for (Map.Entry<String, String> e : estudios.entrySet()) {
                boolean coincideEstudio = !consulta.containsKey("StudyInstanceUID")
                        || consulta.get("StudyInstanceUID").equals(e.getKey());
                boolean coincidePaciente = !consulta.containsKey("PatientID")
                        || consulta.get("PatientID").equals(e.getValue());
                if (coincideEstudio && coincidePaciente) {
                    if (sb.length() > 1) sb.append(',');
                    sb.append("{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"").append(e.getKey()).append("\"]},")
                      .append("\"00100020\":{\"vr\":\"LO\",\"Value\":[\"").append(e.getValue()).append("\"]}}");
                }
            }
            responder(ex, 200, "application/dicom+json", sb.append(']').toString().getBytes(StandardCharsets.UTF_8));
        } else if (ruta.matches("^/studies/[^/]+/series/?$")) {
            String uid = ruta.split("/")[2];
            String cuerpo = estudios.containsKey(uid)
                    ? "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4.5\"]}}]" : "[]";
            responder(ex, 200, "application/dicom+json", cuerpo.getBytes(StandardCharsets.UTF_8));
        } else if (ruta.endsWith("/frames/1")) {
            responder(ex, 200, "application/octet-stream", new byte[] {1, 2, 3, 4});
        } else {
            responder(ex, 404, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> parsear(String consulta) {
        Map<String, String> mapa = new HashMap<>();
        if (consulta == null || consulta.isBlank()) return mapa;
        for (String par : consulta.split("&")) {
            int i = par.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? par : par.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
            mapa.put(k, v);
        }
        return mapa;
    }

    private static void responder(HttpExchange ex, int codigo, String tipo, byte[] cuerpo) throws IOException {
        ex.getResponseHeaders().add("Content-Type", tipo);
        ex.sendResponseHeaders(codigo, cuerpo.length);
        ex.getResponseBody().write(cuerpo);
        ex.close();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
