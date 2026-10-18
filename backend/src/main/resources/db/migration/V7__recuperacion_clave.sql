-- RF-09: recuperación de clave por correo (MEDICO y PACIENTE).
-- Tabla nueva; no se modifica ninguna tabla existente. Del token solo se guarda su hash SHA-256:
-- quien lea la base no puede reconstruir el enlace enviado al correo.
--  * usado_en: se marca al usarse o al anularse (solicitud más reciente, cambio de clave); NULL = pendiente.
--  * Un token vale una sola vez y vence en vence_en (15 minutos por defecto, configurable).

CREATE TABLE recuperacion_clave (
  id_recuperacion BIGINT      NOT NULL AUTO_INCREMENT,
  id_usuario      INT         NOT NULL,
  token_hash      CHAR(64)    NOT NULL,
  creado_en       DATETIME(3) NOT NULL,
  vence_en        DATETIME(3) NOT NULL,
  usado_en        DATETIME(3) NULL,
  ip              VARCHAR(45) NULL,
  PRIMARY KEY (id_recuperacion),
  UNIQUE KEY uk_recuperacion_token (token_hash),
  KEY idx_recuperacion_usuario (id_usuario, creado_en),
  CONSTRAINT fk_recuperacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
