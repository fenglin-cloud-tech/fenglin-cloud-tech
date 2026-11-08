-- P2 字典与配置：
-- 1) 平台级字典项/参数（tenant_id=0）对所有租户只读可见，租户写入仍受 p_*_tenant 策略约束；
-- 2) 补充字典/参数管理权限点（接口文档未列该组接口，按数据库设计 4.23~4.25 与管理员手册第 7 章兜底）。

SET search_path TO fenglin_hub, public;

CREATE POLICY p_hub_dict_item_platform_read ON hub_dict_item
    FOR SELECT USING (tenant_id = 0);

CREATE POLICY p_hub_param_platform_read ON hub_param
    FOR SELECT USING (tenant_id = 0);

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8121, 'tenant:iam:dict:manage', '字典管理', 'API', 'iam', 21, 0),
       (8122, 'tenant:iam:param:manage', '参数管理', 'API', 'iam', 22, 0)
ON CONFLICT (code) DO NOTHING;
