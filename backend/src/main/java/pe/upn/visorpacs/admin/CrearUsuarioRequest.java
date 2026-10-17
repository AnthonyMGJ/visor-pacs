package pe.upn.visorpacs.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** rol: MEDICO o PACIENTE. dicomPacienteId (PatientID en Orthanc) es obligatorio solo para PACIENTE. */
public record CrearUsuarioRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 12, max = 128) String clave,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank String rol,
        @Size(max = 100) String dicomPacienteId) {}
