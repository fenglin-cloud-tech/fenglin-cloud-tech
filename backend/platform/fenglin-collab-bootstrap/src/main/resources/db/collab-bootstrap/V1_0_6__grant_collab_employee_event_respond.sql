-- 云协员工自助：响应日程邀请（可重复执行）
--   biz:collab:calendar:self:respond  POST /events/{eventId}/respond
-- 仅授 EMPLOYEE；服务端强校验参会记录属于当前用户，组织者调用被拒。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10662, 'biz:collab:calendar:self:respond', '我的日程邀请响应', 'API', 'collab', 33, 0)
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
  AND p.code = 'biz:collab:calendar:self:respond'
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
