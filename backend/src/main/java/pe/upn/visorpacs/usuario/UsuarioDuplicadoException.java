package pe.upn.visorpacs.usuario;

/** Ya existe una cuenta con ese usuario, correo o paciente (se responde 409). */
public class UsuarioDuplicadoException extends UsuarioInvalidoException {
    public UsuarioDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
