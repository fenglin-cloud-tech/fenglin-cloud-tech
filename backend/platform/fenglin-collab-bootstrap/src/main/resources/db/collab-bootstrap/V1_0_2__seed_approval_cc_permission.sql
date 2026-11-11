-- COL-01c：审批抄送查看权限点（可重复执行）
-- 1) biz:collab:approval:cc:read（id 8530）：仅允许访问 /approvals/cc/my 与抄送已读接口；
-- 2) 同步授予各租户 TENANT_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8530, 'biz:collab:approval:cc:read', '审批抄送查看', 'API', 'collab', 63, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       p.code,
       now(),
       0,
       0
FROM hub_role r
JOIN hub_permission p ON p.code = 'biz:collab:approval:cc:read' AND p.deleted = 0
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
