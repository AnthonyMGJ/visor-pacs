#!/usr/bin/env bash
# Resume el CSV de vigilar.sh: mediciones, caídas, % de disponibilidad y latencia media/máxima.
# Uso:  ./docker/monitoreo/resumen.sh [ARCHIVO_CSV]
CSV="${1:-docs/disponibilidad/disponibilidad.csv}"
[ -f "$CSV" ] || { echo "No existe $CSV"; exit 1; }
awk -F, 'NR>1 { n++; if ($2=="UP") up++; s+=$4; if ($4>m) m=$4; if (!p) p=$1; u=$1 }
  END { if (!n) { print "Sin datos"; exit }
        printf "Periodo: %s -> %s\nMediciones: %d\nCaidas: %d\nDisponibilidad: %.2f %%\nLatencia media: %.0f ms (max %d ms)\n",
               p, u, n, n-up, 100*up/n, s/n, m }' "$CSV"
