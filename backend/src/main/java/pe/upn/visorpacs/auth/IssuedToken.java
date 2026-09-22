package pe.upn.visorpacs.auth;

public record IssuedToken(String value, long expiresInSeconds) {}
