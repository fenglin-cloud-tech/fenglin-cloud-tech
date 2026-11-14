-- 云研员工自助权限（可重复执行）
-- 只守护服务端按登录账号维度收敛的接口，不覆盖租户级列表：
--   biz:devops:task:self:view          GET /tasks/my          按 assignee_id = 当前用户
--   biz:devops:worklog:self:view       GET /worklogs/my       按 user_id = 当前用户
--   biz:devops:worklog:self:submit     POST /worklogs         写入时强制 user_id = 当前用户
--   biz:devops:defect:self:view        GET /defects/my        按 reporter_id / assignee_id = 当前用户
--   biz:devops:defect:self:create      POST /defects          写入时强制 reporter_id = 当前用户
--   biz:devops:contribution:self:view  GET /metrics/contributions/my 按 user_id = 当前用户
-- 授予各租户 TENANT_ADMIN、EMPLOYEE 与 PLATFORM_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10643, 'biz:devops:task:self:view', '我的任务查看', 'API', 'devops', 75, 0),
       (10644, 'biz:devops:worklog:self:view', '我的工时查看', 'API', 'devops', 76, 0),
       (10645, 'biz:devops:worklog:self:submit', '我的工时登记', 'API', 'devops', 77, 0),
       (10646, 'biz:devops:defect:self:view', '我的缺陷查看', 'API', 'devops', 78, 0),
       (10647, 'biz:devops:defect:self:create', '我的缺陷提报', 'API', 'devops', 79, 0),
       (10648, 'biz:devops:contribution:self:view', '我的贡献查看', 'API', 'devops', 80, 0)
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
JOIN hub_permission p ON p.domain = 'devops' AND p.deleted = 0
WHERE r.code IN ('TENANT_ADMIN', 'EMPLOYEE', 'PLATFORM_ADMIN') AND r.deleted = 0
  AND p.code IN ('biz:devops:task:self:view', 'biz:devops:worklog:self:view',
                 'biz:devops:worklog:self:submit', 'biz:devops:defect:self:view',
                 'biz:devops:defect:self:create', 'biz:devops:contribution:self:view')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
