# Despliegue en la nube con HTTPS (HT02 · T38–T40)

Este documento es el procedimiento; **no se ha ejecutado** en un servidor real. Cada paso indica cómo comprobarlo.

## Qué se despliega

```
Internet ──443──▶ Caddy (HTTPS automático) ──▶ backend :8080 ──▶ Orthanc :8042
                        │                                   └──▶ MySQL :3306
                        └──▶ frontend compilado (frontend/dist)
```

Solo Caddy publica puertos (80 y 443). Backend, Orthanc y MySQL quedan en la red interna de Docker; Orthanc y MySQL además escuchan solo en `127.0.0.1` del servidor.

## 1. Servidor

- VM Linux (Ubuntu 22.04/24.04 LTS), 2 vCPU y 4 GB de RAM como mínimo, disco según los estudios (la serie de 180 cortes sintética pesa ~90 MB).
- Docker Engine con Compose **2.24 o superior** (`docker compose version`).
- Dominio (o subdominio) con un registro DNS **A** hacia la IP pública. Sin dominio real no hay certificado de confianza: para ensayar use `DOMINIO=localhost`.
- Cortafuegos: permitir **solo** 22 (SSH, idealmente restringido a su IP), 80 y 443.
  ```bash
  sudo ufw default deny incoming && sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
  ```

## 2. Código y configuración

```bash
git clone <su-repositorio> visor-pacs && cd visor-pacs
cp .env.example .env && nano .env
```

En `.env` de producción (valores **nuevos**, nunca los de desarrollo):

| Variable | Valor |
|---|---|
| `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `ORTHANC_PASSWORD` | Contraseñas largas y distintas (`openssl rand -base64 24`) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `DOMINIO` | `visor.sudominio.com` |
| `OPENAPI_ENABLED` | `false` |
| `CORS_ALLOWED_ORIGINS` | `https://visor.sudominio.com` (con HTTPS el frontend y la API comparten origen: puede quedar vacío si solo se usa por Caddy) |
| `BOOTSTRAP_ENABLED` | `false` (las cuentas se crean con el comando de consola) |

`.env` nunca se sube a Git ni se copia en un zip. Permisos: `chmod 600 .env`.

## 3. Frontend

En su equipo: `cd frontend && npm ci && npm run build`; copiar `frontend/dist` al servidor en `visor-pacs/frontend/dist` (Caddy lo sirve y reescribe las rutas del SPA a `index.html`). Con `VITE_API_URL` vacío el frontend llama a `/api/...` en el mismo origen.

## 4. Levantar

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d --build
docker compose ps
docker compose logs -f caddy backend
```

Comprobación: `curl -sS https://visor.sudominio.com/actuator/health` → `{"status":"UP"}` y, en el navegador, candado válido.
Si Caddy no obtiene el certificado: revise que el DNS ya apunte al servidor, que 80/443 estén abiertos y los registros del contenedor `caddy`.

## 5. Primeras cuentas

```bash
bash docker/crear-usuario.sh ADMIN admin.ti "Nombre" "Apellido" admin@clinica.pe
bash docker/crear-usuario.sh MEDICO ana.perez "Ana" "Pérez" ana@clinica.pe
```

Las siguientes cuentas las crea el administrador desde el panel (`POST /api/admin/usuarios`). Los datos de pacientes **reales** no deben subirse sin el marco legal y de privacidad correspondiente; para pruebas use solo imágenes sintéticas.

## 6. Monitoreo de salud y disponibilidad (T40)

`/actuator/health` responde `UP` solo si la base de datos **y Orthanc** responden.

```bash
# una medición por minuto, en segundo plano
nohup ./docker/monitoreo/vigilar.sh https://visor.sudominio.com/actuator/health 60 docs/disponibilidad/disponibilidad.csv &
./docker/monitoreo/resumen.sh          # periodo, caídas, % de disponibilidad y latencia
```

Alternativa con cron (una medición por ejecución): `* * * * * cd /ruta/visor-pacs && ./docker/monitoreo/vigilar.sh https://visor.sudominio.com/actuator/health 0`.
Para alertas por correo o chat conviene un servicio externo (UptimeRobot, Better Stack…) apuntando a la misma URL; no sustituye al registro del CSV, que es la evidencia para el informe.

## 7. Copias de seguridad

```bash
# MySQL (cuentas, permisos, auditoría)
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers visor_pacs' | gzip > backup-$(date +%F).sql.gz
# Orthanc (imágenes): volumen orthanc_data
docker run --rm -v visor-pacs_orthanc_data:/datos -v "$PWD":/copia alpine tar czf /copia/orthanc-$(date +%F).tgz -C /datos .
```

Guárdelas fuera del servidor y pruebe una restauración antes de necesitarla.

## 8. Actualizar y volver atrás

```bash
git pull && docker compose -f docker-compose.yml -f docker-compose.https.yml up -d --build
```

Flyway aplica las migraciones nuevas al arrancar el backend. Antes de actualizar haga el respaldo de MySQL. Para volver atrás: `git checkout <etiqueta-anterior>`, reconstruir y, si una migración ya se aplicó, restaurar el respaldo (las migraciones no se revierten solas).

## 9. Endurecimiento recomendado (antes de publicar de verdad)

- **Auditoría realmente inmutable (permisos mínimos de base de datos)**: los triggers bloquean `UPDATE` y `DELETE`, pero con la cuenta por defecto (todos los permisos sobre la base) alguien podría hacer `TRUNCATE` o borrar los triggers. Se incluye un procedimiento opt-in (**escrito pero no probado contra su MySQL**):
  1. Arranque normal una vez, para que Flyway cree todas las tablas.
  2. Ejecute `docker/privilegios-minimos.sh` (Linux) o `docker/privilegios-minimos.ps1` (Windows). Crea el usuario `visor_app` con permisos tabla por tabla (`evento_auditoria` solo `SELECT`/`INSERT`; MySQL no permite quitar un permiso de tabla concedido a toda la base) y deja las migraciones a un usuario aparte (`DB_MIGRACION_*`).
  3. Levante con `docker compose -f docker-compose.yml -f docker-compose.privilegios.yml up -d` (activa el perfil Spring `privilegios-minimos`).
  4. **Repita el paso 2 cada vez que una migración nueva cree tablas**, o la aplicación no podrá usarlas.
  Compruebe: con `visor_app`, `TRUNCATE evento_auditoria;` y `DROP TRIGGER ...` deben dar «command denied».
- **Límite de intentos por IP**: el login ya limita intentos por IP (`LIMITE_LOGIN_*`, por defecto en `.env.example`; responde `429` con `Retry-After`). Cuenta todos los intentos de login, no solo los fallidos. Detrás de Caddy la IP real llega por `X-Forwarded-For`; añada además un límite en Caddy/WAF si quiere defensa en capas.
- **Recuperación de clave por correo (RF-09)**: viene **apagada** (`RECUPERACION_HABILITADA=false`). Para activarla: (1) cuenta SMTP propia con TLS y sus credenciales **solo en `.env`** (`SMTP_*`, ver `.env.example`); en pruebas, Mailpit (`docker compose --profile correo-dev up -d`, bandeja en `http://localhost:8025`, no envía correos reales); (2) `FRONTEND_URL` con la dirección pública (el enlace sale de aquí, nunca de la cabecera `Host`); (3) `VITE_RECUPERACION_HABILITADA=true` al compilar el frontend; (4) **repita `docker/privilegios-minimos.*`** si usa la cuenta `visor_app`, porque la migración V7 crea la tabla `recuperacion_clave`; (5) SPF/DKIM/DMARC del dominio remitente. Un servidor SMTP caído no afecta al servicio ni al `/actuator/health`: el envío es asíncrono y falla en silencio (queda en el log). Límites: 3 correos/cuenta/hora y `RECUPERACION_MAX_IP` solicitudes/IP/hora (429).
- **Revocación individual de tokens**: la lista de tokens revocados está en memoria y en la tabla `token_revocado`; con **más de una instancia** del backend cada una la carga al arrancar, así que una revocación puede tardar en verse en las demás.
- Rotar los secretos de desarrollo si alguna vez estuvieron en un `.env` compartido o en un zip.
- Puertos 8080/8042/3306 cerrados hacia Internet (compruebe con `nmap` desde fuera).
- Actualizaciones del sistema operativo y de las imágenes (`docker compose pull`).
- La IP que se registra en la auditoría viene de `X-Forwarded-For` (lo pone Caddy). Con el backend publicado directamente en desarrollo (`8080`) ese encabezado podría falsearse; por eso, en producción, el backend no publica puertos.
