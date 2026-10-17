package pe.upn.visorpacs.web;

/** La petición es válida pero choca con el estado o las reglas del sistema (se responde 409). */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
