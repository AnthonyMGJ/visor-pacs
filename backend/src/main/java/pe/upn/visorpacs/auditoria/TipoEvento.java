package pe.upn.visorpacs.auditoria;

/** Tipos de evento que se registran en la auditoría. */
public enum TipoEvento {
    INICIO_SESION,
    CIERRE_SESION,
    APERTURA_ESTUDIO,
    ACCESO_ESTUDIO_DENEGADO,
    USUARIO_CREADO,
    USUARIO_EDITADO,
    USUARIO_ACTIVADO,
    USUARIO_DESACTIVADO,
    PERMISO_OTORGADO,
    PERMISO_REVOCADO,
    CLAVE_CAMBIADA,
    CLAVE_RESTABLECIDA
}
