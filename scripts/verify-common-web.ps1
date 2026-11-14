# Verify the fenglin-common-web artifact embedded in every service fat jar.
#
# Hard gate (controls exit code):
#   javap must find all six handlers on com.fenglin.common.web.ApiExceptionHandler.
#   A missing artifact or a missing handler is FAIL.
#
# Soft signal (warning only):
#   SHA256 difference from the current ~/.m2 artifact. Byte equality is not
#   required for correctness; a different build of the same source is WARN.
#
# Exit codes: 0 = no FAIL (PASS/WARN), 1 = at least one FAIL, 2 = javap unavailable.
#
# Usage (Windows PowerShell 5+ / PowerShell 7 on Linux):
#   pwsh -File scripts/verify-common-web.ps1 -Root .
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$M2Jar = '',
    [string]$Javap = '',
    [string]$Version = '0.1.0-SNAPSHOT'
)

$ErrorActionPreference = 'Stop'
try { Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop } catch { }

$requiredHandlers = @(
    'handleBizException',
    'handleValidation',
    'handleMissingParameter',
    'handleTypeMismatch',
    'handleNotFound',
    'handleUnexpected'
)

function Resolve-Javap([string]$explicit) {
    if ($explicit -and (Test-Path $explicit)) { return (Resolve-Path $explicit).Path }

    $exeName = if ($env:OS -eq 'Windows_NT') { 'javap.exe' } else { 'javap' }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin/$exeName"
        if (Test-Path $candidate) { return $candidate }
    }
    if ($env:USERPROFILE) {
        $candidate = Join-Path $env:USERPROFILE "jdk-17/bin/$exeName"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command javap -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$javapPath = Resolve-Javap $Javap
if (-not $javapPath) {
    Write-Host '[FAIL] javap not found. Install JDK 17 or pass -Javap <path>.'
    exit 2
}

$homeDir = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
if (-not $M2Jar) {
    $M2Jar = Join-Path $homeDir ".m2/repository/com/fenglin/fenglin-common-web/$Version/fenglin-common-web-$Version.jar"
}

$m2Hash = ''
$m2Size = ''
if (Test-Path $M2Jar) {
    $m2Hash = (Get-FileHash $M2Jar -Algorithm SHA256).Hash
    $m2Size = (Get-Item $M2Jar).Length
    Write-Host "m2 baseline : $m2Size / $m2Hash"
} else {
    Write-Host "[WARN] m2 baseline not found: $M2Jar (byte comparison skipped)"
}
$javapVersion = (& $javapPath -version 2>&1 | Out-String).Trim()
Write-Host "javap       : $javapPath ($javapVersion)"
Write-Host ''

$platform = Join-Path $Root 'backend/platform'

$tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) ('verify-common-web-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$rows = @()
$failCount = 0
$warnCount = 0
$index = 0

try {
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
        $methodsOk = '-'
        $byteVerdict = 'N/A'
        $verdict = 'FAIL'
        $detail = ''

        if (-not $jar) {
            $detail = 'fat jar not found'
            $failCount++
        } else {
            try {
                $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
                $entry = $zip.Entries |
                         Where-Object { $_.FullName -like 'BOOT-INF/lib/fenglin-common-web*.jar' } |
                         Select-Object -First 1

                if (-not $entry) {
                    if ($name -eq 'gateway') {
                        $methodsOk = 'N/A'
                        $byteVerdict = 'N/A'
                        $verdict = 'PASS'
                        $detail = 'NO_DEP (gateway)'
                    } else {
                        $detail = 'common-web missing from fat jar'
                        $failCount++
                    }
                } else {
                    $size = $entry.Length
                    $extract = Join-Path $tmpDir ("$name-common-web.jar")
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $extract, $true)
                    $hash = (Get-FileHash $extract -Algorithm SHA256).Hash

                    if ($m2Hash) {
                        if ($hash -eq $m2Hash) { $byteVerdict = 'CURRENT' } else { $byteVerdict = 'STALE' }
                    } else {
                        $byteVerdict = 'NO_BASELINE'
                    }

                    $javapOut = & $javapPath -classpath $extract -p com.fenglin.common.web.ApiExceptionHandler 2>&1 | Out-String
                    if ($LASTEXITCODE -ne 0) {
                        $methodsOk = 'NO (javap failed)'
                        $detail = 'javap failed'
                        $failCount++
                    } else {
                        $missing = @()
                        foreach ($m in $requiredHandlers) {
                            if ($javapOut -notmatch ('\b' + [regex]::Escape($m) + '\s*\(')) { $missing += $m }
                        }
                        if ($missing.Count -eq 0) {
                            $methodsOk = 'YES'
                            if ($byteVerdict -eq 'STALE') {
                                $verdict = 'WARN'
                                $warnCount++
                                $detail = 'methods complete; bytes differ from m2'
                            } elseif ($byteVerdict -eq 'NO_BASELINE') {
                                $verdict = 'WARN'
                                $warnCount++
                                $detail = 'methods complete; no m2 baseline'
                            } else {
                                $verdict = 'PASS'
                            }
                        } else {
                            $methodsOk = 'NO'
                            $detail = 'missing: ' + ($missing -join ',')
                            $failCount++
                        }
                    }
                }
                $zip.Dispose()
            } catch {
                $detail = $_.Exception.Message
                $failCount++
            }
        }

        $rows += [pscustomobject]@{
            No = $index
            Service = $name
            Size = $size
            Sha256 = $hash
            Methods = $methodsOk
            Bytes = $byteVerdict
            Verdict = $verdict
            Detail = $detail
        }
    }
} finally {
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
}

$format = '{0,2}  {1,-13} {2,7}  {3,-64}  {4,-16}  {5,-11}  {6,-5}  {7}'
Write-Host ($format -f 'No', 'Service', 'Size', 'SHA256', 'MethodSet', 'Bytes', 'Final', 'Detail')
Write-Host ('-' * 150)
foreach ($r in $rows) {
    Write-Host ($format -f $r.No, $r.Service, $r.Size, $r.Sha256, $r.Methods, $r.Bytes, $r.Verdict, $r.Detail)
}

Write-Host ''
Write-Host ("Summary: PASS={0} WARN={1} FAIL={2}" -f ($rows | Where-Object { $_.Verdict -eq 'PASS' }).Count, $warnCount, $failCount)
Write-Host 'Hard gate: method set on ApiExceptionHandler; SHA256 mismatch is WARN only.'

if ($failCount -gt 0) {
    Write-Host '[FAIL] one or more services failed the hard gate.'
    exit 1
}
Write-Host '[PASS] all services passed the hard gate.'
exit 0
