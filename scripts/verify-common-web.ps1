# Verify that every fat jar embeds the current fenglin-common-web artifact.
# Exit 0 only when all servlet services embed the exact SHA256 found in ~/.m2.
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-common-web.ps1
#   powershell ... -File scripts/verify-common-web.ps1 -Root e:\fenglin-cloud-tech
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$M2Jar = "$env:USERPROFILE\.m2\repository\com\fenglin\fenglin-common-web\0.1.0-SNAPSHOT\fenglin-common-web-0.1.0-SNAPSHOT.jar",
    [string]$Version = '0.1.0-SNAPSHOT'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $M2Jar)) {
    Write-Host "[FAIL] m2 artifact not found: $M2Jar"
    Write-Host "       Run: mvn -pl fenglin-common/fenglin-common-web -am install -DskipTests"
    exit 2
}

$m2Hash = (Get-FileHash $M2Jar -Algorithm SHA256).Hash
$m2Size = (Get-Item $M2Jar).Length
Write-Host "m2 artifact : $M2Jar"
Write-Host "m2 size/hash: $m2Size / $m2Hash"
Write-Host ""

$platform = Join-Path $Root 'backend\platform'
$tmpDir = Join-Path $env:TEMP ('verify-common-web-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$rows = @()
$failures = 0
$index = 0

$serviceDirs = Get-ChildItem $platform -Directory |
               Where-Object { $_.Name -eq 'fenglin-gateway' -or $_.Name -like '*-service' } |
               Sort-Object Name
foreach ($dir in $serviceDirs) {
    $index++
    $name = $dir.Name -replace '^fenglin-', '' -replace '-service$', ''
    $target = Join-Path $dir.FullName 'target'
    $jar = Get-ChildItem $target -Filter "*-$Version.jar" -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } |
           Select-Object -First 1

    $size = '-'
    $hash = '-'
    $status = 'MISSING'
    $detail = ''

    if (-not $jar) {
        $detail = 'fat jar not found'
        $failures++
    } else {
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            $entry = $zip.Entries |
                     Where-Object { $_.FullName -like 'BOOT-INF/lib/fenglin-common-web*.jar' } |
                     Select-Object -First 1
            if (-not $entry) {
                if ($name -eq 'gateway') {
                    $status = 'NO_DEP'
                    $detail = 'gateway does not depend on common-web'
                } else {
                    $status = 'NO_DEP'
                    $detail = 'common-web missing from fat jar'
                    $failures++
                }
            } else {
                $size = $entry.Length
                $extract = Join-Path $tmpDir ("$name-common-web.jar")
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $extract, $true)
                $hash = (Get-FileHash $extract -Algorithm SHA256).Hash
                if ($hash -eq $m2Hash) {
                    $status = 'CURRENT'
                } else {
                    $status = 'STALE'
                    $detail = 'hash differs from m2'
                    $failures++
                }
            }
            $zip.Dispose()
        } catch {
            $status = 'ERROR'
            $detail = $_.Exception.Message
            $failures++
        }
    }

    $rows += [pscustomobject]@{
        No = $index
        Service = $name
        Size = $size
        Verdict = $status
        Sha256 = $hash
        Detail = $detail
    }
}

Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue

$rows | Format-Table -AutoSize No, Service, Size, Verdict, Sha256, Detail

Write-Host ""
if ($failures -gt 0) {
    Write-Host "[FAIL] $failures service(s) do not embed the current common-web artifact."
    Write-Host "       Rebuild them after installing the shared module, then rerun this check."
    exit 1
}
Write-Host "[PASS] all servlet services embed the current common-web artifact."
exit 0
