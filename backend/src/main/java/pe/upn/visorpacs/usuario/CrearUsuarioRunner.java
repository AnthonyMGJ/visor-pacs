package pe.upn.visorpacs.usuario;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pe.upn.visorpacs.domain.Roles;
import pe.upn.visorpacs.domain.Usuario;

/**
 * Comando de una sola ejecución: crea un usuario y cierra la aplicación.
 * Se activa con --app.cli.crear-usuario=true (ver docker/crear-usuario.sh y .ps1).
 * La clave llega por la variable de entorno NUEVO_USUARIO_PASSWORD, nunca por argumento.
 */
@Component
@ConditionalOnProperty(prefix = "app.cli", name = "crear-usuario", havingValue = "true")
public class CrearUsuarioRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrearUsuarioRunner.class);

    private final UsuarioService servicio;
    private final Environment env;
    private final ConfigurableApplicationContext contexto;

    public CrearUsuarioRunner(UsuarioService servicio, Environment env, ConfigurableApplicationContext contexto) {
        this.servicio = servicio;
        this.env = env;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments args) {
        int codigo = 0;
        try {
            String rol = env.getProperty("app.cli.rol", "").trim().toUpperCase(Locale.ROOT);
            String username = env.getProperty("app.cli.username", "");
            String nombres = env.getProperty("app.cli.nombres", "");
            String apellidos = env.getProperty("app.cli.apellidos", "");
            String email = env.getProperty("app.cli.email", "");
            String clave = System.getenv("NUEVO_USUARIO_PASSWORD");
            Usuario creado = switch (rol) {
                case Roles.MEDICO -> servicio.crearMedico(username, clave, nombres, apellidos, email);
                case Roles.PACIENTE -> servicio.crearPaciente(username, clave, nombres, apellidos, email,
                        env.getProperty("app.cli.dicom-id", ""));
                default -> throw new UsuarioInvalidoException("El rol debe ser MEDICO o PACIENTE.");
            };
            log.info("Usuario '{}' creado con rol {}.", creado.getUsername(), creado.getRol().getNombre());
        } catch (UsuarioInvalidoException | IllegalStateException e) {
            log.error("No se creó el usuario: {}", e.getMessage());
            codigo = 1;
        }
        int salida = codigo;
        System.exit(SpringApplication.exit(contexto, () -> salida));
    }
}
