-- E1：发布单与部署记录
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_release (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    release_no varchar(32) NOT NULL,
    -- 偏差标注：文档字段名为 version，与全表公共乐观锁 version 冲突，按总纲兜底更名为 release_version
    release_version varchar(64) NOT NULL,
    env varchar(16) NOT NULL,
    build_id bigint,
    status varchar(16) NOT NULL DEFAULT 'draft',
    approval_instance_id varchar(64),
    window_start timestamptz,
    window_end timestamptz,
    is_rollback smallint NOT NULL DEFAULT 0,
    deployed_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_release_window CHECK (window_start IS NULL OR window_end IS NULL OR window_end > window_start)
);
COMMENT ON TABLE devops_release IS '发布单';
COMMENT ON COLUMN devops_release.env IS '目标环境：staging/prod';
COMMENT ON COLUMN devops_release.status IS '状态：draft/pending_approval/approved/rejected/deploying/deployed/rolled_back';

CREATE TABLE devops_deployment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    release_id bigint NOT NULL,
    env varchar(16) NOT NULL,
    result varchar(16) NOT NULL,
    strategy varchar(16),
    operator_id bigint NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    remark varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_deployment IS '部署记录';
COMMENT ON COLUMN devops_deployment.result IS '结果：success/failed/rolled_back';
COMMENT ON COLUMN devops_deployment.strategy IS '策略：rolling/blue_green/canary';
