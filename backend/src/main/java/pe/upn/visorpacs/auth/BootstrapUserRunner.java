package pe.upn.visorpacs.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.usuario.UsuarioService;

/** Crea un médico (y opcionalmente un paciente) de prueba al arrancar. Solo si BOOTSTRAP_ENABLED=true. */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
public class BootstrapUserRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUserRunner.class);

    private final AppProperties props;
    private final UsuarioRepository usuarios;
    private final UsuarioService servicio;

    public BootstrapUserRunner(AppProperties props, UsuarioRepository usuarios, UsuarioService servicio) {
        this.props = props;
        this.usuarios = usuarios;
        this.servicio = servicio;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap cfg = props.bootstrap();
        if (cfg.username() == null || cfg.username().isBlank()
                || cfg.password() == null || cfg.password().length() < UsuarioService.CLAVE_MINIMA) {
            throw new IllegalStateException(
                    "BOOTSTRAP_USERNAME es obligatorio y BOOTSTRAP_PASSWORD debe tener al menos 12 caracteres.");
        }
        if (usuarios.findByUsername(cfg.username()).isEmpty()) {
            servicio.crearMedico(cfg.username(), cfg.password(), cfg.nombres(), cfg.apellidos(), cfg.email());
            log.info("Usuario inicial '{}' creado.", cfg.username());
        }
        crearPacienteDePrueba(cfg);
    }

    private void crearPacienteDePrueba(AppProperties.Bootstrap cfg) {
        String username = cfg.pacienteUsername();
        if (username == null || username.isBlank()) {
            return; // paciente de prueba no configurado
        }
        if (cfg.pacientePassword() == null || cfg.pacientePassword().length() < UsuarioService.CLAVE_MINIMA
                || cfg.pacienteDicomId() == null || cfg.pacienteDicomId().isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_PACIENTE_PASSWORD debe tener al menos 12 caracteres y BOOTSTRAP_PACIENTE_DICOM_ID es obligatorio.");
        }
        if (usuarios.findByUsername(username).isEmpty()) {
            servicio.crearPaciente(username, cfg.pacientePassword(), cfg.pacienteNombres(), "de Prueba",
                    username + "@example.com", cfg.pacienteDicomId());
            log.info("Paciente de prueba '{}' creado (PatientID {}).", username, cfg.pacienteDicomId());
        }
    }
}
