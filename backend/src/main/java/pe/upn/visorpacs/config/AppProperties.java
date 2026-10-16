package pe.upn.visorpacs.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia de la aplicación (prefijo "app" en application.yml). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Security security, Orthanc orthanc, Cors cors, Bootstrap bootstrap) {
    public record Jwt(String secret, String issuer, long ttlMinutes) {}
    public record Security(int maxFailedAttempts, long lockMinutes) {}
    public record Orthanc(String baseUrl, String username, String password) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Bootstrap(boolean enabled, String username, String password,
                            String nombres, String apellidos, String email,
                            String pacienteUsername, String pacientePassword,
                            String pacienteDicomId, String pacienteNombres) {}
}
