# Hallazgos de la verificación (17 de octubre de 2026)

Registro de los fallos que aparecieron al verificar el backend de Sprint 1 a 3 en un entorno real
(Docker + Orthanc + MySQL 8.4), con su causa, su corrección y la lección. Ningún valor secreto se
reproduce en este documento.

## 1. Claves de demo expuestas en una evidencia versionada

| | |
|---|---|
| **Cómo se detectó** | Antes del `push`, se buscó cada valor sensible del `.env` en todo el historial con `git grep -F` sobre `git rev-list --all`. Se imprimió solo el nombre de la variable y el número de apariciones. |
| **Qué se encontró** | La clave de `medico.demo` y la de `paciente.demo` aparecían en `docs/pruebas/evidencias/antes/newman-antes.json` (commit de la línea base, ya publicado en `origin/prueba/linea-base`). `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `ORTHANC_PASSWORD`, `JWT_SECRET` y la clave del ADMIN: 0 apariciones. Tokens JWT en el historial: ninguno. El `.env` nunca estuvo versionado. |
| **Causa** | Newman exporta en su reporte JSON las variables de entorno recibidas con `--env-var`. Al pasar las claves por esa vía y guardar el JSON como evidencia, las claves quedaron dentro del archivo. |
| **Impacto** | Solo cuentas de demostración. El resto de secretos no se vio comprometido. |
| **Corrección** | Se rotaron ambas claves con el flujo del ADMIN (`restablecer-clave`, luego `cambiar-clave`) y se actualizó el `.env`. Se verificó con Newman Sprint 1: 10 de 10 peticiones y 11 de 11 aserciones. Las evidencias nuevas (`docs/pruebas/evidencias/despues/`) quedan fuera del repositorio mediante `.gitignore`. |
| **Lo que no se hizo** | No se reescribió el historial: la rama ya estaba publicada. Los valores antiguos siguen en el historial, pero ya no abren ninguna cuenta. |
| **Pendiente** | Rotar al final de las pruebas `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `ORTHANC_PASSWORD`, `JWT_SECRET` y la clave del ADMIN. Confirmar que el repositorio es privado. |

**Lecciones.** (a) Las evidencias de pruebas se tratan como datos sensibles: se revisan antes de
versionarlas. (b) Se busca por *valor* de los secretos reales, no solo por patrones. (c) Los reportes
de herramientas pueden incluir la configuración con la que se ejecutaron.

### Fallo en la propia rotación
El primer intento de rotación falló: en Windows PowerShell 5.1 no existe
`RandomNumberGenerator.GetInt32`, la clave generada salió vacía, el servidor respondió 400 y, aun así,
el script imprimió "OK" y dejó vacías dos variables del `.env`. Se corrigió con otro generador de
bytes aleatorios, `$ErrorActionPreference = 'Stop'`, validación de largo mínimo y comprobación de
que se puede iniciar sesión con la clave nueva antes de tocar el `.env`.
**Lección:** un script que cambia credenciales debe detenerse al primer error y verificar el
resultado antes de declarar éxito.

## 2. Otros defectos encontrados al ejecutar de verdad

| Hallazgo | Causa | Corrección |
|---|---|---|
| Colección Postman Sprint 1: el login del paciente daba 401 | El request heredaba `Bearer {{token}}` de la colección, el token del médico que el paso anterior (logout) acababa de revocar. Un Bearer inválido se rechaza con 401 incluso en un endpoint público. | Request 08 marcado como `noauth`. |
| Newman Sprint 3: 91 fallos en la primera corrida | El `.env` no tenía las variables `BOOTSTRAP_ADMIN_*`, así que no existía la cuenta ADMIN. | Agregar las variables y reiniciar el stack: 67 de 67 peticiones. |
| `SesionTokenValidatorTest` no compilaba | `Jwt.Builder` no tiene `.id(...)`; el método correcto es `.jti(...)`. | Corregido. |
| `EstadoCuentaCacheTest` fallaba con NPE | El usuario de prueba no tenía `id`, y el registro `EstadoCuenta` exige un entero. | Se asigna el id en la preparación de la prueba. |
| `SeguridadRolesIT`: 1 de 70 en rojo | MockMvc `.param()` no llena `getQueryString()`, y el controlador valida la cadena de consulta cruda. | Se usa `.queryParam(...)`. |
| Prueba de integración contra la migración V5 | Los triggers de solo inserción de la tabla de auditoría exigen `--log-bin-trust-function-creators=1` en MySQL. | Incluido en `docker-compose.yml`. |

## 3. Estado de la verificación

Verificado en la máquina de desarrollo: 138 pruebas unitarias, 70 de integración (migraciones V1 a V6
sobre MySQL 8.4 con Testcontainers), stack Docker con Orthanc, Newman Sprint 3 (67/67) y Sprint 1
(10/10).

No verificado todavía: el frontend contra el backend real, los scripts de mínimo privilegio, las
pruebas de carga con k6, HTTPS con Caddy, y el tope absoluto de sesión de 8 horas y el límite de
intentos por IP en el sistema real (hoy solo los cubren las pruebas de integración).
