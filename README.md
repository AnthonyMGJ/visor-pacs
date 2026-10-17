# Visor PACS responsive — backend Sprints 1–3

Objetivo del Sprint 1: *el médico inicia sesión de forma segura y visualiza una radiografía desde su celular.*
Este repositorio contiene el **backend e infraestructura** de los Sprints 1–3: login, proxy DICOMweb, administración de cuentas y permisos (HU08), estudios del paciente (HU09), registro de accesos (HU10), sesión segura (HU12), HTTPS y monitoreo (HT02). El frontend (React + Vite) está en `frontend/`. Contrato para el frontend: [`docs/api/CONTRATO_API.md`](docs/api/CONTRATO_API.md) · API navegable en `/swagger-ui.html`.

## Arquitectura
React (Vite) → **Spring Boot** (JWT) → **Orthanc** (DICOMweb) y **MySQL**. El navegador nunca habla con Orthanc: solo con el backend.

| Carpeta | Contenido |
|---|---|
| `backend/` | Spring Boot 3.5 (Java 21): JWT, roles MEDICO/PACIENTE/ADMIN, permisos, auditoría, proxy DICOMweb, Flyway, OpenAPI |
| `docker/` | Scripts para cargar radiografías y radiografías sintéticas de prueba |
| `docs/postman/` | Colecciones de Postman: Sprint 1 (login y proxy) y Sprint 3 (ADMIN, permisos, sesión, auditoría) |
| `docs/api/`, `docs/despliegue/` | Contrato de la API y procedimiento de despliegue con HTTPS |
| `pruebas/carga/` | Guiones k6 (login, listado y primera imagen con 180 cortes) |
| `tools/` | Generadores de DICOM sintéticos (radiografías y serie de TC de 180 cortes) |

## Requisitos
Docker Desktop, Git, y (para correr pruebas o desarrollar fuera de Docker) JDK 21 y Maven 3.9+.

## Puesta en marcha
1. Copia `.env.example` como `.env` y **cambia todos los valores** (`JWT_SECRET`: `openssl rand -base64 48`).
2. Levanta todo: `docker compose up -d --build`
3. Revisa: `docker compose ps` y `docker compose logs -f backend` (debe decir que creó el usuario inicial).
4. Sube las radiografías de prueba: `./docker/subir-radiografias.ps1` (Windows) o `./docker/subir-radiografias.sh`.
5. Importa `docs/postman/visor-pacs-sprint1.postman_collection.json` (y, con un ADMIN creado, `visor-pacs-sprint3.postman_collection.json`), pon las claves en las variables y ejecuta las peticiones en orden.

Resultados esperados: 01 → 200 · 02 → 200 · 03 → 401 · 04 → 200 (lista los 3 estudios) · 05 → 401 · 06 → 400 · 07 → 204.

Desarrollo desde el IDE: `docker compose up -d mysql orthanc` y ejecutar `VisorPacsApplication` con directorio de trabajo `backend/`
(lee el `.env` de la raíz automáticamente).

## Pruebas
- `cd backend && mvn -B test` — pruebas unitarias (JUnit 5 + Mockito); no requieren base de datos ni Docker.
- `cd backend && mvn -B verify` — además, pruebas de integración `*IT` con MySQL 8.4 real (Testcontainers, **requiere Docker abierto**) y un Orthanc simulado.
- Postman/Newman: ver `docs/postman/`. Carga: `pruebas/carga/` (k6). Disponibilidad: `docker/monitoreo/`.

## Flujo de ramas
`main` (estable, una etiqueta por sprint) ← `develop` (integración) ← `feature/SCRUM-<n>-<tema>`. Commits: `feat(HU01): ...`.
Todo cambio entra por Pull Request con la plantilla del DoD y revisión de otro integrante.

## Seguridad
- Contraseñas con BCrypt; mensaje genérico ante credenciales incorrectas; bloqueo temporal tras 5 intentos fallidos.
- JWT de 15 minutos; API sin sesión. Tres roles: `MEDICO` (ve todos los estudios, con el permiso `ACCESO_ESTUDIOS`), `PACIENTE` (el proxy DICOMweb solo le deja ver estudios con su PatientID) y `ADMIN` (gestiona cuentas, permisos y auditoría; **nunca ve imágenes**). Lo no listado en `SecurityConfig` se deniega por defecto.
- Revocación inmediata: cada petición valida que la cuenta siga activa y que la versión de sesión del token sea la vigente; desactivar una cuenta o cerrar sesión surte efecto en la siguiente solicitud.
- Auditoría de solo inserción (tabla con triggers que rechazan UPDATE/DELETE): inicios de sesión (exitosos y fallidos), aperturas de estudio con IP, y acciones del administrador.
- Errores uniformes en `application/problem+json` (incluidos 401/403).
- Orthanc solo en la red interna (su puerto queda ligado a `127.0.0.1`), con usuario propio del backend.
- Respuestas de imágenes con `Cache-Control: no-store`. Secretos solo en `.env` (ignorado por Git).
- Solo datos sintéticos en desarrollo.

Pendiente / no incluido: segundo factor y recuperación de contraseña por correo (requiere SMTP; hoy la restablece un ADMIN con clave temporal), rotación del token anterior al renovar y revocación compartida entre varias instancias. Los permisos mínimos de base de datos son opt-in (ver `docs/despliegue/DESPLIEGUE.md` §9).

## Crear usuarios (médicos, pacientes y administradores)

Con `docker compose up -d` corriendo, desde `backend/`:

```bash
bash docker/crear-usuario.sh MEDICO   ana.perez "Ana" "Pérez" ana@clinica.pe
bash docker/crear-usuario.sh PACIENTE juan.diaz "Juan" "Díaz" juan@correo.pe PRUEBA-0001
bash docker/crear-usuario.sh ADMIN    admin.ti "Admin" "TI" admin@clinica.pe
```

Para desarrollo, `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` en `.env` crean un administrador al arrancar. Una vez que existe un ADMIN, médicos y pacientes se crean desde el panel (`POST /api/admin/usuarios`); el ADMIN solo se crea por consola.
**Si ya tenías MySQL corriendo:** recrea el contenedor (`docker compose up -d`) para que tome `--log-bin-trust-function-creators=1`, necesario para los triggers de la auditoría; tus datos se conservan.

En Windows: `.\docker\crear-usuario.ps1 -Rol MEDICO -Usuario ana.perez -Nombres "Ana" -Apellidos "Pérez" -Email ana@clinica.pe`.
La clave se pide por teclado (mínimo 12 caracteres). Para un paciente, el último dato es el `PatientID` de sus estudios en Orthanc: debe coincidir exactamente.
Toda la lógica vive en `UsuarioService`; la consola, el arranque de desarrollo y el módulo de administración la comparten.
