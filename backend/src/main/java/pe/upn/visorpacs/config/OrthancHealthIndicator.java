package pe.upn.visorpacs.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Parte de /actuator/health (HT02, T40): el sistema solo está "UP" si Orthanc responde. Sin Orthanc el visor
 * no puede mostrar imágenes, así que el monitoreo debe verlo como caída. Tiempo máximo: 3 s.
 */
@Component("orthanc")
public class OrthancHealthIndicator implements HealthIndicator {

    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI destino;
    private final String autorizacion;

    public OrthancHealthIndicator(AppProperties props) {
        String base = props.orthanc().baseUrl();
        this.destino = URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/system");
        String credencial = props.orthanc().username() + ":" + props.orthanc().password();
        this.autorizacion = "Basic " + Base64.getEncoder().encodeToString(credencial.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Health health() {
        HttpRequest peticion = HttpRequest.newBuilder(destino).timeout(Duration.ofSeconds(3))
                .header("Authorization", autorizacion).GET().build();
        try {
            int codigo = cliente.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode();
            return codigo == 200 ? Health.up().build() : Health.down().withDetail("http", codigo).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().build();
        } catch (IOException e) {
            return Health.down().build();
        }
    }
}
