package pe.upn.visorpacs.usuario;

import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upn.visorpacs.domain.Paciente;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Roles;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.PacienteRepository;
import pe.upn.visorpacs.repository.PermisoRepository;
import pe.upn.visorpacs.repository.RolRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;

/**
 * Única vía para crear cuentas (médicos, pacientes y administradores): valida, cifra la clave con BCrypt,
 * enlaza al paciente y concede a los médicos el permiso de acceso a estudios.
 * La usan el arranque de desarrollo, el comando crear-usuario y el módulo de administración.
 */
@Service
public class UsuarioService {

    public static final int CLAVE_MINIMA = 12;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PacienteRepository pacientes;
    private final PasswordEncoder encoder;
    private final PermisoRepository permisos;

    public UsuarioService(UsuarioRepository usuarios, RolRepository roles, PacienteRepository pacientes,
                          PasswordEncoder encoder, PermisoRepository permisos) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.pacientes = pacientes;
        this.encoder = encoder;
        this.permisos = permisos;
    }

    /** Las cuentas ADMIN solo se crean por consola o arranque (no por la API), para evitar escalada de privilegios. */
    @Transactional
    public Usuario crearAdmin(String username, String clave, String nombres, String apellidos, String email) {
        return crearCuenta(Roles.ADMIN, username, clave, nombres, apellidos, email, null);
    }

    @Transactional
    public Usuario crearMedico(String username, String clave, String nombres, String apellidos, String email) {
        return crearCuenta(Roles.MEDICO, username, clave, nombres, apellidos, email, null);
    }

    /** dicomPacienteId = PatientID (0010,0020) con el que sus estudios están guardados en Orthanc. */
    @Transactional
    public Usuario crearPaciente(String username, String clave, String nombres, String apellidos,
                                 String email, String dicomPacienteId) {
        return crearCuenta(Roles.PACIENTE, username, clave, nombres, apellidos, email,
                limpio(dicomPacienteId, "El PatientID DICOM", 100));
    }

    /** Crea una cuenta de cualquier rol. dicomPacienteId solo aplica (y es obligatorio) para PACIENTE. */
    @Transactional
    public Usuario crearCuenta(String nombreRol, String username, String clave, String nombres,
                               String apellidos, String email, String dicomPacienteId) {
        String user = limpio(username, "El usuario", 50);
        if (!USERNAME.matcher(user).matches()) {
            throw new UsuarioInvalidoException(
                    "El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.");
        }
        if (clave == null || clave.length() < CLAVE_MINIMA) {
            throw new UsuarioInvalidoException("La clave debe tener al menos " + CLAVE_MINIMA + " caracteres.");
        }
        String nom = limpio(nombres, "Los nombres", 100);
        String ape = limpio(apellidos, "Los apellidos", 100);
        String correo = limpio(email, "El correo", 100);
        validarCorreo(correo);
        if (usuarios.findByUsername(user).isPresent()) {
            throw new UsuarioDuplicadoException("Ya existe un usuario con ese nombre de usuario.");
        }
        if (usuarios.existsByEmail(correo)) {
            throw new UsuarioDuplicadoException("Ya existe un usuario con ese correo.");
        }
        // Solo el PACIENTE se enlaza a un registro de paciente; para los demás roles se ignora.
        dicomPacienteId = Roles.PACIENTE.equals(nombreRol)
                ? limpio(dicomPacienteId, "El PatientID DICOM", 100) : null;
        Rol rol = roles.findByNombre(nombreRol)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + nombreRol + " (migraciones de Flyway)."));
        String hash = encoder.encode(clave);
        if (dicomPacienteId == null) {
            Usuario nuevo = Usuario.nuevo(user, hash, nom, ape, correo, rol);
            if (Roles.MEDICO.equals(nombreRol)) {
                Permiso acceso = permisos.findByCodigo(Permisos.ACCESO_ESTUDIOS).orElseThrow(() ->
                        new IllegalStateException("Falta el permiso " + Permisos.ACCESO_ESTUDIOS + " (migraciones de Flyway)."));
                nuevo.otorgar(acceso);
            }
            return usuarios.save(nuevo);
        }
        Paciente paciente = pacientes.findByDicomPacienteId(dicomPacienteId).orElse(null);
        if (paciente != null && paciente.getId() != null && usuarios.existsByPacienteId(paciente.getId())) {
            throw new UsuarioDuplicadoException("Ese paciente ya tiene una cuenta de usuario.");
        }
        if (paciente == null) {
            String completo = (nom + " " + ape).trim();
            paciente = pacientes.save(Paciente.nuevo(dicomPacienteId,
                    completo.length() > 100 ? completo.substring(0, 100) : completo));
        }
        return usuarios.save(Usuario.nuevoPaciente(user, hash, nom, ape, correo, rol, paciente));
    }

    /** Sin caracteres que se confunden al leerlos (0/O, 1/l/I) porque el ADMIN la dicta o la copia a mano. */
    private static final String ALFABETO_TEMPORAL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int LARGO_TEMPORAL = 16;
    private static final SecureRandom AZAR = new SecureRandom();

    /**
     * Fija una clave temporal aleatoria para la cuenta, la marca como "debe cambiarla" y cierra sus sesiones.
     * Devuelve la clave EN CLARO una sola vez: no se guarda ni se registra en ninguna parte.
     */
    public String restablecerClave(Usuario usuario) {
        StringBuilder sb = new StringBuilder(LARGO_TEMPORAL);
        for (int i = 0; i < LARGO_TEMPORAL; i++) {
            sb.append(ALFABETO_TEMPORAL.charAt(AZAR.nextInt(ALFABETO_TEMPORAL.length())));
        }
        String temporal = sb.toString();
        usuario.restablecerClave(encoder.encode(temporal));
        usuarios.save(usuario);
        return temporal;
    }

    public static void validarCorreo(String correo) {
        if (!EMAIL.matcher(correo).matches()) {
            throw new UsuarioInvalidoException("El correo no tiene un formato válido.");
        }
    }

    public static String limpio(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) {
            throw new UsuarioInvalidoException(campo + " es obligatorio.");
        }
        String v = valor.trim();
        if (v.length() > max) {
            throw new UsuarioInvalidoException(campo + " no puede superar " + max + " caracteres.");
        }
        return v;
    }
}
