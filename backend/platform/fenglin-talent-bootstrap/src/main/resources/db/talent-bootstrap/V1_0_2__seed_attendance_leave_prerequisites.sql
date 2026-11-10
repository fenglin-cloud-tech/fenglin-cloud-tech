-- 云才考勤 / 假期模块前置数据（可重复执行）
-- 1) 权限点 id 8682 ~ 8689（延续 V1_0_1 的 8601~8681 段）；
-- 2) 字典类型 8715 / 8716，字典项 9041 ~ 9053（V1_0_1 用了 8901~9033，这里顺延）；
-- 3) 同步授予各租户 TENANT_ADMIN 角色，否则新权限点只在表里、不会出现在管理端 token 中。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8682, 'talent:admin:attendance:list', '考勤记录列表', 'API', 'talent', 400, 0),
       (8683, 'talent:admin:attendance:manage', '考勤异常处理', 'API', 'talent', 401, 0),
       (8684, 'talent:attendance:clock', '员工打卡', 'API', 'talent', 402, 0),
       (8685, 'talent:attendance:view', '我的考勤查看', 'API', 'talent', 403, 0),
       (8686, 'talent:admin:leave:list', '请假单列表', 'API', 'talent', 410, 0),
       (8687, 'talent:admin:leave:approve', '请假审批', 'API', 'talent', 411, 0),
       (8688, 'talent:leave:apply', '员工请假申请', 'API', 'talent', 412, 0),
       (8689, 'talent:leave:view', '我的假期查看', 'API', 'talent', 413, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_dict_type (id, code, name, scope, status, deleted)
VALUES (8715, 'talent_attendance_status', '考勤状态', 'PLATFORM', 1, 0),
       (8716, 'talent_leave_status', '请假状态', 'PLATFORM', 1, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
VALUES (9041, 0, 'talent_attendance_status', 'NORMAL', '正常', 1, 1, 0),
       (9042, 0, 'talent_attendance_status', 'LATE', '迟到', 2, 1, 0),
       (9043, 0, 'talent_attendance_status', 'EARLY', '早退', 3, 1, 0),
       (9044, 0, 'talent_attendance_status', 'LATE_EARLY', '迟到且早退', 4, 1, 0),
       (9045, 0, 'talent_attendance_status', 'MISSING', '缺卡', 5, 1, 0),
       (9046, 0, 'talent_attendance_status', 'ABSENT', '旷工', 6, 1, 0),
       (9047, 0, 'talent_attendance_status', 'MAKEUP', '补卡', 7, 1, 0),
       (9051, 0, 'talent_leave_status', 'APPROVING', '审批中', 1, 1, 0),
       (9052, 0, 'talent_leave_status', 'APPROVED', '已通过', 2, 1, 0),
       (9053, 0, 'talent_leave_status', 'REJECTED', '已驳回', 3, 1, 0),
       (9054, 0, 'talent_leave_status', 'CANCELED', '已撤销', 4, 1, 0)
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
  AND p.code IN ('talent:admin:attendance:list', 'talent:admin:attendance:manage',
                 'talent:attendance:clock', 'talent:attendance:view',
                 'talent:admin:leave:list', 'talent:admin:leave:approve',
                 'talent:leave:apply', 'talent:leave:view')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
