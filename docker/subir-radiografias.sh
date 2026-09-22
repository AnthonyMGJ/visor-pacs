#!/usr/bin/env bash
# Sube los .dcm de una carpeta a Orthanc. Uso:  ./docker/subir-radiografias.sh [carpeta]
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
CARPETA="${1:-$DIR/sample-dicom}"
URL="${ORTHANC_URL:-http://localhost:8042}"
set -a; source "$DIR/../.env"; set +a
find "$CARPETA" -name '*.dcm' | while read -r f; do
  curl -sS -u "$ORTHANC_USER:$ORTHANC_PASSWORD" -H 'Content-Type: application/dicom' \
       --data-binary @"$f" "$URL/instances" && echo "  <- $(basename "$f")"
done
