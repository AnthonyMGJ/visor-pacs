package pe.upn.visorpacs.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarClaveRequest(
        @NotBlank @Size(max = 200) String claveActual,
        @NotBlank @Size(max = 200) String claveNueva) {}
