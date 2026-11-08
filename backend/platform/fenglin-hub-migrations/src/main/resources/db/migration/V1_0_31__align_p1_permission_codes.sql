-- P1 权限点补齐：使实现与《接口文档》HUB-API-201/204/301/302/306/401/405 的权限码一致
-- 契约缺口：文档未定义用户创建/更新、组织、角色管理、角色分配对应的权限码，此处按命名约定补齐

SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8111, 'platform:tenant:manage', '平台租户管理', 'API', 'platform', 11, 0),
       (8112, 'platform:billing:manage', '平台计费管理', 'API', 'platform', 12, 0),
       (8113, 'tenant:iam:user:create', '用户创建', 'API', 'iam', 13, 0),
       (8114, 'tenant:iam:user:update', '用户更新', 'API', 'iam', 14, 0),
       (8115, 'tenant:iam:org:manage', '组织管理', 'API', 'iam', 15, 0),
       (8116, 'tenant:iam:role:manage', '角色管理', 'API', 'iam', 16, 0),
       (8117, 'tenant:iam:role:assign', '角色分配', 'API', 'iam', 17, 0)
ON CONFLICT (code) DO NOTHING;
