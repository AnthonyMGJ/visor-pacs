-- Sprint 3 (HU08, HU12): rol ADMIN, permisos asignables y versión de sesión.
--  * ADMIN gestiona cuentas y permisos y consulta la auditoría; NO ve imágenes.
--  * permiso: catálogo de permisos. Hoy existe ACCESO_ESTUDIOS (el médico lo necesita para abrir estudios).
--  * usuario_permiso: permisos concedidos a cada usuario.
--  * usuario.sesion_version: se incrementa al cerrar sesión o desactivar la cuenta; los tokens emitidos
--    con una versión anterior dejan de valer en la siguiente solicitud (revocación inmediata).

INSERT INTO rol (nombre_rol, descripcion)
VALUES ('ADMIN', 'Administrador: gestiona cuentas y permisos y consulta la auditoría; no ve imágenes');

CREATE TABLE permiso (
  id_permiso  INT          NOT NULL AUTO_INCREMENT,
  codigo      VARCHAR(50)  NOT NULL,
  descripcion VARCHAR(150) DEFAULT NULL,
  PRIMARY KEY (id_permiso),
  UNIQUE KEY uk_permiso_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usuario_permiso (
  id_usuario INT NOT NULL,
  id_permiso INT NOT NULL,
  PRIMARY KEY (id_usuario, id_permiso),
  KEY idx_usuario_permiso_permiso (id_permiso),
  CONSTRAINT fk_usuario_permiso_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_usuario_permiso_permiso FOREIGN KEY (id_permiso) REFERENCES permiso (id_permiso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO permiso (codigo, descripcion)
VALUES ('ACCESO_ESTUDIOS', 'Puede buscar y abrir estudios de imágenes médicas');

-- Los médicos que ya existen conservan su acceso.
INSERT INTO usuario_permiso (id_usuario, id_permiso)
SELECT u.id_usuario, p.id_permiso
FROM usuario u
JOIN rol r ON r.id_rol = u.id_rol AND r.nombre_rol = 'MEDICO'
JOIN permiso p ON p.codigo = 'ACCESO_ESTUDIOS';

ALTER TABLE usuario ADD COLUMN sesion_version INT NOT NULL DEFAULT 0;
