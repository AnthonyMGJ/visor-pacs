package pe.upn.visorpacs.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
