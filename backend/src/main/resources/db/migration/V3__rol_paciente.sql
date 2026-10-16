-- Segundo rol del sistema: PACIENTE (solo ve sus propios estudios).
INSERT INTO rol (nombre_rol, descripcion)
VALUES ('PACIENTE', 'Paciente con acceso únicamente a sus propios estudios');

-- Un usuario con rol PACIENTE se vincula a su registro de paciente (dicom_paciente_id = PatientID en Orthanc).
ALTER TABLE usuario
  ADD COLUMN id_paciente INT NULL,
  ADD UNIQUE KEY uk_usuario_paciente (id_paciente),
  ADD CONSTRAINT fk_usuario_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente);
