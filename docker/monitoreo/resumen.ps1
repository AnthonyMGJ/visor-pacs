# Resume el CSV de vigilar.ps1: mediciones, caídas, % de disponibilidad y latencia.
param([string]$Csv = "docs/disponibilidad/disponibilidad.csv")
$d = Import-Csv $Csv
if (-not $d) { Write-Host "Sin datos"; exit }
$up = ($d | Where-Object { $_.estado -eq "UP" }).Count
$ms = $d | ForEach-Object { [int]$_.ms }
"Periodo: {0} -> {1}" -f $d[0].fecha_utc, $d[-1].fecha_utc
"Mediciones: {0}" -f $d.Count
"Caidas: {0}" -f ($d.Count - $up)
"Disponibilidad: {0:N2} %" -f (100 * $up / $d.Count)
"Latencia media: {0:N0} ms (max {1} ms)" -f ($ms | Measure-Object -Average).Average, ($ms | Measure-Object -Maximum).Maximum
