-- 云才 TAL-05 / TAL-09：试用期列表与职级新建/调整权限（纯数据迁移，无 DDL）
-- 1) 10704 talent:admin:grade:create  职级新建  —— POST /grades（此前控制器有码、权限目录无码）
-- 2) 10705 talent:admin:grade:update   职级调整  —— PUT  /grades/{id}
-- 3) 10706 talent:admin:probation:list 试用期列表 —— GET /probations（此前只有按 ID 评估，无列表可查）
-- 4) 授予各租户 TENANT_ADMIN，与既有 grade:list / probation:evaluate 配套。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10704, 'talent:admin:grade:create', '职级新建', 'API', 'talent', 94, 0),
       (10705, 'talent:admin:grade:update', '职级调整', 'API', 'talent', 95, 0),
       (10706, 'talent:admin:probation:list', '试用期列表', 'API', 'talent', 96, 0)
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
JOIN hub_permission p ON p.domain = 'talent' AND p.deleted = 0
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
  AND p.code IN ('talent:admin:grade:create', 'talent:admin:grade:update', 'talent:admin:probation:list')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
