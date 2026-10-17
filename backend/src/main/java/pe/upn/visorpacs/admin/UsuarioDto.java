package pe.upn.visorpacs.admin;

import java.time.LocalDateTime;
import java.util.List;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Usuario;

/** Vista de una cuenta para el administrador. Nunca incluye la clave ni su hash. */
public record UsuarioDto(Integer id, String username, String nombres, String apellidos, String email,
                         String rol, boolean activo, boolean bloqueado, String dicomPacienteId,
                         List<String> permisos) {

    public static UsuarioDto de(Usuario u, LocalDateTime ahora) {
        return new UsuarioDto(u.getId(), u.getUsername(), u.getNombres(), u.getApellidos(), u.getEmail(),
                u.getRol().getNombre(), u.isActivo(), u.estaBloqueado(ahora),
                u.getPaciente() == null ? null : u.getPaciente().getDicomPacienteId(),
                u.getPermisos().stream().map(Permiso::getCodigo).sorted().toList());
    }
}
