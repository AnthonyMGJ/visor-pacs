package pe.upn.visorpacs.web;

/** El recurso pedido no existe (se responde 404). El mensaje es apto para mostrar. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
