$ErrorActionPreference = 'Stop'
$ansi = [Text.Encoding]::GetEncoding(936)
$utf8 = New-Object Text.UTF8Encoding($false)
$pattern = '\$body(R|Ap) = ''([^'']*)'''
$rawAnsi = [IO.File]::ReadAllText('e:\fenglin-cloud-tech\.tmp\open04-a.ps1', $ansi)
foreach ($m in [regex]::Matches($rawAnsi, $pattern)) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($m.Groups[2].Value)
    Write-Output ($m.Groups[1].Value + ' ' + [Convert]::ToBase64String($bytes))
}
