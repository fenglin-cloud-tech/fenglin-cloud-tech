-- 员工自助闭环权限（可重复执行）
--   biz:collab:approval:submit  发起审批（审批人由提交时指定，处理权仍受“仅本人待办”服务端校验）
--   biz:collab:approval:handle  处理分配给自己待办（后端 requireAssignee 强校验，非本人 403）
--   biz:collab:okr:progress     更新本人目标下 KR 进度（后端校验 objective.owner_id = 当前用户）
-- 不授予 okr:manage / calendar:manage 等管理端配置能力。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8515, 'biz:collab:approval:submit', '审批发起', 'API', 'collab', 61, 0),
       (8516, 'biz:collab:approval:handle', '审批处理', 'API', 'collab', 62, 0),
       (8513, 'biz:collab:okr:progress', 'KR 进度更新', 'API', 'collab', 52, 0)
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
  AND p.code IN ('biz:collab:approval:submit', 'biz:collab:approval:handle', 'biz:collab:okr:progress')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
