package pe.upn.visorpacs.domain;

/** Nombres de los roles del sistema (tabla rol). */
public final class Roles {
    public static final String MEDICO = "MEDICO";
    public static final String PACIENTE = "PACIENTE";
    /** Gestiona cuentas y permisos y consulta la auditoría. Nunca ve imágenes. */
    public static final String ADMIN = "ADMIN";

    private Roles() {}
}
