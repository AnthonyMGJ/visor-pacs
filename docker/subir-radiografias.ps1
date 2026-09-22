# Sube los .dcm de una carpeta a Orthanc (PowerShell). Uso:  ./docker/subir-radiografias.ps1
param(
    [string]$Carpeta = (Join-Path $PSScriptRoot "sample-dicom"),
    [string]$Url = "http://localhost:8042"
)
$vars = @{}
Get-Content (Join-Path $PSScriptRoot "../.env") | Where-Object { $_ -match '^\s*[^#\s].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $vars[$k.Trim()] = $v.Trim()
}
$cred = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$($vars['ORTHANC_USER']):$($vars['ORTHANC_PASSWORD'])"))
$headers = @{ Authorization = "Basic $cred" }

Get-ChildItem -Path $Carpeta -Filter *.dcm -Recurse | ForEach-Object {
    $intentos = 0
    $ok = $false
    while (-not $ok -and $intentos -lt 3) {
        $intentos++
        try {
            # -DisableKeepAlive: pide una conexión nueva en cada archivo, en vez de reutilizar
            # una que el servidor ya pudo haber cerrado (causa del error intermitente).
            $r = Invoke-RestMethod -Uri "$Url/instances" -Method Post -InFile $_.FullName `
                 -ContentType "application/dicom" -Headers $headers -DisableKeepAlive
            Write-Host "OK    $($_.Name) -> $($r.Status)"
            $ok = $true
        } catch {
            if ($intentos -lt 3) {
                Start-Sleep -Milliseconds 500
            } else {
                Write-Host "ERROR $($_.Name) (tras $intentos intentos): $($_.Exception.Message)" -ForegroundColor Red
            }
        }
    }
}