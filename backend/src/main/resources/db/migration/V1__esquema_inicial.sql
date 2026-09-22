-- Esquema inicial del Visor PACS (basado en el diseño del equipo, con correcciones):
--  * dicom_paciento_id  -> dicom_paciente_id (typo)
--  * usuario: se agregan intentos_fallidos y bloqueado_hasta (HU01, bloqueo por intentos), email único
--  * password ampliado a 255 (permite otros algoritmos de hash a futuro)
--  * nombres de constraints con prefijo fk_ (evita choques de nombres)

CREATE TABLE rol (
  id_rol      INT          NOT NULL AUTO_INCREMENT,
  nombre_rol  VARCHAR(50)  NOT NULL,
  descripcion VARCHAR(150) DEFAULT NULL,
  PRIMARY KEY (id_rol),
  UNIQUE KEY uk_rol_nombre (nombre_rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usuario (
  id_usuario        INT          NOT NULL AUTO_INCREMENT,
  username          VARCHAR(50)  NOT NULL,
  password          VARCHAR(255) NOT NULL,
  nombres           VARCHAR(100) NOT NULL,
  apellidos         VARCHAR(100) NOT NULL,
  email             VARCHAR(100) NOT NULL,
  estado            TINYINT(1)   NOT NULL DEFAULT 1,
  intentos_fallidos INT          NOT NULL DEFAULT 0,
  bloqueado_hasta   DATETIME     NULL,
  fecha_creacion    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  id_rol            INT          NOT NULL,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uk_usuario_username (username),
  UNIQUE KEY uk_usuario_email (email),
  KEY idx_usuario_rol (id_rol),
  CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE paciente (
  id_paciente       INT          NOT NULL AUTO_INCREMENT,
  dicom_paciente_id VARCHAR(100) NOT NULL,
  nombres_paciente  VARCHAR(100) NOT NULL,
  fecha_nacimiento  DATE         DEFAULT NULL,
  sexo              CHAR(1)      DEFAULT NULL,
  fecha_registro    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_paciente),
  UNIQUE KEY uk_paciente_dicom (dicom_paciente_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE estudio (
  id_estudio          INT          NOT NULL AUTO_INCREMENT,
  id_paciente         INT          NOT NULL,
  dicom_estudio_id    VARCHAR(100) NOT NULL COMMENT 'StudyInstanceUID (el que usa DICOMweb)',
  fecha_estudio       DATE         NOT NULL,
  descripcion_estudio VARCHAR(255) DEFAULT NULL,
  modalidad           VARCHAR(50)  NOT NULL,
  institucion         VARCHAR(100) DEFAULT NULL,
  PRIMARY KEY (id_estudio),
  UNIQUE KEY uk_estudio_dicom (dicom_estudio_id),
  KEY idx_estudio_paciente (id_paciente),
  KEY idx_estudio_fecha (fecha_estudio),
  CONSTRAINT fk_estudio_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE historial_visualizacion (
  id_historial INT       NOT NULL AUTO_INCREMENT,
  id_usuario   INT       NOT NULL,
  id_estudio   INT       NOT NULL,
  fecha_acceso TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_historial),
  KEY idx_historial_usuario (id_usuario),
  KEY idx_historial_estudio (id_estudio),
  CONSTRAINT fk_historial_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_historial_estudio FOREIGN KEY (id_estudio) REFERENCES estudio (id_estudio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
