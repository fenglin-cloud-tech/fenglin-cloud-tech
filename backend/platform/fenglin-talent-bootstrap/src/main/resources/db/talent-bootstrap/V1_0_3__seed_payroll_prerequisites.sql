-- 云才薪酬模块前置数据（可重复执行）
-- 1) 权限点 id 8690 ~ 8692（延续 V1_0_2 的 8682~8689 段）；
-- 2) 字典类型 8717 / 8718，字典项 9061 ~ 9074（延续 V1_0_2 的 9041~9054 段）；
-- 3) 同步授予各租户 TENANT_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8690, 'talent:admin:payroll:list', '薪酬查询', 'API', 'talent', 420, 0),
       (8691, 'talent:admin:payroll:manage', '薪酬项与周期管理', 'API', 'talent', 421, 0),
       (8692, 'talent:admin:payroll:approve', '薪资发放单审批与发放', 'API', 'talent', 422, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_dict_type (id, code, name, scope, status, deleted)
VALUES (8717, 'talent_pay_period_status', '薪资周期状态', 'PLATFORM', 1, 0),
       (8718, 'talent_payslip_status', '薪资发放单状态', 'PLATFORM', 1, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
VALUES (9061, 0, 'talent_pay_period_status', 'DRAFT', '草稿', 1, 1, 0),
       (9062, 0, 'talent_pay_period_status', 'CALCULATED', '已核算', 2, 1, 0),
       (9063, 0, 'talent_pay_period_status', 'APPROVING', '发放审批中', 3, 1, 0),
       (9064, 0, 'talent_pay_period_status', 'APPROVED', '审批通过', 4, 1, 0),
       (9065, 0, 'talent_pay_period_status', 'PAID', '已发放', 5, 1, 0),
       (9066, 0, 'talent_pay_period_status', 'CLOSED', '已关闭', 6, 1, 0),
       (9071, 0, 'talent_payslip_status', 'APPROVING', '审批中', 1, 1, 0),
       (9072, 0, 'talent_payslip_status', 'APPROVED', '已通过', 2, 1, 0),
       (9073, 0, 'talent_payslip_status', 'PAID', '已发放', 3, 1, 0),
       (9074, 0, 'talent_payslip_status', 'VOID', '已作废', 4, 1, 0)
ON CONFLICT DO NOTHING;

-- 新权限点默认授予各租户管理员角色（幂等：按 tenant_id + role_id + permission_code 去重）
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
  AND p.code IN ('talent:admin:payroll:list', 'talent:admin:payroll:manage',
                 'talent:admin:payroll:approve')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
