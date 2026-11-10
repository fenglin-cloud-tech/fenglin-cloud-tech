-- 云才考勤：日打卡记录（含异常状态与行政处理）
-- 设计约定：
--   1) 一人一天一条记录，部分唯一索引兜底重复打卡（应用层幂等 + 数据库约束双保险）；
--   2) 状态枚举 varchar + 注释，校验在应用层；
--   3) RLS 与审计列沿用 V1_0_3 员工表范式。
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_attendance_record (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    work_date date NOT NULL,
    clock_in_at timestamptz,
    clock_out_at timestamptz,
    source varchar(16) NOT NULL DEFAULT 'WEB',
    att_status varchar(16) NOT NULL DEFAULT 'NORMAL',
    work_minutes int NOT NULL DEFAULT 0,
    late_minutes int NOT NULL DEFAULT 0,
    early_minutes int NOT NULL DEFAULT 0,
    exception_note varchar(500),
    handled_by bigint,
    handled_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_attendance_record IS '考勤日打卡记录（一人一天一条，唯一索引保证幂等）';
COMMENT ON COLUMN talent_attendance_record.source IS '打卡来源：WEB/MOBILE/DEVICE';
COMMENT ON COLUMN talent_attendance_record.att_status IS '考勤状态：NORMAL/LATE/EARLY/LATE_EARLY/MISSING/ABSENT/MAKEUP';
COMMENT ON COLUMN talent_attendance_record.work_minutes IS '当日工作时长（分钟）';
COMMENT ON COLUMN talent_attendance_record.late_minutes IS '迟到分钟数';
COMMENT ON COLUMN talent_attendance_record.early_minutes IS '早退分钟数';

CREATE UNIQUE INDEX uk_att_emp_day ON talent_attendance_record (tenant_id, employee_id, work_date)
    WHERE deleted = 0;
CREATE INDEX idx_att_day ON talent_attendance_record (tenant_id, work_date);
CREATE INDEX idx_att_status ON talent_attendance_record (tenant_id, att_status);

ALTER TABLE talent_attendance_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_attendance_record FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_attendance_record_tenant ON talent_attendance_record;
CREATE POLICY p_talent_attendance_record_tenant ON talent_attendance_record
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_talent_attendance_record_updated ON talent_attendance_record;
CREATE TRIGGER trg_talent_attendance_record_updated BEFORE UPDATE ON talent_attendance_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON talent_attendance_record TO fl_talent_app;
