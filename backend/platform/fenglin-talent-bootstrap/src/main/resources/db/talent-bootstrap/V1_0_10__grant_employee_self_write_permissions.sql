-- 云才员工自助写权限：按控制器实际校验码授予 EMPLOYEE（可重复执行）
-- 1) biz:talent:course:enroll   POST /training/sessions/{id}/enroll      培训报名
-- 2) talent:attendance:clock    POST /attendance/clock                   员工打卡
-- 3) talent:leave:apply         POST /leave/requests                     员工请假申请
-- 4) biz:talent:flow:apply      POST /flow/applications                  活水申请
-- 说明：以上四处控制器常量即为实际校验码；talent 域只有查看类 self 语义码
--      （talent:self:attendance:view / talent:self:leave:view），写操作无 self 码可复用。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       p.code,
       now(),
       0,
       0
FROM hub_role r
JOIN hub_permission p ON p.deleted = 0
WHERE r.code = 'EMPLOYEE' AND r.deleted = 0
  AND p.code IN ('biz:talent:course:enroll', 'talent:attendance:clock',
                 'talent:leave:apply', 'biz:talent:flow:apply')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
