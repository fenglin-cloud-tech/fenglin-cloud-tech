-- 云研管理端收口：补充集成配置权限点（文档 API-59 预留，按总纲以 tenant:devops:integration:manage 落地）
-- 可重复执行：ON CONFLICT DO NOTHING；ID 段顺延 9508，避开既有 9501-9555。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (9508, 'tenant:devops:integration:manage', '集成配置管理', 'API', 'devops', 17, 0)
ON CONFLICT (code) DO NOTHING;
