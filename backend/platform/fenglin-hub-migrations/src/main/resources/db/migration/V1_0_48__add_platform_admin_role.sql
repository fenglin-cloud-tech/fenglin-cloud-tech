-- 平台管理员角色（PLATFORM_ADMIN）：持有全部 platform:* 权限点，用于平台运维审核与治理。
-- 背景：openapi 审核/配额等管理端接口要求 platform:openapi:*，但库中没有任何角色持有 platform:*，
--       导致开放应用审核长期不可执行。角色按租户落位（hub_role 租户隔离建模），但不自动授予用户，
--       由平台运维在目标租户内分配给平台账号，避免租户管理员默认获得平台权限。
INSERT INTO fenglin_hub.hub_role
    (id, tenant_id, code, name, role_type, data_scope, builtin, status, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY t.id) AS id,
       t.id AS tenant_id,
       'PLATFORM_ADMIN' AS code,
       '平台管理员' AS name,
       'SYS' AS role_type,
       'ALL' AS data_scope,
       1 AS builtin,
       'ENABLED' AS status,
       now(),
       now(),
       0,
       0
FROM fenglin_hub.hub_tenant t
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role) base
WHERE t.deleted = 0
ON CONFLICT (tenant_id, code) DO NOTHING;

-- 授予全部已存在的平台级权限点；新增的 platform:* 权限点由后续迁移按同样模式补齐。
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
JOIN fenglin_hub.hub_permission p
  ON p.deleted = 0
 AND p.code LIKE 'platform:%'
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
WHERE r.code = 'PLATFORM_ADMIN'
  AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
