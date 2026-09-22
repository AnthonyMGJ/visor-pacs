package pe.upn.visorpacs.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;

/** Casos de HU01: acceso válido, rechazo genérico y bloqueo temporal tras intentos fallidos. */
@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;
    private final Clock clock;
    private final String dummyHash; // para gastar el mismo tiempo cuando el usuario no existe

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AppProperties props, Clock clock) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("relleno-no-es-una-clave-real");
    }

    // Las excepciones NO deben revertir la transacción: el contador de intentos debe guardarse.
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public IssuedToken login(String username, String rawPassword) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Usuario usuario = usuarios.findByUsername(username).orElse(null);

        if (usuario == null || !usuario.isActivo()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw new InvalidCredentialsException();
        }
        if (usuario.estaBloqueado(ahora)) {
            throw new AccountLockedException();
        }
        if (!passwordEncoder.matches(rawPassword, usuario.getPassword())) {
            usuario.registrarIntentoFallido(props.security().maxFailedAttempts(),
                    Duration.ofMinutes(props.security().lockMinutes()), ahora);
            usuarios.save(usuario);
            throw new InvalidCredentialsException();
        }
        usuario.registrarAccesoExitoso();
        usuarios.save(usuario);
        return jwtService.issue(usuario);
    }
}
