#!/usr/bin/env bash
# Crea un médico o un paciente (la clave se pide por teclado y se cifra con BCrypt).
# Uso (desde la carpeta backend/, con `docker compose up -d` ya corriendo):
#   bash docker/crear-usuario.sh MEDICO   ana.perez "Ana" "Pérez" ana@clinica.pe
#   bash docker/crear-usuario.sh PACIENTE juan.diaz "Juan" "Díaz" juan@correo.pe PRUEBA-0001
# El último dato (PatientID) debe ser igual al PatientID de sus estudios en Orthanc.
set -euo pipefail
if [ $# -lt 5 ]; then
  echo "Uso: $0 ROL(MEDICO|PACIENTE) usuario nombres apellidos correo [PatientID]"; exit 1
fi
ROL="$(echo "$1" | tr '[:lower:]' '[:upper:]')"; USUARIO="$2"; NOMBRES="$3"; APELLIDOS="$4"; EMAIL="$5"; DICOM="${6:-}"
read -rsp "Clave (mínimo 12 caracteres): " CLAVE; echo
cd "$(cd "$(dirname "$0")/.." && pwd)"
NUEVO_USUARIO_PASSWORD="$CLAVE" docker compose run --rm -e NUEVO_USUARIO_PASSWORD backend \
  --app.cli.crear-usuario=true --server.port=0 --app.bootstrap.enabled=false \
  --app.cli.rol="$ROL" --app.cli.username="$USUARIO" --app.cli.nombres="$NOMBRES" \
  --app.cli.apellidos="$APELLIDOS" --app.cli.email="$EMAIL" --app.cli.dicom-id="$DICOM"
