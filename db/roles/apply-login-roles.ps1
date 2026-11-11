# Plan A step 2: wrapper that executes 002_login_roles.sql.
# NOTE: keep this file ASCII-only; Windows PowerShell 5.1 reads BOM-less files as ANSI.
#
# Passwords come from process environment variables (never from CLI args, so they do not
# leak into shell history or process command lines):
#   FL_LOGIN_HUB_PASSWORD / FL_LOGIN_COLLAB_PASSWORD / FL_LOGIN_LEGAL_PASSWORD
#   FL_LOGIN_TALENT_PASSWORD / FL_LOGIN_FINANCE_PASSWORD / FL_LOGIN_CRM_PASSWORD
#   FL_LOGIN_DEVOPS_PASSWORD / FL_LOGIN_INTELLIGENCE_PASSWORD / FL_LOGIN_SERVICE_PASSWORD
#
# Usage (PowerShell):
#   $env:Path = "E:\DockerDesktop\app\resources\bin;" + $env:Path
#   $env:FL_LOGIN_HUB_PASSWORD = "<local random value>"
#   ... same for the other 8 ...
#   .\apply-login-roles.ps1
#
# Re-runnable: existing roles are only corrected (attributes + password rotation), never dropped.
# Production: DBA exports the same env vars on the psql host and runs
#   psql -U postgres -d postgres -v ON_ERROR_STOP=1 -f 002_login_roles.sql
# Passwords are injected by the secret manager; never commit them to the repo, compose files
# or Flyway migrations.

[CmdletBinding()]
param(
    [string]$Container = 'fenglin-postgres',
    [string]$PgUser = 'fenglin',
    [string]$Database = 'postgres'
)

$ErrorActionPreference = 'Stop'

$required = @(
    'FL_LOGIN_HUB_PASSWORD',
    'FL_LOGIN_COLLAB_PASSWORD',
    'FL_LOGIN_LEGAL_PASSWORD',
    'FL_LOGIN_TALENT_PASSWORD',
    'FL_LOGIN_FINANCE_PASSWORD',
    'FL_LOGIN_CRM_PASSWORD',
    'FL_LOGIN_DEVOPS_PASSWORD',
    'FL_LOGIN_INTELLIGENCE_PASSWORD',
    'FL_LOGIN_SERVICE_PASSWORD'
)

$missing = $required | Where-Object { [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($_, 'Process')) }
if ($missing.Count -gt 0) {
    Write-Error ("Missing environment variables: " + ($missing -join ', '))
    exit 1
}

$sqlFile = Join-Path $PSScriptRoot '002_login_roles.sql'
if (-not (Test-Path $sqlFile)) {
    Write-Error "SQL file not found: $sqlFile"
    exit 1
}

$sql = [IO.File]::ReadAllText($sqlFile, [Text.Encoding]::UTF8)
$envArgs = @()
foreach ($name in $required) {
    $envArgs += @('-e', ("{0}={1}" -f $name, [Environment]::GetEnvironmentVariable($name, 'Process')))
}

# psql writes NOTICE/WARNING to stderr; PowerShell would treat that as a terminating error
# under ErrorActionPreference=Stop, so stream both handles and rely on the exit code.
$ErrorActionPreference = 'Continue'
$sql | & docker exec -i @envArgs $Container psql -U $PgUser -d $Database -v ON_ERROR_STOP=1 -f - 2>&1 | ForEach-Object { Write-Host $_ }
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    Write-Error "002_login_roles.sql failed with exit code $exitCode"
    exit $exitCode
}

Write-Host "Login roles created/corrected (password = value from this run's environment)"
