package pe.upn.visorpacs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class OrthancHealthIndicatorTest {

    private HttpServer servidor;

    @AfterEach
    void cerrar() {
        if (servidor != null) servidor.stop(0);
    }

    private OrthancHealthIndicator indicador(String url) {
        return new OrthancHealthIndicator(new AppProperties(
                new AppProperties.Jwt("x", "visor-pacs", 15), new AppProperties.Security(5, 15),
                new AppProperties.Orthanc(url, "backend", "clave"), new AppProperties.Cors(List.of()),
                new AppProperties.Bootstrap(false, "", "", "", "", "", "", "", "", "")));
    }

    private String iniciar(int codigo, AtomicReference<String> autorizacion) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/system", ex -> {
            autorizacion.set(ex.getRequestHeaders().getFirst("Authorization"));
            ex.sendResponseHeaders(codigo, -1);
            ex.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Test
    void estaUpSiOrthancResponde200ConLasCredencialesDelBackend() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        assertEquals(Status.UP, indicador(iniciar(200, auth)).health().getStatus());
        assertTrue(auth.get().startsWith("Basic "));
    }

    @Test
    void estaDownSiOrthancRechazaLasCredenciales() throws Exception {
        assertEquals(Status.DOWN, indicador(iniciar(401, new AtomicReference<>())).health().getStatus());
    }

    @Test
    void estaDownSiOrthancNoEstaAlcanzable() {
        assertEquals(Status.DOWN, indicador("http://127.0.0.1:1").health().getStatus());
    }
}
