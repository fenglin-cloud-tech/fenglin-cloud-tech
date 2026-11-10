-- 开发者门户权限点（HUB-API-1001/1003/1004/1005 的门户侧收敛能力）。
-- 背景：原始 1001/1003/1004/1005 接口挂在 /api/hub/v1/admin/openapi/**，仅平台角色可访问；
--       开发者门户（PORTAL 客户端）需要同一批能力的自有应用视图，故补充 biz 前缀权限点。
INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8191, 'biz:openapi:app:manage', '开发者门户应用创建与配置', 'API', 'openapi', 61, 0),
       (8192, 'biz:openapi:key:manage', '开发者门户密钥签发与轮换', 'API', 'openapi', 62, 0),
       (8193, 'biz:openapi:quota:view', '开发者门户配额与用量查看', 'API', 'openapi', 63, 0),
       (8194, 'biz:openapi:calllog:view', '开发者门户调用日志查看', 'API', 'openapi', 64, 0)
ON CONFLICT (code) DO NOTHING;

-- 已有租户补齐授权；新租户由 tenant-service 创建 TENANT_ADMIN 时自动授予全部非 platform 权限。
INSERT INTO fenglin_hub.hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id, p.code) AS id,
       r.tenant_id,
       r.id AS role_id,
       p.code AS permission_code,
       now(),
       now(),
       0,
       0
FROM fenglin_hub.hub_role r
CROSS JOIN (VALUES ('biz:openapi:app:manage'),
                   ('biz:openapi:key:manage'),
                   ('biz:openapi:quota:view'),
                   ('biz:openapi:calllog:view')) AS p(code)
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
WHERE r.code = 'TENANT_ADMIN'
  AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
