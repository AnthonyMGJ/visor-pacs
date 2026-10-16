package pe.upn.visorpacs.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.domain.Roles;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public IssuedToken issue(Usuario usuario) {
        Instant now = clock.instant();
        long ttlSeconds = props.jwt().ttlMinutes() * 60;
        String rol = usuario.getRol().getNombre();
        String nombre = usuario.getNombres() + " " + usuario.getApellidos();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(usuario.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(rol))
                .claim("nombre", nombre);
        if (Roles.PACIENTE.equals(rol)) {
            if (usuario.getPaciente() == null) {
                throw new IllegalStateException("El usuario con rol PACIENTE no tiene un paciente asociado.");
            }
            // El proxy DICOMweb limita al paciente a los estudios con este PatientID.
            claims.claim("patientId", usuario.getPaciente().getDicomPacienteId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, ttlSeconds, rol, nombre);
    }
}
