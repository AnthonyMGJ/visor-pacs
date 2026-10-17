package pe.upn.visorpacs.sesion;

import java.util.Set;
import pe.upn.visorpacs.domain.Usuario;

/** Foto mínima del estado de una cuenta para validar un token sin cargar la entidad completa. */
public record EstadoCuenta(int id, boolean activo, int sesionVersion, String rol,
                           boolean habilitada, Set<String> permisos) {

    public static EstadoCuenta de(Usuario u) {
        Set<String> codigos = Set.copyOf(u.getPermisos().stream().map(p -> p.getCodigo()).toList());
        return new EstadoCuenta(u.getId(), u.isActivo(), u.getSesionVersion(), u.getRol().getNombre(),
                u.habilitada(), codigos);
    }

    public boolean tienePermiso(String codigo) {
        return permisos.contains(codigo);
    }
}
