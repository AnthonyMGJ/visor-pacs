# Crea un médico o un paciente (la clave se pide por teclado y se cifra con BCrypt).
# Uso (desde backend/, con `docker compose up -d` ya corriendo):
#   .\docker\crear-usuario.ps1 -Rol MEDICO -Usuario ana.perez -Nombres "Ana" -Apellidos "Pérez" -Email ana@clinica.pe
#   .\docker\crear-usuario.ps1 -Rol ADMIN -Usuario admin.ti -Nombres "Admin" -Apellidos "TI" -Email admin@clinica.pe
#   .\docker\crear-usuario.ps1 -Rol PACIENTE -Usuario juan.diaz -Nombres "Juan" -Apellidos "Díaz" -Email juan@correo.pe -DicomId PRUEBA-0001
param(
  [Parameter(Mandatory)][ValidateSet('MEDICO','PACIENTE','ADMIN')][string]$Rol,
  [Parameter(Mandatory)][string]$Usuario,
  [Parameter(Mandatory)][string]$Nombres,
  [Parameter(Mandatory)][string]$Apellidos,
  [Parameter(Mandatory)][string]$Email,
  [string]$DicomId = ''
)
$seg = Read-Host "Clave (minimo 12 caracteres)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seg)
$env:NUEVO_USUARIO_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
Set-Location (Join-Path $PSScriptRoot '..')
try {
  docker compose run --rm -e NUEVO_USUARIO_PASSWORD backend `
    "--app.cli.crear-usuario=true" "--server.port=0" "--app.bootstrap.enabled=false" `
    "--app.cli.rol=$Rol" "--app.cli.username=$Usuario" "--app.cli.nombres=$Nombres" `
    "--app.cli.apellidos=$Apellidos" "--app.cli.email=$Email" "--app.cli.dicom-id=$DicomId"
} finally {
  Remove-Item Env:NUEVO_USUARIO_PASSWORD -ErrorAction SilentlyContinue
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
