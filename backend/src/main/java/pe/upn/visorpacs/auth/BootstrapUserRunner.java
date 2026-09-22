package pe.upn.visorpacs.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.RolRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;

/** Crea un médico de prueba al arrancar. Solo si BOOTSTRAP_ENABLED=true (desarrollo). */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
public class BootstrapUserRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapUserRunner.class);
    private final AppProperties props;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;

    public BootstrapUserRunner(AppProperties props, UsuarioRepository usuarios,
                               RolRepository roles, PasswordEncoder encoder) {
        this.props = props;
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap cfg = props.bootstrap();
        if (cfg.username() == null || cfg.username().isBlank()
                || cfg.password() == null || cfg.password().length() < 12) {
            throw new IllegalStateException(
                    "BOOTSTRAP_USERNAME es obligatorio y BOOTSTRAP_PASSWORD debe tener al menos 12 caracteres.");
        }
        if (usuarios.findByUsername(cfg.username()).isPresent()) {
            return;
        }
        Rol rol = roles.findByNombre("MEDICO")
                .orElseThrow(() -> new IllegalStateException("Falta el rol MEDICO (migración V2)."));
        usuarios.save(Usuario.nuevo(cfg.username(), encoder.encode(cfg.password()),
                cfg.nombres(), cfg.apellidos(), cfg.email(), rol));
        log.info("Usuario inicial '{}' creado.", cfg.username());
    }
}
