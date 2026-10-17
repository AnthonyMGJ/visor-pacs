#!/usr/bin/env bash
# Registro de disponibilidad (HT02 / T40): consulta /actuator/health cada N segundos y añade una línea a un CSV.
# Uso:  ./docker/monitoreo/vigilar.sh [URL] [SEGUNDOS] [ARCHIVO_CSV]
#   ./docker/monitoreo/vigilar.sh https://visor.midominio.com/actuator/health 60 docs/disponibilidad/disponibilidad.csv
# Déjalo corriendo con nohup / systemd, o llámalo desde cron con SEGUNDOS=0 (una sola medición).
# Resumen del periodo:  ./docker/monitoreo/resumen.sh docs/disponibilidad/disponibilidad.csv
set -u
URL="${1:-http://localhost:8080/actuator/health}"
INTERVALO="${2:-60}"
CSV="${3:-docs/disponibilidad/disponibilidad.csv}"
mkdir -p "$(dirname "$CSV")"
[ -f "$CSV" ] || echo "fecha_utc,estado,http,ms" > "$CSV"
while true; do
  inicio=$(date +%s%3N)
  codigo=$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$URL" || true)
  codigo="${codigo:-000}"
  ms=$(( $(date +%s%3N) - inicio ))
  if [ "$codigo" = "200" ]; then estado=UP; else estado=DOWN; fi
  echo "$(date -u +%Y-%m-%dT%H:%M:%SZ),$estado,$codigo,$ms" >> "$CSV"
  [ "$INTERVALO" -le 0 ] && break
  sleep "$INTERVALO"
done
