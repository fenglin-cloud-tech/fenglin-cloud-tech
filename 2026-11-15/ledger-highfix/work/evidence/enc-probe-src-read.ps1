$ErrorActionPreference = 'Stop'
$ansi = [Text.Encoding]::GetEncoding(936)
$pattern = '\$bodyR = ''([^'']*)'''
$rawAnsi = [IO.File]::ReadAllText('e:\fenglin-cloud-tech\.tmp\open04-a.ps1', $ansi)
$m1 = [regex]::Match($rawAnsi, $pattern)
Write-Output ('ANSI(GBK) read literal -> ' + $m1.Groups[1].Value)
$hex = ($m1.Groups[1].Value.ToCharArray() | ForEach-Object { '{0:x2}' -f [int]$_ }) -join ''
Write-Output ('UTF8 bytes of that literal -> ' + $hex)
$rawUtf8 = [IO.File]::ReadAllText('e:\fenglin-cloud-tech\.tmp\open04-a.ps1', [Text.Encoding]::UTF8)
$m2 = [regex]::Match($rawUtf8, $pattern)
Write-Output ('UTF8 read literal -> ' + $m2.Groups[1].Value)
