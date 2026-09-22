# Visor PACS responsive — Sprint 1 (backend + infraestructura)

Objetivo del Sprint 1: *el médico inicia sesión de forma segura y visualiza una radiografía desde su celular.*
Este repositorio contiene la parte de **backend e infraestructura** (HT01, HT03, HT04 y HU01). El frontend Angular se agrega en `frontend/`.

## Arquitectura
Angular → **Spring Boot** (JWT) → **Orthanc** (DICOMweb) y **MySQL**. El navegador nunca habla con Orthanc: solo con el backend.

| Carpeta | Contenido |
|---|---|
| `backend/` | Spring Boot 3.5 (Java 21): login JWT, bloqueo por intentos, proxy DICOMweb, Flyway |
| `docker/` | Scripts para cargar radiografías y radiografías sintéticas de prueba |
| `docs/postman/` | Colección de Postman para verificar el Sprint 1 |
| `tools/` | Generador de radiografías DICOM sintéticas |

## Requisitos
Docker Desktop, Git, y (para correr pruebas o desarrollar fuera de Docker) JDK 21 y Maven 3.9+.

## Puesta en marcha
1. Copia `.env.example` como `.env` y **cambia todos los valores** (`JWT_SECRET`: `openssl rand -base64 48`).
2. Levanta todo: `docker compose up -d --build`
3. Revisa: `docker compose ps` y `docker compose logs -f backend` (debe decir que creó el usuario inicial).
4. Sube las radiografías de prueba: `./docker/subir-radiografias.ps1` (Windows) o `./docker/subir-radiografias.sh`.
5. Importa `docs/postman/visor-pacs-sprint1.postman_collection.json`, pon tu clave en la variable `password` y ejecuta las 7 peticiones en orden.

Resultados esperados: 01 → 200 · 02 → 200 · 03 → 401 · 04 → 200 (lista los 3 estudios) · 05 → 401 · 06 → 400 · 07 → 204.

Desarrollo desde el IDE: `docker compose up -d mysql orthanc` y ejecutar `VisorPacsApplication` con directorio de trabajo `backend/`
(lee el `.env` de la raíz automáticamente).

## Pruebas
`cd backend && mvn test` — unitarias de login/bloqueo, emisión de JWT y validación de rutas DICOMweb (no requieren base de datos).

## Flujo de ramas
`main` (estable, una etiqueta por sprint) ← `develop` (integración) ← `feature/SCRUM-<n>-<tema>`. Commits: `feat(HU01): ...`.
Todo cambio entra por Pull Request con la plantilla del DoD y revisión de otro integrante.

## Seguridad (lo que ya cubre el Sprint 1)
- Contraseñas con BCrypt; mensaje genérico ante credenciales incorrectas; bloqueo temporal tras 5 intentos fallidos.
- JWT de 15 minutos; API sin sesión; toda ruta `/api/**` exige rol `MEDICO`; el resto se deniega por defecto.
- Orthanc solo en la red interna (su puerto queda ligado a `127.0.0.1`), con usuario propio del backend.
- Respuestas de imágenes con `Cache-Control: no-store`. Secretos solo en `.env` (ignorado por Git).
- Solo datos sintéticos en desarrollo.

Pendiente en próximos sprints: cierre por inactividad y revocación (HU08), registro de accesos (HU10), segundo factor (HU09),
límite de peticiones por IP, HTTPS con reverse proxy y despliegue. Nota: el bloqueo por intentos puede usarse para bloquear a propósito
la cuenta de un médico; el límite por IP (Sprint 3) lo mitiga.
