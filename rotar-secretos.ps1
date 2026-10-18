# rotar-secretos.ps1  -  Visor PACS
# Rota JWT_SECRET, DB_PASSWORD, DB_ROOT_PASSWORD, ORTHANC_PASSWORD, la clave del administrador
# (y APP_DB_PASSWORD si esta activa en el .env). Compatible con Windows PowerShell 5.1.
#
# Uso (desde la carpeta que tiene docker-compose.yml y .env, con el stack encendido):
#     powershell -ExecutionPolicy Bypass -File .\rotar-secretos.ps1
#     (con permisos minimos:  ... -File .\rotar-secretos.ps1 -ConPrivilegios)
#
# Nunca imprime los secretos. Los valores nuevos quedan solo en el .env.
param([switch]$ConPrivilegios)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'   # docker escribe progreso en stderr; los errores se detectan con $LASTEXITCODE

function Falla([string]$m) { Write-Host "ERROR: $m" -ForegroundColor Red; exit 1 }
function Paso([string]$m)  { Write-Host "`n== $m" -ForegroundColor Cyan }
function Bien([string]$m)  { Write-Host "OK  $m" -ForegroundColor Green }

# --- Comprobaciones previas -------------------------------------------------------------------------------------
$raiz = (Get-Location).Path
$envRuta = Join-Path $raiz '.env'
if (-not (Test-Path (Join-Path $raiz 'docker-compose.yml'))) { Falla 'Ejecuta el script en la carpeta que tiene docker-compose.yml.' }
if (-not (Test-Path $envRuta)) { Falla 'No existe .env en esta carpeta.' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Falla 'No se encontro docker.' }

$compose = @('compose')
if ($ConPrivilegios) { $compose += @('-f', 'docker-compose.yml', '-f', 'docker-compose.privilegios.yml') }

# --- Utilidades -------------------------------------------------------------------------------------------------
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Leer-Env {
    $h = @{}
    foreach ($linea in [IO.File]::ReadAllLines($envRuta)) {
        if ($linea -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $h[$Matches[1]] = $Matches[2].Trim() }
    }
    return $h
}

function Poner-En-Env([string]$clave, [string]$valor) {
    $texto = [IO.File]::ReadAllText($envRuta)
    $patron = '(?m)^' + [regex]::Escape($clave) + '=[^\r\n]*'
    if (-not [regex]::IsMatch($texto, $patron)) { Falla "$clave no esta en el .env." }
    $nuevo = [regex]::Replace($texto, $patron, { param($m) $clave + '=' + $valor })
    [IO.File]::WriteAllText($envRuta, $nuevo, $utf8)
    if ((Leer-Env)[$clave] -ne $valor) { Falla "No se pudo escribir $clave en el .env." }
}

$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
$alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'   # sin simbolos: seguro en .env, JSON y SQL
function Nueva-Clave([int]$largo) {
    $sb = New-Object System.Text.StringBuilder
    $b = New-Object byte[] 1
    $limite = 256 - (256 % $alfabeto.Length)   # descarta valores que sesgarian el reparto
    while ($sb.Length -lt $largo) {
        $rng.GetBytes($b)
        if ($b[0] -lt $limite) { [void]$sb.Append($alfabeto[$b[0] % $alfabeto.Length]) }
    }
    return $sb.ToString()
}

function Esperar-Backend {
    for ($i = 0; $i -lt 60; $i++) {
        try { $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 5; if ($r.StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Seconds 3
    }
    Falla 'El backend no respondio en 3 minutos. Revisa: docker compose logs backend'
}

function Login([string]$usuario, [string]$clave) {
    $cuerpo = (@{ username = $usuario; password = $clave } | ConvertTo-Json)
    return Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body $cuerpo
}

# --- 0. Lectura y copia de seguridad ----------------------------------------------------------------------------
Paso '0. Lectura del .env y copia de seguridad'
$viejo = Leer-Env
foreach ($k in 'DB_USER', 'DB_PASSWORD', 'DB_ROOT_PASSWORD', 'ORTHANC_PASSWORD', 'JWT_SECRET') {
    if (-not $viejo.ContainsKey($k) -or $viejo[$k] -eq '') { Falla "Falta $k en el .env." }
}
$usarApp = $viejo.ContainsKey('APP_DB_PASSWORD') -and $viejo['APP_DB_PASSWORD'] -ne ''
if ($ConPrivilegios -and -not $usarApp) { Falla 'Usaste -ConPrivilegios pero no hay APP_DB_PASSWORD activa en el .env.' }

# La copia va FUERA del repositorio, para que nunca se suba por accidente.
$copia = Join-Path $env:USERPROFILE ("visor-pacs-env-ANTES-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
Copy-Item $envRuta $copia -ErrorAction Stop
Bien "Copia del .env anterior (con las claves viejas): $copia"

# --- 1. Valores nuevos -------------------------------------------------------------------------------------------
Paso '1. Generando valores nuevos'
$nuevo = @{
    DB_PASSWORD      = Nueva-Clave 28
    DB_ROOT_PASSWORD = Nueva-Clave 28
    ORTHANC_PASSWORD = Nueva-Clave 28
    JWT_SECRET       = Nueva-Clave 64
}
if ($usarApp) { $nuevo['APP_DB_PASSWORD'] = Nueva-Clave 28 }
Bien ("Generados {0} valores aleatorios (no se muestran)." -f $nuevo.Count)

# --- 2. MySQL: cambiar las claves DENTRO de la base (el .env solo no basta) ---------------------------------------
Paso '2. Cambiando las claves dentro de MySQL'
$consultaCuentas = "SELECT CONCAT(user,'@',host) FROM mysql.user WHERE user IN ('root','$($viejo['DB_USER'])'" + $(if ($usarApp) { ",'visor_app'" } else { '' }) + ")"
$cuentas = & docker @compose exec -T -e "MYSQL_PWD=$($viejo['DB_ROOT_PASSWORD'])" mysql mysql -uroot -N -B -e $consultaCuentas
if ($LASTEXITCODE -ne 0) { Falla 'No pude entrar a MySQL con la clave root actual. Nada se modifico.' }

$sql = New-Object System.Text.StringBuilder
foreach ($c in $cuentas) {
    if ($c -notmatch '^(.+)@(.+)$') { continue }
    $u = $Matches[1]; $h = $Matches[2]
    if ($u -eq 'root')                  { $p = $nuevo['DB_ROOT_PASSWORD'] }
    elseif ($u -eq $viejo['DB_USER'])   { $p = $nuevo['DB_PASSWORD'] }
    elseif ($u -eq 'visor_app')         { $p = $nuevo['APP_DB_PASSWORD'] }
    else { continue }
    [void]$sql.AppendLine("ALTER USER '$u'@'$h' IDENTIFIED BY '$p';")
}
[void]$sql.AppendLine('FLUSH PRIVILEGES;')
$sql.ToString() | & docker @compose exec -T -e "MYSQL_PWD=$($viejo['DB_ROOT_PASSWORD'])" mysql mysql -uroot
if ($LASTEXITCODE -ne 0) { Falla 'MySQL rechazo el cambio. El .env sigue intacto.' }

# Desde aqui las claves viejas ya no sirven en MySQL: se guarda el .env de inmediato.
foreach ($k in $nuevo.Keys) { Poner-En-Env $k $nuevo[$k] }
Bien '.env actualizado con las claves nuevas.'

& docker @compose exec -T -e "MYSQL_PWD=$($nuevo['DB_ROOT_PASSWORD'])" mysql mysql -uroot -e 'SELECT 1' | Out-Null
if ($LASTEXITCODE -ne 0) { Falla 'La clave root nueva no funciona.' }
& docker @compose exec -T -e "MYSQL_PWD=$($nuevo['DB_PASSWORD'])" mysql mysql "-u$($viejo['DB_USER'])" -e 'SELECT 1' | Out-Null
if ($LASTEXITCODE -ne 0) { Falla 'La clave nueva de la cuenta de la aplicacion no funciona.' }
Bien 'MySQL acepta las claves nuevas (root y cuenta de la aplicacion).'

# --- 3. Reiniciar (Orthanc y backend toman las claves nuevas al recrearse) ---------------------------------------
Paso '3. Recreando los contenedores (los datos de los volumenes se conservan)'
& docker @compose up -d --force-recreate
if ($LASTEXITCODE -ne 0) { Falla 'docker compose up fallo.' }
Esperar-Backend
Bien 'Backend arriba.'

# --- 4. Verificacion de extremo a extremo --------------------------------------------------------------------------
Paso '4. Verificando: login medico + lista de estudios (prueba base de datos, JWT nuevo y Orthanc)'
$env2 = Leer-Env
if (-not $env2.ContainsKey('BOOTSTRAP_USERNAME') -or -not $env2.ContainsKey('BOOTSTRAP_PASSWORD')) { Falla 'Faltan BOOTSTRAP_USERNAME/BOOTSTRAP_PASSWORD en el .env.' }
try { $t = Login $env2['BOOTSTRAP_USERNAME'] $env2['BOOTSTRAP_PASSWORD'] } catch { Falla 'El login del medico fallo (base de datos o JWT). Revisa: docker compose logs backend' }
try {
    $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/dicomweb/studies' -Headers @{ Authorization = "Bearer $($t.accessToken)"; Accept = 'application/dicom+json' }
    if ($r.StatusCode -ne 200) { Falla "Estudios respondio $($r.StatusCode)." }
} catch { Falla 'No se pudo listar estudios (Orthanc con clave nueva?). Revisa: docker compose logs orthanc backend' }
Bien 'Login del medico y lista de estudios funcionan con los secretos nuevos.'

# --- 5. Clave del administrador (se cambia con el flujo normal; el .env solo no la cambia) -----------------------
Paso '5. Clave del administrador'
if ($env2.ContainsKey('BOOTSTRAP_ADMIN_USERNAME') -and $env2['BOOTSTRAP_ADMIN_USERNAME'] -ne '') {
    $adminNueva = Nueva-Clave 28
    $adm = $null
    try { $adm = Login $env2['BOOTSTRAP_ADMIN_USERNAME'] $env2['BOOTSTRAP_ADMIN_PASSWORD'] } catch {
        Write-Host 'AVISO: no pude entrar como admin con la clave del .env (quiza ya se cambio antes). Se omite este paso.' -ForegroundColor Yellow
    }
    if ($adm) {
        $cuerpo = (@{ claveActual = $env2['BOOTSTRAP_ADMIN_PASSWORD']; claveNueva = $adminNueva } | ConvertTo-Json)
        try {
            Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/cambiar-clave' -ContentType 'application/json' `
                -Headers @{ Authorization = "Bearer $($adm.accessToken)" } -Body $cuerpo | Out-Null
        } catch { Falla 'El backend rechazo el cambio de clave del admin. El .env conserva la clave anterior.' }
        Poner-En-Env 'BOOTSTRAP_ADMIN_PASSWORD' $adminNueva
        try { Login $env2['BOOTSTRAP_ADMIN_USERNAME'] $adminNueva | Out-Null } catch { Falla 'La clave nueva del admin no entra.' }
        Bien 'Clave del administrador rotada y verificada.'
    }
} else { Write-Host 'No hay administrador de prueba en el .env; se omite.' }

# --- Cierre --------------------------------------------------------------------------------------------------------
Paso 'Listo'
Write-Host 'Rotados: JWT_SECRET, DB_PASSWORD, DB_ROOT_PASSWORD, ORTHANC_PASSWORD, admin' -NoNewline
if ($usarApp) { Write-Host ', APP_DB_PASSWORD' } else { Write-Host '' }
Write-Host "Las sesiones abiertas se cerraron (JWT nuevo): hay que iniciar sesion otra vez." -ForegroundColor Yellow
Write-Host "Cuando confirmes que todo funciona, BORRA la copia con las claves viejas:" -ForegroundColor Yellow
Write-Host "    Remove-Item '$copia'"
