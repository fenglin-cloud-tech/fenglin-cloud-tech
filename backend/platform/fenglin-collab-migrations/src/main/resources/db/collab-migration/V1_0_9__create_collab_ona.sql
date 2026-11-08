-- ONA 域：关系图谱、关系边、指标、快照
-- 偏差标注：文档要求 collab_ona_edge 按快照周期分区，首版使用普通表 + (graph_id) 索引；
--           ONA 快照生成任务属收口阶段，主链仅建表。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_ona_graph (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    scope_type varchar(16) NOT NULL DEFAULT 'TENANT',
    scope_dept_id bigint,
    period_start date NOT NULL,
    period_end date NOT NULL,
    node_count int NOT NULL DEFAULT 0,
    edge_count int NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'BUILDING',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_ona_graph_status CHECK (status IN ('BUILDING', 'READY', 'FAILED'))
);

CREATE TABLE collab_ona_edge (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    graph_id uuid NOT NULL REFERENCES collab_ona_graph (id),
    source_type varchar(16) NOT NULL DEFAULT 'USER',
    source_id bigint NOT NULL,
    target_type varchar(16) NOT NULL DEFAULT 'USER',
    target_id bigint NOT NULL,
    weight numeric(12, 4) NOT NULL DEFAULT 0,
    im_count int NOT NULL DEFAULT 0,
    doc_count int NOT NULL DEFAULT 0,
    meeting_count int NOT NULL DEFAULT 0,
    approval_count int NOT NULL DEFAULT 0,
    okr_count int NOT NULL DEFAULT 0,
    cross_dept smallint NOT NULL DEFAULT 0,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_ona_edge UNIQUE (tenant_id, graph_id, source_type, source_id, target_type, target_id)
);

CREATE TABLE collab_ona_metric (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    snapshot_id uuid NOT NULL,
    node_type varchar(16) NOT NULL DEFAULT 'USER',
    node_id bigint NOT NULL,
    degree_centrality numeric(10, 6),
    betweenness numeric(10, 6),
    closeness numeric(10, 6),
    clustering_coef numeric(10, 6),
    structural_hole numeric(10, 6),
    is_key_connector smallint NOT NULL DEFAULT 0,
    cross_dept_ratio numeric(5, 4),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_ona_metric UNIQUE (tenant_id, snapshot_id, node_type, node_id)
);

CREATE TABLE collab_ona_snapshot (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    period_type varchar(8) NOT NULL DEFAULT 'MONTH',
    period_key varchar(16) NOT NULL,
    graph_id uuid NOT NULL,
    network_density numeric(10, 6),
    avg_degree numeric(10, 4),
    cross_dept_ratio numeric(5, 4),
    key_connector_count int NOT NULL DEFAULT 0,
    silo_count int NOT NULL DEFAULT 0,
    generated_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_ona_period_type CHECK (period_type IN ('WEEK', 'MONTH', 'QUARTER')),
    CONSTRAINT uk_ona_snapshot UNIQUE (tenant_id, period_type, period_key, graph_id)
);

CREATE TABLE collab_ona_config (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    weight_im numeric(5, 4) NOT NULL DEFAULT 0.30,
    weight_doc numeric(5, 4) NOT NULL DEFAULT 0.25,
    weight_meeting numeric(5, 4) NOT NULL DEFAULT 0.25,
    weight_approval numeric(5, 4) NOT NULL DEFAULT 0.10,
    weight_okr numeric(5, 4) NOT NULL DEFAULT 0.10,
    anonymity_threshold int NOT NULL DEFAULT 5,
    key_connector_top_ratio numeric(5, 4) NOT NULL DEFAULT 0.05,
    profile_visible_scope varchar(16) NOT NULL DEFAULT 'SELF',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_ona_config UNIQUE (tenant_id)
);

COMMENT ON TABLE collab_ona_config IS '扩展：文档未定义 ONA 度量配置表，按 API-ONA-050 最小语义补齐';
