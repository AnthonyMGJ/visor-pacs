# Contrato de la API (backend Sprints 1–3)

Documentación navegable y siempre al día: `http://localhost:8080/swagger-ui.html` (JSON en `/v3/api-docs`).
Se apaga con `OPENAPI_ENABLED=false`. Este documento resume lo que necesita el frontend.

## Convenciones

- Autenticación: `Authorization: Bearer <accessToken>` en todo salvo `POST /api/auth/login`, `/actuator/health` y la documentación.
- Roles: `MEDICO`, `PACIENTE`, `ADMIN`. El ADMIN gestiona cuentas, permisos y auditoría; **nunca ve imágenes**.
- Errores: siempre `application/problem+json` con `type`, `title`, `status`, `detail`, `instance`.
  Los de validación (400) añaden `errores: [{ campo, mensaje }]` (nunca el valor enviado).
- Listados paginados: `{ contenido: [...], pagina, tamano, total, totalPaginas }`. Parámetros `pagina` (desde 0) y `tamano` (1–100, por defecto 20).
- Fechas: instantes ISO-8601 en UTC (`2026-10-17T05:00:00Z`).

| Estado | Cuándo |
|---|---|
| 400 | Datos inválidos, JSON mal formado, parámetro de tipo incorrecto, ruta DICOMweb no permitida |
| 401 | Sin token, token inválido/vencido, **sesión revocada** (cuenta desactivada o sesión cerrada), credenciales incorrectas |
| 403 | Rol insuficiente, **clave temporal pendiente de cambio (`codigo: CLAVE_TEMPORAL`)**, médico sin permiso `ACCESO_ESTUDIOS`, paciente pidiendo un estudio ajeno |
| 404 | Recurso inexistente |
| 405 | Método no permitido (p. ej. escribir en la auditoría) |
| 409 | Cuenta duplicada u operación no permitida (p. ej. desactivarse a sí mismo) |
| 429 | Demasiados intentos de inicio de sesión desde la misma IP; cabecera `Retry-After` (segundos) |
| 423 | Cuenta bloqueada temporalmente (5 intentos fallidos, 15 min) |
| 502 | Orthanc no disponible |

## Sesión (HU01, HU12)

| Método y ruta | Rol | Descripción |
|---|---|---|
| `POST /api/auth/login` `{username,password}` | público | `200 {accessToken, tokenType:"Bearer", expiresIn:900, rol, nombre, debeCambiarClave}`. `429` si la IP agotó su cupo |
| `POST /api/auth/renovar` | cualquiera | `200` con un token nuevo (misma forma que login). **`401` si la sesión superó el tope absoluto** (8 h desde el inicio de sesión con clave): hay que volver a escribir la clave |
| `POST /api/auth/cambiar-clave` `{claveActual,claveNueva}` | cualquiera | `200` con token nuevo. Clave de 12+ caracteres y distinta de la actual. Clave actual incorrecta → **`400`** (no 401) y cuenta como intento fallido (puede acabar en `423`). Cierra las demás sesiones |
| `POST /api/auth/logout` | cualquiera | `204`. **Revoca todos los tokens de la cuenta** |
| `POST /api/auth/logout?soloEsta=true` | cualquiera | `204`. Revoca **solo el token presentado**; los demás dispositivos siguen |
| `GET /api/auth/me` | cualquiera | `{username, nombre, rol, permisos[]}` |

**Clave temporal.** Si un ADMIN restablece la clave, el usuario entra con la temporal y el login devuelve `debeCambiarClave: true`. Con ese token solo se puede llamar a `cambiar-clave`, `logout` y `me`; todo lo demás responde `403` con `codigo: "CLAVE_TEMPORAL"`. El frontend debe llevar a la pantalla de cambio de clave.

**Qué debe hacer el frontend**

1. Cualquier `401` (salvo en `/login`) = volver a la pantalla de inicio de sesión. Ahora un `401` también significa «tu cuenta fue desactivada o cerraste sesión en otro lugar».
2. Un `403` **no** cierra la sesión (ya es así en `api.ts`).
3. Inactividad (CA12.1): el token dura 15 min (`expiresIn`). Mientras el usuario esté activo, llamar a `POST /api/auth/renovar` antes de que venza (p. ej. a los ~10 min si hubo actividad) y reemplazar el token. Si no hubo actividad, no renovar: al vencer, el siguiente `401` lo devuelve al login. El servidor nunca renueva un token ya vencido.
4. Al cerrar sesión, llamar a `POST /api/auth/logout` y luego descartar el token (si el logout falla por red, descartar igual).
5. Rutas del frontend por rol: `ADMIN` → panel de administración; `MEDICO` → búsqueda y visor; `PACIENTE` → «Mis estudios».

Claims del JWT (informativos; el servidor no se fía de ellos para el estado de la cuenta): `sub` usuario, `roles`, `nombre`, `uid`, `ver`, `jti` (id del token), `ses` (inicio de sesión con clave; se conserva al renovar y fija el tope de 8 h), `cc` (clave temporal), `patientId` (solo PACIENTE).

## Imágenes: proxy DICOMweb (HU02, HU05, HU06, HU09)

`GET /api/dicomweb/{ruta}` para `MEDICO` y `PACIENTE`. Rutas permitidas: `/studies`, `/studies/{uid}/series`, `/studies/{uid}/series/{s}/metadata`, `/instances`, `/frames/{n}`…; todo lo que no sea lectura DICOMweb da 400.

- **MEDICO** necesita el permiso `ACCESO_ESTUDIOS` (403 si se lo quitaron; también deja de poder iniciar sesión). Ve todos los estudios. Filtros QIDO-RS estándar: `PatientID`, `StudyDate` (`20260101-20260131`), `ModalitiesInStudy` (el proxy los reenvía tal cual; **no verificado** contra tu versión de Orthanc, confírmalo al construir la búsqueda HU05).
- **PACIENTE**: el listado siempre se limita a su `PatientID` (aunque envíe otro). `/studies/{uid}/…` solo si el estudio es suyo; `/series` e `/instances` sueltos dan 403.
- La respuesta lleva `Cache-Control: no-store`.
- La apertura de un estudio queda en la auditoría (una vez por usuario y estudio cada 15 min).

## Administración (HU08) — solo `ADMIN`

| Método y ruta | Descripción |
|---|---|
| `GET /api/admin/usuarios?q=&rol=&activo=&pagina=&tamano=` | Lista cuentas. `q` busca en usuario, nombres, apellidos y correo |
| `GET /api/admin/usuarios/{id}` | Detalle |
| `POST /api/admin/usuarios` `{username,clave,nombres,apellidos,email,rol,dicomPacienteId?}` | `201`. `rol`: `MEDICO` o `PACIENTE` (el ADMIN solo se crea por consola). Clave de 12+ caracteres. Los médicos nacen con `ACCESO_ESTUDIOS` |
| `PUT /api/admin/usuarios/{id}` `{nombres,apellidos,email}` | Edita datos |
| `PATCH /api/admin/usuarios/{id}/estado` `{activo}` | Desactivar revoca la sesión abierta en su siguiente solicitud (401). Reactivar limpia bloqueos |
| `POST /api/admin/usuarios/{id}/restablecer-clave` | `200 {claveTemporal}` (se muestra **una sola vez**). Obliga a cambiarla al entrar, limpia bloqueos y cierra sus sesiones. No sobre uno mismo (`409`) |
| `GET /api/admin/permisos` | Catálogo: `[{codigo, descripcion}]` |
| `PUT /api/admin/usuarios/{id}/permisos/{codigo}` | Asigna (idempotente; solo médicos) |
| `DELETE /api/admin/usuarios/{id}/permisos/{codigo}` | Revoca (idempotente; solo médicos) |

Forma de una cuenta: `{id, username, nombres, apellidos, email, rol, activo, bloqueado, dicomPacienteId, permisos[]}`. Nunca incluye la clave.

## Auditoría (HU10) — solo `ADMIN`, solo lectura

`GET /api/admin/auditoria?usuario=&tipo=&resultado=&estudio=&desde=&hasta=&pagina=&tamano=`, del más reciente al más antiguo.

- `tipo`: `INICIO_SESION`, `CIERRE_SESION`, `APERTURA_ESTUDIO`, `ACCESO_ESTUDIO_DENEGADO`, `USUARIO_CREADO`, `USUARIO_EDITADO`, `USUARIO_ACTIVADO`, `USUARIO_DESACTIVADO`, `CLAVE_CAMBIADA`, `CLAVE_RESTABLECIDA`, `PERMISO_OTORGADO`, `PERMISO_REVOCADO`.
- `resultado`: `EXITO` o `FALLO`.
- Evento: `{id, fecha, tipo, resultado, idUsuario, username, rol, estudio, ip, detalle}`.
- No existe ninguna operación para modificar o borrar eventos; además la base de datos lo impide con triggers.

## Salud

`GET /actuator/health` → `{"status":"UP"}` (público). Incluye base de datos y Orthanc: si Orthanc cae, responde `503`/`DOWN`.
