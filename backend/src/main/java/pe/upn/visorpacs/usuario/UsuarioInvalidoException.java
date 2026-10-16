package pe.upn.visorpacs.usuario;

/** Datos inválidos al crear un usuario (clave corta, usuario repetido, etc.). El mensaje es apto para mostrar. */
public class UsuarioInvalidoException extends RuntimeException {
    public UsuarioInvalidoException(String mensaje) {
        super(mensaje);
    }
}
