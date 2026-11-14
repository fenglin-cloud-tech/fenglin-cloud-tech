-- 云协员工自助权限（可重复执行）
--   biz:collab:doc:self:read       GET /docs/my                  仅 owner_id = 当前登录用户
--   biz:collab:im:self:chat        GET /im/conversations/my      仅本人所在会话
--   biz:collab:calendar:self:read  GET /events/my                仅本人日历或本人参会的日程
--   biz:collab:okr:self:read       GET /okr/objectives/my        仅 owner_id = 当前登录用户
-- 授予各租户 TENANT_ADMIN、EMPLOYEE 与 PLATFORM_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10651, 'biz:collab:doc:self:read', '我的文档查看', 'API', 'collab', 24, 0),
       (10652, 'biz:collab:im:self:chat', '我的消息查看', 'API', 'collab', 12, 0),
       (10653, 'biz:collab:calendar:self:read', '我的日程查看', 'API', 'collab', 32, 0),
       (10654, 'biz:collab:okr:self:read', '我的 OKR 查看', 'API', 'collab', 53, 0)
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
JOIN hub_permission p ON p.domain = 'collab' AND p.deleted = 0
WHERE r.code IN ('TENANT_ADMIN', 'EMPLOYEE', 'PLATFORM_ADMIN') AND r.deleted = 0
  AND p.code IN ('biz:collab:doc:self:read', 'biz:collab:im:self:chat',
                 'biz:collab:calendar:self:read', 'biz:collab:okr:self:read')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
