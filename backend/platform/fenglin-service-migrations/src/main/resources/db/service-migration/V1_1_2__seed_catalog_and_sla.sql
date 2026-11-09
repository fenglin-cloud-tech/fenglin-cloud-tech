-- 云服内置种子：平台默认服务目录 + 目录项 + SLA 策略（tenant_id=0，租户可覆盖/复制）
-- 文档 §8 V1.0.17。字典项（工单类型/优先级/来源/状态）在 fenglin-service-bootstrap 写入平台 hub_dict_*。
SET search_path TO fenglin_service, public;

INSERT INTO svc_sla_policy
    (id, tenant_id, name, priority, ticket_type, calendar_id, response_minutes, resolve_minutes,
     warning_threshold, status, created_by)
VALUES
    (900001, 0, 'P1 紧急事件', 'P1', 'INCIDENT', 'CN_WORKDAY', 15, 120, 80, 'ACTIVE', 0),
    (900002, 0, 'P3 标准请求', 'P3', 'REQUEST', 'CN_WORKDAY', 60, 1440, 80, 'ACTIVE', 0)
ON CONFLICT DO NOTHING;

INSERT INTO svc_catalog
    (id, tenant_id, name, icon, sort_no, status, created_by)
VALUES
    (900101, 0, 'IT 支持', 'laptop', 1, 'ACTIVE', 0)
ON CONFLICT DO NOTHING;

INSERT INTO svc_catalog_item
    (id, tenant_id, catalog_id, code, name, description, ticket_type, sla_policy_id,
     need_approval, sort_no, status, created_by)
VALUES
    (900201, 0, 900101, 'IT-ACCOUNT', '账号与权限申请', '新建账号、权限调整、系统开通', 'REQUEST', 900002, true, 1, 'ACTIVE', 0),
    (900202, 0, 900101, 'IT-DEVICE', '设备报修', '电脑、外设、网络故障报修', 'INCIDENT', 900001, false, 2, 'ACTIVE', 0),
    (900203, 0, 900101, 'IT-SOFT', '软件安装', '正版软件安装与授权', 'REQUEST', 900002, false, 3, 'ACTIVE', 0)
ON CONFLICT DO NOTHING;
