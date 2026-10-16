package pe.upn.visorpacs.usuario;

import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upn.visorpacs.domain.Paciente;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Roles;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.PacienteRepository;
import pe.upn.visorpacs.repository.RolRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;

/**
 * Única vía para crear cuentas (médicos y pacientes): valida, cifra la clave con BCrypt y enlaza al paciente.
 * Hoy la usan el arranque de desarrollo y el comando crear-usuario; mañana, un endpoint o una pantalla.
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

    public UsuarioService(UsuarioRepository usuarios, RolRepository roles,
                          PacienteRepository pacientes, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.pacientes = pacientes;
        this.encoder = encoder;
    }

    @Transactional
    public Usuario crearMedico(String username, String clave, String nombres, String apellidos, String email) {
        return crear(Roles.MEDICO, username, clave, nombres, apellidos, email, null);
    }

    /** dicomPacienteId = PatientID (0010,0020) con el que sus estudios están guardados en Orthanc. */
    @Transactional
    public Usuario crearPaciente(String username, String clave, String nombres, String apellidos,
                                 String email, String dicomPacienteId) {
        return crear(Roles.PACIENTE, username, clave, nombres, apellidos, email,
                limpio(dicomPacienteId, "El PatientID DICOM", 100));
    }

    private Usuario crear(String nombreRol, String username, String clave, String nombres,
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
        if (!EMAIL.matcher(correo).matches()) {
            throw new UsuarioInvalidoException("El correo no tiene un formato válido.");
        }
        if (usuarios.findByUsername(user).isPresent()) {
            throw new UsuarioInvalidoException("Ya existe un usuario con ese nombre de usuario.");
        }
        if (usuarios.existsByEmail(correo)) {
            throw new UsuarioInvalidoException("Ya existe un usuario con ese correo.");
        }
        Rol rol = roles.findByNombre(nombreRol)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + nombreRol + " (migraciones de Flyway)."));
        String hash = encoder.encode(clave);
        if (dicomPacienteId == null) {
            return usuarios.save(Usuario.nuevo(user, hash, nom, ape, correo, rol));
        }
        Paciente paciente = pacientes.findByDicomPacienteId(dicomPacienteId).orElse(null);
        if (paciente != null && paciente.getId() != null && usuarios.existsByPacienteId(paciente.getId())) {
            throw new UsuarioInvalidoException("Ese paciente ya tiene una cuenta de usuario.");
        }
        if (paciente == null) {
            String completo = (nom + " " + ape).trim();
            paciente = pacientes.save(Paciente.nuevo(dicomPacienteId,
                    completo.length() > 100 ? completo.substring(0, 100) : completo));
        }
        return usuarios.save(Usuario.nuevoPaciente(user, hash, nom, ape, correo, rol, paciente));
    }

    private static String limpio(String valor, String campo, int max) {
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
