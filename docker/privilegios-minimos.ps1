# Crea (o actualiza) la cuenta de MySQL 'visor_app' con los permisos mínimos para la aplicación.
# Ver docker/privilegios-minimos.sh para el detalle. NO HA SIDO PROBADO en MySQL 8.4 real.
# Uso (desde la raíz del repo):  .\docker\privilegios-minimos.ps1
param([string]$Base = "visor_pacs")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$clave = $env:APP_DB_PASSWORD
if (-not $clave) {
    $s = Read-Host "Clave para la cuenta visor_app (12+ caracteres)" -AsSecureString
    $clave = [System.Net.NetworkCredential]::new('', $s).Password
}
if ($clave -notmatch '^[A-Za-z0-9._@#%+=-]{12,}$') {
    Write-Error "La clave debe tener 12+ caracteres y solo letras, números y . _ @ # % + = -"
}

function Invoke-MySqlRoot([string]$sql) {
    $sql | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --skip-column-names'
}

$tablas = Invoke-MySqlRoot "SELECT table_name FROM information_schema.tables WHERE table_schema='$Base' AND table_type='BASE TABLE';" |
    Where-Object { $_ -and $_.Trim() }
if (-not $tablas) { Write-Error "No hay tablas en '$Base': arranque primero el backend para que Flyway cree el esquema." }

$sql = New-Object System.Collections.Generic.List[string]
$sql.Add("CREATE USER IF NOT EXISTS 'visor_app'@'%' IDENTIFIED BY '$clave';")
$sql.Add("ALTER USER 'visor_app'@'%' IDENTIFIED BY '$clave';")
$sql.Add("REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'visor_app'@'%';")
foreach ($t in $tablas) {
    $t = $t.Trim()
    if ($t -eq 'flyway_schema_history') { continue }
    if ($t -eq 'evento_auditoria') {
        $sql.Add("GRANT SELECT, INSERT ON ``$Base``.``$t`` TO 'visor_app'@'%';")
    } else {
        $sql.Add("GRANT SELECT, INSERT, UPDATE, DELETE ON ``$Base``.``$t`` TO 'visor_app'@'%';")
    }
}
$sql.Add("FLUSH PRIVILEGES;")
$sql.Add("SHOW GRANTS FOR 'visor_app'@'%';")
Invoke-MySqlRoot ($sql -join "`n")

Write-Host "Listo. Arranque con:  docker compose -f docker-compose.yml -f docker-compose.privilegios.yml up -d"
Write-Host "(defina antes `$env:APP_DB_PASSWORD o agréguela al .env)"
