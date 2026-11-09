-- E1：看板与看板列
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_board (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    board_type varchar(16) NOT NULL DEFAULT 'scrum',
    sprint_id bigint,
    filter_json jsonb,
    is_default smallint NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_board IS '看板';
COMMENT ON COLUMN devops_board.board_type IS '类型：scrum/kanban';

CREATE TABLE devops_board_column (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    board_id bigint NOT NULL,
    column_name varchar(64) NOT NULL,
    status_codes jsonb NOT NULL DEFAULT '[]'::jsonb,
    wip_limit int,
    sort_no int NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_board_column_wip CHECK (wip_limit IS NULL OR wip_limit > 0)
);
COMMENT ON TABLE devops_board_column IS '看板列';
COMMENT ON COLUMN devops_board_column.wip_limit IS 'WIP 限制（空=不限）';
