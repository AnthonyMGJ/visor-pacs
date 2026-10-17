package pe.upn.visorpacs.auth;

import java.util.List;

/** Datos de la cuenta autenticada (GET /api/auth/me). */
public record PerfilResponse(String username, String nombre, String rol, List<String> permisos) {}
