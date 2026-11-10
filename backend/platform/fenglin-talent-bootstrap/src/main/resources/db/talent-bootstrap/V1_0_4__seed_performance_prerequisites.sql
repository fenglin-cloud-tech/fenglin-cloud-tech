-- 云才绩效模块前置数据（可重复执行）
-- 1) 权限点 id 8693 ~ 8695（延续 V1_0_3 的 8690~8692 段）；
-- 2) 字典类型 8719 ~ 8722，字典项 9081 ~ 9097（延续 V1_0_3 的 9061~9074 段）；
-- 3) 同步授予各租户 TENANT_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8693, 'talent:admin:performance:list', '绩效查询', 'API', 'talent', 430, 0),
       (8694, 'talent:admin:performance:manage', '绩效周期/目标/评估管理', 'API', 'talent', 431, 0),
       (8695, 'talent:admin:performance:approve', '绩效目标/评估审批与结果发布', 'API', 'talent', 432, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_dict_type (id, code, name, scope, status, deleted)
VALUES (8719, 'talent_perf_cycle_status', '绩效周期状态', 'PLATFORM', 1, 0),
       (8720, 'talent_perf_goal_status', '绩效目标状态', 'PLATFORM', 1, 0),
       (8721, 'talent_perf_evaluation_status', '绩效评估状态', 'PLATFORM', 1, 0),
       (8722, 'talent_perf_grade', '绩效等级', 'PLATFORM', 1, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
VALUES (9081, 0, 'talent_perf_cycle_status', 'DRAFT', '草稿', 1, 1, 0),
       (9082, 0, 'talent_perf_cycle_status', 'GOAL_SETTING', '目标制定', 2, 1, 0),
       (9083, 0, 'talent_perf_cycle_status', 'EVALUATING', '评估中', 3, 1, 0),
       (9084, 0, 'talent_perf_cycle_status', 'CLOSED', '已关闭', 4, 1, 0),
       (9085, 0, 'talent_perf_goal_status', 'DRAFT', '草稿', 1, 1, 0),
       (9086, 0, 'talent_perf_goal_status', 'SUBMITTED', '待审批', 2, 1, 0),
       (9087, 0, 'talent_perf_goal_status', 'APPROVED', '已通过', 3, 1, 0),
       (9088, 0, 'talent_perf_goal_status', 'REJECTED', '已驳回', 4, 1, 0),
       (9089, 0, 'talent_perf_evaluation_status', 'DRAFT', '草稿', 1, 1, 0),
       (9090, 0, 'talent_perf_evaluation_status', 'APPROVING', '审批中', 2, 1, 0),
       (9091, 0, 'talent_perf_evaluation_status', 'APPROVED', '已通过', 3, 1, 0),
       (9092, 0, 'talent_perf_evaluation_status', 'REJECTED', '已驳回', 4, 1, 0),
       (9093, 0, 'talent_perf_grade', 'S', '卓越', 1, 1, 0),
       (9094, 0, 'talent_perf_grade', 'A', '优秀', 2, 1, 0),
       (9095, 0, 'talent_perf_grade', 'B', '良好', 3, 1, 0),
       (9096, 0, 'talent_perf_grade', 'C', '待改进', 4, 1, 0),
       (9097, 0, 'talent_perf_grade', 'D', '不合格', 5, 1, 0)
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
  AND p.code IN ('talent:admin:performance:list', 'talent:admin:performance:manage',
                 'talent:admin:performance:approve')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
