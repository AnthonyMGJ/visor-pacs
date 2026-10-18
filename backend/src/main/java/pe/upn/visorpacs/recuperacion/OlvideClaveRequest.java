package pe.upn.visorpacs.recuperacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sin @Email a propósito: un correo mal escrito recibe la misma respuesta genérica que uno no registrado. */
public record OlvideClaveRequest(@NotBlank @Size(max = 100) String email) {}
