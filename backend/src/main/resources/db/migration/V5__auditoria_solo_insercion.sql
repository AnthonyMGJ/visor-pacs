-- Sprint 3 (HU10): registro de auditoría de SOLO INSERCIÓN (CA10.4).
-- Sin claves foráneas a propósito: el evento se guarda en una transacción aparte y debe sobrevivir
-- aunque la operación original se revierta; además conserva el nombre de usuario aunque no exista.
-- Los triggers impiden UPDATE y DELETE desde cualquier conexión (incluida la de la aplicación).
-- Nota: crear triggers con el registro binario activo exige --log-bin-trust-function-creators=1
-- (ya incluido en docker-compose.yml y en las pruebas con Testcontainers).

CREATE TABLE evento_auditoria (
  id_evento        BIGINT       NOT NULL AUTO_INCREMENT,
  fecha_evento     DATETIME(3)  NOT NULL,
  tipo             VARCHAR(40)  NOT NULL,
  resultado        VARCHAR(10)  NOT NULL,
  id_usuario       INT          NULL,
  username         VARCHAR(50)  NULL,
  rol              VARCHAR(20)  NULL,
  dicom_estudio_id VARCHAR(100) NULL,
  ip               VARCHAR(45)  NULL,
  detalle          VARCHAR(255) NULL,
  PRIMARY KEY (id_evento),
  KEY idx_auditoria_fecha (fecha_evento),
  KEY idx_auditoria_usuario_fecha (id_usuario, fecha_evento),
  KEY idx_auditoria_username (username),
  KEY idx_auditoria_tipo_fecha (tipo, fecha_evento),
  KEY idx_auditoria_estudio (dicom_estudio_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TRIGGER trg_evento_auditoria_no_update
BEFORE UPDATE ON evento_auditoria
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evento_auditoria es de solo insercion';

CREATE TRIGGER trg_evento_auditoria_no_delete
BEFORE DELETE ON evento_auditoria
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'evento_auditoria es de solo insercion';
