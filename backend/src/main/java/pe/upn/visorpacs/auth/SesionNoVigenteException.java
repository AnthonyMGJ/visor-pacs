package pe.upn.visorpacs.auth;

/** La cuenta ya no puede mantener la sesión (desactivada, sin permiso o eliminada). */
public class SesionNoVigenteException extends RuntimeException {
    public SesionNoVigenteException() {
        super("Sesión no vigente");
    }
}
