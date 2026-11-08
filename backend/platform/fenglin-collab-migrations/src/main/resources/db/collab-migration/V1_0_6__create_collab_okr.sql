-- OKR 域：周期、目标、关键结果、对齐、进展
-- 扩展：collab_okr_period（接口 API-OKR-050 需要周期配置，数据库文档仅引用 period_id 未定义周期表）。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_okr_period (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(32) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    score_policy varchar(32) NOT NULL DEFAULT 'AVG_KR',
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_okr_period_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_okr_period_range CHECK (end_date >= start_date)
);

CREATE TABLE collab_okr_objective (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    period_id uuid NOT NULL REFERENCES collab_okr_period (id),
    owner_id bigint NOT NULL,
    obj_type varchar(16) NOT NULL DEFAULT 'PERSONAL',
    dept_id bigint,
    title varchar(256) NOT NULL,
    description_cipher text,
    visibility varchar(16) NOT NULL DEFAULT 'DEPT',
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    score numeric(5, 2),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_okr_obj_type CHECK (obj_type IN ('COMPANY', 'DEPT', 'TEAM', 'PERSONAL')),
    CONSTRAINT ck_okr_obj_visibility CHECK (visibility IN ('PRIVATE', 'DEPT', 'ALL')),
    CONSTRAINT ck_okr_obj_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED'))
);

CREATE TABLE collab_okr_key_result (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    objective_id uuid NOT NULL REFERENCES collab_okr_objective (id),
    owner_id bigint,
    title varchar(256) NOT NULL,
    metric_type varchar(16) NOT NULL DEFAULT 'PERCENT',
    target_value numeric(18, 4),
    current_value numeric(18, 4),
    progress numeric(5, 2) NOT NULL DEFAULT 0,
    confidence varchar(8) NOT NULL DEFAULT 'MID',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_kr_metric CHECK (metric_type IN ('PERCENT', 'NUMBER', 'BOOLEAN')),
    CONSTRAINT ck_kr_confidence CHECK (confidence IN ('HIGH', 'MID', 'LOW')),
    CONSTRAINT ck_kr_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE collab_okr_alignment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    child_objective_id uuid NOT NULL,
    parent_objective_id uuid NOT NULL,
    align_type varchar(16) NOT NULL DEFAULT 'VERTICAL',
    weight numeric(5, 2),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_okr_align_type CHECK (align_type IN ('VERTICAL', 'HORIZONTAL')),
    CONSTRAINT uk_okr_align UNIQUE (tenant_id, child_objective_id, parent_objective_id)
);

CREATE TABLE collab_okr_progress (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    kr_id uuid NOT NULL,
    progress numeric(5, 2) NOT NULL,
    confidence varchar(8) NOT NULL,
    note_cipher text,
    reported_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_okr_progress_range CHECK (progress BETWEEN 0 AND 100)
);

COMMENT ON TABLE collab_okr_period IS '扩展：文档未定义周期表，按 API-OKR-050 最小语义补齐';
