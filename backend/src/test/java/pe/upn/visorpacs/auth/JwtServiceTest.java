package pe.upn.visorpacs.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Paciente;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;

class JwtServiceTest {

    @Test
    void tokenEmitidoSeDecodificaYTraeElRolMedico() {
        SecretKey key = new SecretKeySpec("s".repeat(40).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x", "visor-pacs", 15),
                new AppProperties.Security(5, 15),
                new AppProperties.Orthanc("http://orthanc:8042", "u", "p"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Bootstrap(false, "", "", "", "", "", "", "", "", ""));
        Usuario medico = Usuario.nuevo("medico.demo", "hash", "Ana", "Pérez", "ana@example.com", new Rol("MEDICO"));

        IssuedToken token = new JwtService(encoder, props, Clock.systemUTC()).issue(medico);
        Jwt jwt = decoder.decode(token.value());

        assertEquals("medico.demo", jwt.getSubject());
        assertEquals("visor-pacs", jwt.getClaimAsString("iss"));
        assertEquals(List.of("MEDICO"), jwt.getClaimAsStringList("roles"));
        assertEquals(900, token.expiresInSeconds());
    }

    @Test
    void tokenDePacienteTraeRolYPatientId() {
        SecretKey key = new SecretKeySpec("s".repeat(40).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x", "visor-pacs", 15),
                new AppProperties.Security(5, 15),
                new AppProperties.Orthanc("http://orthanc:8042", "u", "p"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Bootstrap(false, "", "", "", "", "", "", "", "", ""));
        Usuario paciente = Usuario.nuevoPaciente("paciente.demo", "hash", "Paciente", "Uno", "p@example.com",
                new Rol("PACIENTE"), Paciente.nuevo("PRUEBA-0001", "Paciente Uno"));
        IssuedToken token = new JwtService(encoder, props, Clock.systemUTC()).issue(paciente);
        Jwt jwt = decoder.decode(token.value());
        assertEquals(List.of("PACIENTE"), jwt.getClaimAsStringList("roles"));
        assertEquals("PRUEBA-0001", jwt.getClaimAsString("patientId"));
        assertEquals("PACIENTE", token.rol());
        assertEquals("Paciente Uno", token.nombre());
    }

    @Test
    void tokenTraeElIdYLaVersionDeSesionParaPoderRevocarlo() {
        SecretKey key = new SecretKeySpec("s".repeat(40).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x", "visor-pacs", 15),
                new AppProperties.Security(5, 15),
                new AppProperties.Orthanc("http://orthanc:8042", "u", "p"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Bootstrap(false, "", "", "", "", "", "", "", "", ""));
        Usuario admin = Usuario.nuevo("admin.demo", "hash", "Luz", "Rojas", "luz@example.com", new Rol("ADMIN"));
        ReflectionTestUtils.setField(admin, "id", 5);
        admin.invalidarSesiones();
        admin.invalidarSesiones();

        Jwt jwt = decoder.decode(new JwtService(encoder, props, Clock.systemUTC()).issue(admin).value());

        assertEquals(List.of("ADMIN"), jwt.getClaimAsStringList("roles"));
        assertEquals(5, ((Number) jwt.getClaims().get("uid")).intValue());
        assertEquals(2, ((Number) jwt.getClaims().get("ver")).intValue());
    }

    private static JwtService servicio(Clock reloj) {
        SecretKey key = new SecretKeySpec("s".repeat(40).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x", "visor-pacs", 15),
                new AppProperties.Security(5, 15),
                new AppProperties.Orthanc("http://orthanc:8042", "u", "p"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Bootstrap(false, "", "", "", "", "", "", "", "", ""));
        return new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), props, reloj);
    }

    private static Jwt decodificar(String token) {
        SecretKey key = new SecretKeySpec("s".repeat(40).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    }

    @Test
    void elTokenGuardaElInicioDeSesionYAlRenovarSeConserva() {
        java.time.Instant inicio = java.time.Instant.now().minusSeconds(3600);
        Usuario medico = Usuario.nuevo("medico.demo", "hash", "Ana", "Pérez", "ana@example.com", new Rol("MEDICO"));
        JwtService jwtService = servicio(Clock.systemUTC());

        Jwt nuevo = decodificar(jwtService.issue(medico).value());
        Jwt renovado = decodificar(jwtService.issue(medico, inicio).value());

        long ahora = java.time.Instant.now().getEpochSecond();
        assertTrue(Math.abs(((Number) nuevo.getClaims().get("ses")).longValue() - ahora) < 5);
        assertEquals(inicio.getEpochSecond(), ((Number) renovado.getClaims().get("ses")).longValue());
    }

    @Test
    void cadaTokenTraeUnIdentificadorPropioYSoloLaClaveTemporalLlevaLaMarca() {
        Usuario medico = Usuario.nuevo("medico.demo", "hash", "Ana", "Pérez", "ana@example.com", new Rol("MEDICO"));
        JwtService jwtService = servicio(Clock.systemUTC());

        Jwt a = decodificar(jwtService.issue(medico).value());
        Jwt b = decodificar(jwtService.issue(medico).value());
        assertNotNull(a.getId());
        assertNotEquals(a.getId(), b.getId());
        assertNull(a.getClaims().get("cc"));
        assertFalse(jwtService.issue(medico).debeCambiarClave());

        medico.restablecerClave("otro-hash");
        IssuedToken temporal = jwtService.issue(medico);
        assertTrue(temporal.debeCambiarClave());
        assertEquals(Boolean.TRUE, decodificar(temporal.value()).getClaims().get("cc"));
    }
}
