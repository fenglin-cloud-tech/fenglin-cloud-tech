$ErrorActionPreference = 'Continue'
$out = 'e:\fenglin-cloud-tech\2026-11-15\ledger-highfix\work\evidence'
$gw = 'http://localhost:18080'
$tok = (Get-Content "$out\plat27-admin.json" -Raw -Encoding UTF8 | ConvertFrom-Json).data.access_token
$utf8 = New-Object Text.UTF8Encoding($false)

$bodyA = '{"name":"OPEN04-Evidence-Reject-1115","ownerType":"INTERNAL","scopes":[],"redirectUris":["https://example.com/cb"]}'
[IO.File]::WriteAllText("e:\fenglin-cloud-tech\.tmp\ev-a2.json", $bodyA, $utf8)
$code = & curl.exe -s -o "$out\open04-create-reject-app.json" -w "%{http_code}" -X POST -H "Authorization: Bearer $tok" -H "Content-Type: application/json; charset=utf-8" --data-binary "@e:\fenglin-cloud-tech\.tmp\ev-a2.json" "$gw/openapi/portal/apps"
$aId = (Get-Content "$out\open04-create-reject-app.json" -Raw -Encoding UTF8 | ConvertFrom-Json).data.app_id
Write-Output "create A -> HTTP $code app_id=$aId"

$code = & curl.exe -s -o "$out\open04-detail-reject-before.json" -w "%{http_code}" -H "Authorization: Bearer $tok" "$gw/openapi/portal/apps/$aId"
Write-Output "detail A before -> HTTP $code"

$bodyE = '{"action":"REJECT"}'
[IO.File]::WriteAllText("e:\fenglin-cloud-tech\.tmp\ev-e2.json", $bodyE, $utf8)
$code = & curl.exe -s -o "$out\open04-reject-no-reason.json" -w "%{http_code}" -X POST -H "Authorization: Bearer $tok" -H "Content-Type: application/json; charset=utf-8" --data-binary "@e:\fenglin-cloud-tech\.tmp\ev-e2.json" "$gw/api/hub/v1/admin/openapi/apps/$aId`:review"
Write-Output "reject A no reason -> HTTP $code : $((Get-Content "$out\open04-reject-no-reason.json" -Raw -Encoding UTF8))"

$bodyR = '{"action":"REJECT","reason":"缺少生产回调域名白名单说明，请补充后重新提交"}'
[IO.File]::WriteAllText("e:\fenglin-cloud-tech\.tmp\ev-r2.json", $bodyR, $utf8)
$code = & curl.exe -s -o "$out\open04-reject-with-reason.json" -w "%{http_code}" -X POST -H "Authorization: Bearer $tok" -H "Content-Type: application/json; charset=utf-8" --data-binary "@e:\fenglin-cloud-tech\.tmp\ev-r2.json" "$gw/api/hub/v1/admin/openapi/apps/$aId`:review"
Write-Output "reject A with reason -> HTTP $code"

$code = & curl.exe -s -o "$out\open04-detail-reject-after.json" -w "%{http_code}" -H "Authorization: Bearer $tok" "$gw/openapi/portal/apps/$aId"
Write-Output "detail A after -> HTTP $code"

$code = & curl.exe -s -o "$out\open04-admin-apps.json" -w "%{http_code}" -H "Authorization: Bearer $tok" "$gw/api/hub/v1/admin/openapi/apps?page=1&size=50"
Write-Output "admin list -> HTTP $code"
