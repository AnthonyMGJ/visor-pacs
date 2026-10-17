#!/usr/bin/env bash
# Crea (o actualiza) la cuenta de MySQL 'visor_app' con los permisos mínimos para la aplicación:
#   - SELECT/INSERT/UPDATE/DELETE en cada tabla del esquema, EXCEPTO:
#   - evento_auditoria: solo SELECT e INSERT (no puede borrar, editar ni quitar los triggers)
#   - sin permisos de estructura (CREATE, ALTER, DROP, TRIGGER...) ni sobre flyway_schema_history.
# Ejecutar DESPUÉS de que el backend haya aplicado las migraciones y de nuevo tras cada actualización que
# agregue tablas. NO HA SIDO PROBADO en MySQL 8.4 real: revise la salida y pruebe en un entorno de ensayo.
# Uso:  APP_DB_PASSWORD='clave-de-12-o-mas' ./docker/privilegios-minimos.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${APP_DB_PASSWORD:-}" ]]; then
  read -r -s -p "Clave para la cuenta visor_app (12+ caracteres): " APP_DB_PASSWORD; echo
fi
if [[ ! "$APP_DB_PASSWORD" =~ ^[A-Za-z0-9._@#%+=-]{12,}$ ]]; then
  echo "La clave debe tener 12+ caracteres y solo letras, números y . _ @ # % + = -" >&2
  exit 1
fi

DB="${DB_NAME:-visor_pacs}"
mysql_root() { docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --skip-column-names'; }

TABLAS=$(echo "SELECT table_name FROM information_schema.tables WHERE table_schema='${DB}' AND table_type='BASE TABLE';" | mysql_root)
if [[ -z "$TABLAS" ]]; then
  echo "No hay tablas en '${DB}': arranque primero el backend para que Flyway cree el esquema." >&2
  exit 1
fi

{
  echo "CREATE USER IF NOT EXISTS 'visor_app'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}';"
  echo "ALTER USER 'visor_app'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}';"
  echo "REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'visor_app'@'%';"
  while read -r tabla; do
    case "$tabla" in
      flyway_schema_history) ;;  # solo la usa el migrador
      evento_auditoria) echo "GRANT SELECT, INSERT ON \`${DB}\`.\`${tabla}\` TO 'visor_app'@'%';" ;;
      *) echo "GRANT SELECT, INSERT, UPDATE, DELETE ON \`${DB}\`.\`${tabla}\` TO 'visor_app'@'%';" ;;
    esac
  done <<< "$TABLAS"
  echo "FLUSH PRIVILEGES;"
  echo "SHOW GRANTS FOR 'visor_app'@'%';"
} | mysql_root

echo "Listo. Arranque con:  docker compose -f docker-compose.yml -f docker-compose.privilegios.yml up -d"
echo "(exporte antes APP_DB_PASSWORD o agréguela al .env)"
