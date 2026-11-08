-- P1 菜单权限关联 + 平台权限点/菜单种子数据（对应 T1-04/T1-10）

SET search_path TO fenglin_hub, public;

ALTER TABLE hub_menu ADD COLUMN IF NOT EXISTS permission_code varchar(128);
CREATE INDEX IF NOT EXISTS idx_hub_menu_permission ON hub_menu (permission_code);

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8101, 'hub:tenant:read', '租户查看', 'API', 'tenant', 1, 0),
       (8102, 'hub:tenant:write', '租户管理', 'API', 'tenant', 2, 0),
       (8103, 'hub:iam:user:read', '用户查看', 'API', 'iam', 3, 0),
       (8104, 'hub:iam:user:write', '用户管理', 'API', 'iam', 4, 0),
       (8105, 'hub:iam:role:read', '角色查看', 'API', 'iam', 5, 0),
       (8106, 'hub:iam:role:write', '角色管理', 'API', 'iam', 6, 0),
       (8107, 'hub:iam:menu:read', '菜单查看', 'API', 'iam', 7, 0),
       (8108, 'hub:audit:read', '审计查看', 'API', 'audit', 8, 0),
       (8109, 'hub:gw:route:write', '网关路由管理', 'API', 'gateway', 9, 0),
       (8110, 'hub:gw:route:read', '网关路由查看', 'API', 'gateway', 10, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_menu (id, code, name, client_type, path, component, parent_code, icon, sort, visible, permission_code, deleted)
VALUES (8201, 'hub.iam.user', '用户管理', 'ADMIN', '/iam/users', 'iam/users/index', 'hub.iam', 'user', 10, 1, 'hub:iam:user:read', 0),
       (8202, 'hub.iam.role', '角色权限', 'ADMIN', '/iam/roles', 'iam/roles/index', 'hub.iam', 'lock', 20, 1, 'hub:iam:role:read', 0),
       (8203, 'hub.tenant.list', '租户管理', 'ADMIN', '/tenants', 'tenant/list/index', 'hub.tenant', 'office-building', 30, 1, 'hub:tenant:read', 0),
       (8204, 'hub.audit.log', '审计日志', 'ADMIN', '/audit/logs', 'audit/logs/index', 'hub.audit', 'document', 40, 1, 'hub:audit:read', 0),
       (8205, 'hub.gateway.route', '网关路由', 'ADMIN', '/gateway/routes', 'gateway/routes/index', 'hub.gateway', 'connection', 50, 1, 'hub:gw:route:read', 0)
ON CONFLICT (code) DO NOTHING;
