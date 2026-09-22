package pe.upn.visorpacs.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import pe.upn.visorpacs.config.AppProperties;
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
                new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Bootstrap(false, "", "", "", "", ""));
        Usuario medico = Usuario.nuevo("medico.demo", "hash", "Ana", "Pérez", "ana@example.com", new Rol("MEDICO"));

        IssuedToken token = new JwtService(encoder, props, Clock.systemUTC()).issue(medico);
        Jwt jwt = decoder.decode(token.value());

        assertEquals("medico.demo", jwt.getSubject());
        assertEquals("visor-pacs", jwt.getClaimAsString("iss"));
        assertEquals(List.of("MEDICO"), jwt.getClaimAsStringList("roles"));
        assertEquals(900, token.expiresInSeconds());
    }
}
