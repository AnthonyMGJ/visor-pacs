package pe.upn.visorpacs.auth;

public record IssuedToken(String value, long expiresInSeconds, String rol, String nombre) {}
