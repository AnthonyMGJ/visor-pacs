package pe.upn.visorpacs.auth;

/** Al cambiar la clave, la "clave actual" enviada no coincide (se responde 400, no 401, para no cerrar la sesión en el cliente). */
public class ClaveActualIncorrectaException extends RuntimeException {
    public ClaveActualIncorrectaException() {
        super("La clave actual no es correcta.");
    }
}
