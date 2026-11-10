# 重建/回灌后补齐各租户 TENANT_ADMIN 的 8 域权限授权（可重复执行）。
# 用法：powershell -File scripts/grant-tenant-admin-permissions.ps1
# 说明：脚本幂等，连跑多次结果一致；平台级权限（platform:*）不会授予租户管理员。
$ErrorActionPreference = 'Stop'

$sqlPath = Join-Path $PSScriptRoot '..\backend\platform\fenglin-hub-migrations\src\main\resources\db\maintenance\grant_tenant_admin_permissions.sql'
$sqlPath = (Resolve-Path $sqlPath).Path

Write-Output "执行授权脚本: $sqlPath"
Get-Content -Raw -Encoding UTF8 $sqlPath |
    docker exec -i fenglin-postgres psql -U fenglin -d fenglin_hub -v ON_ERROR_STOP=1 -f -
if ($LASTEXITCODE -ne 0) {
    throw "授权脚本执行失败，psql 退出码 $LASTEXITCODE"
}

$verifySql = @'
SELECT r.tenant_id,
       r.code AS role_code,
       count(*) FILTER (WHERE p.domain IN ('crm','collab','talent','finance','devops','service','legal','ai')
                          AND p.code NOT LIKE 'platform:%') AS granted_8_domains
FROM fenglin_hub.hub_role r
LEFT JOIN fenglin_hub.hub_role_permission rp
       ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.deleted = 0
LEFT JOIN fenglin_hub.hub_permission p
       ON p.code = rp.permission_code AND p.deleted = 0
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
GROUP BY r.tenant_id, r.code
ORDER BY r.tenant_id;
'@
$verifySql | docker exec -i fenglin-postgres psql -U fenglin -d fenglin_hub -v ON_ERROR_STOP=1 -f -
