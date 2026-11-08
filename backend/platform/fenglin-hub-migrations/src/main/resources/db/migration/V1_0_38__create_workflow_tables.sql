-- P2 工作流中心：定义/实例/任务/表单（数据库设计 4.28~4.31）
-- 文档缺口兜底：hub_wf_callback 已存在（C-12），新增 hub_wf_callback_log 承载回调幂等键与投递结果。
-- 文档冲突兜底：数据库设计 4.28 将 process_key 标为 UNIQUE(tenant)，但 5.4 要求按版本发布；
--               实际唯一键取 (tenant_id, process_key, def_version)，同一 process_key 仅一个 PUBLISHED。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_wf_definition (
    id           bigint       NOT NULL,
    tenant_id    bigint       NOT NULL,
    process_key  varchar(128) NOT NULL,
    name         varchar(128) NOT NULL,
    category     varchar(64),
    def_version  integer      NOT NULL,
    bpmn_xml     text         NOT NULL,
    form_key     varchar(128),
    status       varchar(16)  NOT NULL DEFAULT 'DRAFT',
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_wf_definition PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_hub_wf_definition
    ON hub_wf_definition (tenant_id, process_key, def_version) WHERE deleted = 0;
CREATE INDEX idx_wf_def_category ON hub_wf_definition (tenant_id, category, status);
CREATE TRIGGER trg_hub_wf_definition_updated_at BEFORE UPDATE ON hub_wf_definition
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_wf_instance (
    id                   bigint       NOT NULL,
    tenant_id            bigint       NOT NULL,
    instance_no          varchar(40)  NOT NULL,
    process_key          varchar(128) NOT NULL,
    def_version          integer      NOT NULL,
    flowable_instance_id varchar(64),
    business_key         varchar(128),
    business_type        varchar(64),
    title                varchar(256),
    initiator_id         bigint       NOT NULL,
    status               varchar(16)  NOT NULL DEFAULT 'RUNNING',
    started_at           timestamptz  NOT NULL DEFAULT now(),
    ended_at             timestamptz,
    result               varchar(16),
    created_by           bigint,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_by           bigint,
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    deleted              smallint     NOT NULL DEFAULT 0,
    version              integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_wf_instance PRIMARY KEY (id),
    CONSTRAINT uk_hub_wf_instance_no UNIQUE (tenant_id, instance_no)
);

CREATE INDEX idx_inst_biz ON hub_wf_instance (tenant_id, business_type, business_key);
CREATE INDEX idx_inst_init ON hub_wf_instance (tenant_id, initiator_id, status);
CREATE INDEX idx_inst_process ON hub_wf_instance (tenant_id, process_key, status);
CREATE TRIGGER trg_hub_wf_instance_updated_at BEFORE UPDATE ON hub_wf_instance
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_wf_task (
    id               bigint       NOT NULL,
    tenant_id        bigint       NOT NULL,
    instance_id      bigint       NOT NULL,
    flowable_task_id varchar(64),
    node_key         varchar(128) NOT NULL,
    node_name        varchar(128),
    assignee_id      bigint,
    candidate_ids    varchar(512),
    candidate_groups varchar(512),
    task_type        varchar(16)  NOT NULL DEFAULT 'APPROVE',
    action           varchar(16),
    comment          text,
    status           varchar(16)  NOT NULL DEFAULT 'PENDING',
    due_at           timestamptz,
    handled_at       timestamptz,
    created_by       bigint,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       bigint,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted          smallint     NOT NULL DEFAULT 0,
    version          integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_wf_task PRIMARY KEY (id)
);

CREATE INDEX idx_task_assignee ON hub_wf_task (tenant_id, assignee_id, status);
CREATE INDEX idx_task_due ON hub_wf_task (tenant_id, status, due_at);
CREATE INDEX idx_task_instance ON hub_wf_task (tenant_id, instance_id);
CREATE TRIGGER trg_hub_wf_task_updated_at BEFORE UPDATE ON hub_wf_task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_wf_form_data (
    id           bigint       NOT NULL,
    tenant_id    bigint       NOT NULL,
    instance_id  bigint       NOT NULL,
    form_key     varchar(128),
    form_data    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    form_version integer      NOT NULL DEFAULT 1,
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_wf_form_data PRIMARY KEY (id)
);

CREATE INDEX idx_wf_form_instance ON hub_wf_form_data (tenant_id, instance_id);
CREATE INDEX idx_wf_form_data_gin ON hub_wf_form_data USING GIN (form_data);
CREATE TRIGGER trg_hub_wf_form_data_updated_at BEFORE UPDATE ON hub_wf_form_data
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_wf_callback_log (
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    instance_id   bigint       NOT NULL,
    node_key      varchar(128) NOT NULL,
    action        varchar(32)  NOT NULL,
    callback_url  varchar(512) NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'PENDING',
    attempts      integer      NOT NULL DEFAULT 0,
    http_status   integer,
    error_msg     varchar(512),
    created_by    bigint,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_wf_callback_log PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_hub_wf_callback_log
    ON hub_wf_callback_log (tenant_id, instance_id, node_key, action) WHERE deleted = 0;
CREATE INDEX idx_wf_callback_log_status ON hub_wf_callback_log (tenant_id, status, created_at);
CREATE TRIGGER trg_hub_wf_callback_log_updated_at BEFORE UPDATE ON hub_wf_callback_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_wf_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_wf_instance ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_wf_task ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_wf_form_data ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_wf_callback_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_wf_definition_tenant ON hub_wf_definition
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_wf_instance_tenant ON hub_wf_instance
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_wf_task_tenant ON hub_wf_task
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_wf_form_data_tenant ON hub_wf_form_data
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_wf_callback_log_tenant ON hub_wf_callback_log
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

CREATE POLICY p_hub_wf_definition_platform ON hub_wf_definition
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_wf_instance_platform ON hub_wf_instance
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_wf_task_platform ON hub_wf_task
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_wf_form_data_platform ON hub_wf_form_data
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_wf_callback_log_platform ON hub_wf_callback_log
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_wf_definition TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_wf_instance TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_wf_task TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_wf_form_data TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_wf_callback_log TO fl_app, fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8421, 'tenant:workflow:def:manage', '流程定义管理', 'API', 'workflow', 1, 0),
       (8422, 'tenant:workflow:monitor:view', '流程监控查看', 'API', 'workflow', 2, 0),
       (8423, 'platform:workflow:engine:manage', '工作流引擎管理', 'API', 'workflow', 3, 0),
       (8424, 'biz:workflow:instance:start', '发起流程实例', 'API', 'workflow', 4, 0),
       (8425, 'biz:workflow:instance:view', '查看流程实例', 'API', 'workflow', 5, 0),
       (8426, 'biz:workflow:task:handle', '审批任务处理', 'API', 'workflow', 6, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_wf_definition IS '工作流定义（按 tenant+process_key+def_version 版本化）';
COMMENT ON TABLE hub_wf_instance IS '工作流实例';
COMMENT ON TABLE hub_wf_task IS '工作流任务';
COMMENT ON TABLE hub_wf_form_data IS '流程表单数据';
COMMENT ON TABLE hub_wf_callback_log IS '流程回调幂等与投递记录（C-13 文档缺口兜底）';
