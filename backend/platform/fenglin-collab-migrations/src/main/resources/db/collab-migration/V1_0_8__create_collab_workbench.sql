-- 工作台域：组件、布局、通知、待办
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_workbench_widget (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    widget_type varchar(32) NOT NULL,
    default_size varchar(16) NOT NULL DEFAULT 'MEDIUM',
    config_schema jsonb,
    enabled smallint NOT NULL DEFAULT 1,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_widget UNIQUE (tenant_id, code)
);

CREATE TABLE collab_workbench_layout (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    owner_id bigint,
    layout_type varchar(16) NOT NULL DEFAULT 'PERSONAL',
    target_dept_id bigint,
    widgets jsonb NOT NULL DEFAULT '[]'::jsonb,
    is_default smallint NOT NULL DEFAULT 0,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_wb_layout_type CHECK (layout_type IN ('PERSONAL', 'TEMPLATE'))
);

CREATE TABLE collab_notification (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    receiver_id bigint NOT NULL,
    category varchar(16) NOT NULL,
    title varchar(256) NOT NULL,
    content varchar(512),
    biz_type varchar(32),
    biz_id varchar(64),
    read_flag smallint NOT NULL DEFAULT 0,
    channel varchar(32) NOT NULL DEFAULT 'INBOX',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_notif_category CHECK (category IN ('COLLAB', 'APPROVAL', 'MEETING', 'SYSTEM')),
    CONSTRAINT ck_notif_channel CHECK (channel IN ('INBOX', 'IM', 'EMAIL', 'SMS'))
);

CREATE TABLE collab_todo (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    owner_id bigint NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(64),
    title varchar(256) NOT NULL,
    due_at timestamptz,
    priority varchar(8) NOT NULL DEFAULT 'NORMAL',
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    done_at timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_todo_source CHECK (source_type IN ('APPROVAL', 'DOC_MENTION', 'MEETING_ACTION', 'OKR', 'IM')),
    CONSTRAINT ck_todo_priority CHECK (priority IN ('HIGH', 'NORMAL', 'LOW')),
    CONSTRAINT ck_todo_status CHECK (status IN ('OPEN', 'DONE', 'CANCELLED'))
);

-- 扩展：个人布局唯一（文档未定义，保存布局需幂等 upsert）
CREATE UNIQUE INDEX uk_wb_layout_owner ON collab_workbench_layout (tenant_id, owner_id)
    WHERE layout_type = 'PERSONAL' AND deleted = 0;

-- 待办来源唯一（文档 uk_todo_source）
CREATE UNIQUE INDEX uk_todo_source ON collab_todo (tenant_id, owner_id, source_type, source_id)
    WHERE deleted = 0;

COMMENT ON INDEX uk_wb_layout_owner IS '扩展：保证个人工作台布局 upsert 唯一';
