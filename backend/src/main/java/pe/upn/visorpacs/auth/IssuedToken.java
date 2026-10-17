package pe.upn.visorpacs.auth;

public record IssuedToken(String value, long expiresInSeconds, String rol, String nombre, boolean debeCambiarClave) {

    public IssuedToken(String value, long expiresInSeconds, String rol, String nombre) {
        this(value, expiresInSeconds, rol, nombre, false);
    }
}
