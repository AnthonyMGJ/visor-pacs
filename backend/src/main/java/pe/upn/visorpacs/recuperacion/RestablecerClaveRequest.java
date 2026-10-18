package pe.upn.visorpacs.recuperacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerClaveRequest(@NotBlank @Size(max = 200) String token,
                                      @NotBlank @Size(max = 200) String claveNueva) {}
