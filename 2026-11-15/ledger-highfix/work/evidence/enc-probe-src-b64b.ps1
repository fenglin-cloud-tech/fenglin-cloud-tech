$ErrorActionPreference = 'Stop'
$ansi = [Text.Encoding]::GetEncoding(936)
$pattern = '\$body(Ap|R|A|B|E) = ''([^'']*)'''
foreach ($file in @('e:\fenglin-cloud-tech\.tmp\open04-evidence.ps1', 'e:\fenglin-cloud-tech\.tmp\open04-evidence2.ps1')) {
    $raw = [IO.File]::ReadAllText($file, $ansi)
    foreach ($m in [regex]::Matches($raw, $pattern)) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($m.Groups[2].Value)
        Write-Output ((Split-Path $file -Leaf) + '|' + $m.Groups[1].Value + '|' + [Convert]::ToBase64String($bytes))
    }
}
