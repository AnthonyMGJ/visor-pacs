# Registro de disponibilidad (PowerShell). Consulta /actuator/health cada N segundos y añade una línea a un CSV.
# Uso:  .\docker\monitoreo\vigilar.ps1 -Url http://localhost:8080/actuator/health -Segundos 60
# -Segundos 0 hace una sola medición. Resumen:  .\docker\monitoreo\resumen.ps1
param(
    [string]$Url = "http://localhost:8080/actuator/health",
    [int]$Segundos = 60,
    [string]$Csv = "docs/disponibilidad/disponibilidad.csv"
)
New-Item -ItemType Directory -Force -Path (Split-Path $Csv) | Out-Null
if (-not (Test-Path $Csv)) { "fecha_utc,estado,http,ms" | Out-File $Csv -Encoding utf8 }
do {
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    try { $codigo = (Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10).StatusCode }
    catch { $codigo = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 } }
    $reloj.Stop()
    $estado = if ($codigo -eq 200) { "UP" } else { "DOWN" }
    "{0},{1},{2},{3}" -f (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ"), $estado, $codigo, $reloj.ElapsedMilliseconds |
        Out-File $Csv -Append -Encoding utf8
    if ($Segundos -gt 0) { Start-Sleep -Seconds $Segundos }
} while ($Segundos -gt 0)
