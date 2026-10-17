package pe.upn.visorpacs.sesion;

/** La cuenta está autenticada pero no tiene el permiso requerido (se responde 403). */
public class SinPermisoException extends RuntimeException {
    public SinPermisoException(String codigo) {
        super("Falta el permiso " + codigo);
    }
}
