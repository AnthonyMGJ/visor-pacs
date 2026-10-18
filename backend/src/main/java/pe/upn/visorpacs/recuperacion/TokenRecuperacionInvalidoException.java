package pe.upn.visorpacs.recuperacion;

/** El enlace no existe, ya se usó, venció o la cuenta ya no puede recuperarse. El motivo exacto no se revela. */
public class TokenRecuperacionInvalidoException extends RuntimeException {
    public TokenRecuperacionInvalidoException() {
        super("El enlace no es válido o ya venció. Solicita uno nuevo.");
    }
}
