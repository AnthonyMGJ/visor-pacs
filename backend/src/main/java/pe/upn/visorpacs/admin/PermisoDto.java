package pe.upn.visorpacs.admin;

import pe.upn.visorpacs.domain.Permiso;

public record PermisoDto(String codigo, String descripcion) {
    public static PermisoDto de(Permiso p) {
        return new PermisoDto(p.getCodigo(), p.getDescripcion());
    }
}
