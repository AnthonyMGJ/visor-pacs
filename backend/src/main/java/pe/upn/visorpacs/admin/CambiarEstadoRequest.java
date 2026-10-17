package pe.upn.visorpacs.admin;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull Boolean activo) {}
