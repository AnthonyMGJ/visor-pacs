-- Sprint 3 (HU12): cambio/restablecimiento de clave y revocación de un token concreto.
--  * usuario.debe_cambiar_clave: se activa cuando un ADMIN restablece la clave; mientras esté activo el
--    token solo sirve para cambiar la clave, ver el perfil y cerrar sesión.
--  * token_revocado: identificadores (jti) de tokens cerrados de forma individual. Se guardan solo hasta
--    que el token vence por sí mismo; después la fila se puede borrar sin riesgo.

ALTER TABLE usuario ADD COLUMN debe_cambiar_clave TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE token_revocado (
  jti         VARCHAR(64) NOT NULL,
  id_usuario  INT         NOT NULL,
  expira_en   DATETIME(3) NOT NULL,
  revocado_en DATETIME(3) NOT NULL,
  PRIMARY KEY (jti),
  KEY idx_token_revocado_expira (expira_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
