package pe.upn.visorpacs.auth;

/** debeCambiarClave: la cuenta tiene una clave temporal; el token solo permite cambiarla. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String rol, String nombre,
                            boolean debeCambiarClave) {}
