-- 员工自助：在本人目标下创建 KR（可重复执行）
-- 仅授 EMPLOYEE；不给 okr:manage。
-- 服务端强校验 objective.owner_id = 当前用户（X-User-Id），非本人目标 403。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10661, 'biz:collab:okr:self:create', '我的 KR 创建', 'API', 'collab', 53, 0)
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
JOIN hub_permission p ON p.deleted = 0
WHERE r.code = 'EMPLOYEE' AND r.deleted = 0
  AND p.code = 'biz:collab:okr:self:create'
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
