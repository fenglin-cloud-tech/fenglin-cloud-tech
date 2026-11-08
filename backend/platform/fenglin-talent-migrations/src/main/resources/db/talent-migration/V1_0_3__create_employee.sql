-- C1 员工主数据（PII 加密）
-- 文档基线：03_丰麟云才_03 第 4.1.5 节、第 7 节
-- 安全约定：仅保留 phone_enc / phone_hash、id_card_enc / id_card_hash，
--           不建 phone / id_card 明文列，也不对明文建索引（文档 P5/P6）。
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_employee (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    emp_no varchar(32) NOT NULL,
    name varchar(64) NOT NULL,
    gender varchar(8),
    birth_date date,
    phone_enc varchar(512),
    phone_hash char(64),
    id_card_enc varchar(512),
    id_card_hash char(64),
    email varchar(128),
    org_id bigint NOT NULL,
    position_id bigint,
    grade_id bigint,
    manager_id bigint,
    employment_type varchar(16) NOT NULL,
    hire_date date NOT NULL,
    formal_date date,
    leave_date date,
    work_location varchar(128),
    emp_status varchar(16) NOT NULL,
    avatar_file_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_employee IS '员工主数据（手机号/身份证号 AES-256-GCM 密文 + 租户盐哈希，禁止明文检索）';
COMMENT ON COLUMN talent_employee.gender IS '性别：M/F/UNKNOWN';
COMMENT ON COLUMN talent_employee.employment_type IS '用工类型：REGULAR/INTERN/OUTSOURCE';
COMMENT ON COLUMN talent_employee.emp_status IS '员工状态：PENDING/PROBATION/ACTIVE/RESIGNED';
COMMENT ON COLUMN talent_employee.phone_enc IS '手机号密文（AES-256-GCM）';
COMMENT ON COLUMN talent_employee.phone_hash IS '手机号哈希（SHA-256 + 租户盐），唯一约束与精确检索';
COMMENT ON COLUMN talent_employee.id_card_enc IS '身份证号密文（AES-256-GCM）';
COMMENT ON COLUMN talent_employee.id_card_hash IS '身份证号哈希（SHA-256 + 租户盐），唯一约束与精确检索';
