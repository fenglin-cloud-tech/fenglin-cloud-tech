-- P3 低代码引擎（HUB-API-1201~1204）——1 张文档表 + 6 张补充表
-- 依据 00 数据库设计 4.45 hub_lowcode_app；其余 6 张为方案“补 6 表”（表单/页面/模型/版本/发布/运行时数据）。
-- 文档缺口兜底：
--   1) 文档只定义 hub_lowcode_app，“补 6 表”仅有用途描述无字段定义，本实现按用途建模并保留 jsonb 扩展位；
--   2) 1202 响应含 processInstanceId，但未定义流程键；约定取 app.data_model.publishProcessKey，
--      缺失时发布不发起审批（processInstanceId 为空）；
--   3) 验收 3 的“数据查询/版本回滚”无接口编号，按扩展接口实现并在服务内标注（非总纲契约）。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_lowcode_app (
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    code          varchar(40)  NOT NULL,
    name          varchar(128) NOT NULL,
    app_type      varchar(16)  NOT NULL DEFAULT 'FORM',
    schema        jsonb,
    data_model    jsonb,
    env           varchar(16)  NOT NULL DEFAULT 'DEV',
    status        varchar(16)  NOT NULL DEFAULT 'DRAFT',
    published_at  timestamptz,
    created_by    bigint,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_app PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_lowcode_app_tenant_code ON hub_lowcode_app (tenant_id, code) WHERE deleted = 0;
CREATE INDEX idx_hub_lowcode_app_env ON hub_lowcode_app (tenant_id, env, status) WHERE deleted = 0;
CREATE TRIGGER trg_hub_lowcode_app_updated_at BEFORE UPDATE ON hub_lowcode_app
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 补充表 1：表单 Schema
CREATE TABLE hub_lowcode_form (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    app_id      bigint       NOT NULL,
    form_key    varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    schema      jsonb,
    sort        integer      NOT NULL DEFAULT 0,
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_form PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_lowcode_form_key ON hub_lowcode_form (app_id, form_key) WHERE deleted = 0;
CREATE INDEX idx_hub_lowcode_form_app ON hub_lowcode_form (tenant_id, app_id) WHERE deleted = 0;
CREATE TRIGGER trg_hub_lowcode_form_updated_at BEFORE UPDATE ON hub_lowcode_form
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 补充表 2：页面 Schema
CREATE TABLE hub_lowcode_page (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    app_id      bigint       NOT NULL,
    page_key    varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    schema      jsonb,
    sort        integer      NOT NULL DEFAULT 0,
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_page PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_lowcode_page_key ON hub_lowcode_page (app_id, page_key) WHERE deleted = 0;
CREATE INDEX idx_hub_lowcode_page_app ON hub_lowcode_page (tenant_id, app_id) WHERE deleted = 0;
CREATE TRIGGER trg_hub_lowcode_page_updated_at BEFORE UPDATE ON hub_lowcode_page
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 补充表 3：数据模型字段
CREATE TABLE hub_lowcode_model (
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    app_id        bigint       NOT NULL,
    field_name    varchar(64)  NOT NULL,
    field_type    varchar(16)  NOT NULL DEFAULT 'string',
    required      smallint     NOT NULL DEFAULT 0,
    max_length    integer,
    default_value varchar(512),
    sort          integer      NOT NULL DEFAULT 0,
    created_by    bigint,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_model PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_lowcode_model_field ON hub_lowcode_model (app_id, field_name) WHERE deleted = 0;
CREATE INDEX idx_hub_lowcode_model_app ON hub_lowcode_model (tenant_id, app_id) WHERE deleted = 0;
CREATE TRIGGER trg_hub_lowcode_model_updated_at BEFORE UPDATE ON hub_lowcode_model
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 补充表 4：版本快照（发布与回滚共用）
CREATE TABLE hub_lowcode_version (
    id                  bigint       NOT NULL,
    tenant_id           bigint       NOT NULL,
    app_id              bigint       NOT NULL,
    version_no          integer      NOT NULL,
    schema              jsonb,
    data_model          jsonb,
    status              varchar(16)  NOT NULL DEFAULT 'PUBLISHED',
    process_instance_id varchar(64),
    published_by        bigint,
    published_at        timestamptz  NOT NULL DEFAULT now(),
    created_by          bigint,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          bigint,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    deleted             smallint     NOT NULL DEFAULT 0,
    version             integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_version PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_lowcode_version_no ON hub_lowcode_version (app_id, version_no) WHERE deleted = 0;
CREATE INDEX idx_hub_lowcode_version_app ON hub_lowcode_version (tenant_id, app_id) WHERE deleted = 0;
CREATE TRIGGER trg_hub_lowcode_version_updated_at BEFORE UPDATE ON hub_lowcode_version
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 补充表 5：环境发布记录（PUBLISH/ROLLBACK）
CREATE TABLE hub_lowcode_publish (
    id                  bigint       NOT NULL,
    tenant_id           bigint       NOT NULL,
    app_id              bigint       NOT NULL,
    version_id          bigint       NOT NULL,
    env                 varchar(16)  NOT NULL,
    action              varchar(16)  NOT NULL DEFAULT 'PUBLISH',
    status              varchar(16)  NOT NULL DEFAULT 'SUCCESS',
    process_instance_id varchar(64),
    remark              varchar(512),
    created_by          bigint,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          bigint,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    deleted             smallint     NOT NULL DEFAULT 0,
    version             integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_publish PRIMARY KEY (id)
);
CREATE INDEX idx_hub_lowcode_publish_app ON hub_lowcode_publish (tenant_id, app_id, created_at DESC);
CREATE TRIGGER trg_hub_lowcode_publish_updated_at BEFORE UPDATE ON hub_lowcode_publish
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 补充表 6：运行时表单数据
CREATE TABLE hub_lowcode_record (
    id           bigint      NOT NULL,
    tenant_id    bigint      NOT NULL,
    app_id       bigint      NOT NULL,
    record_no    varchar(40) NOT NULL,
    idempotency_key varchar(128),
    data         jsonb       NOT NULL,
    status       varchar(16) NOT NULL DEFAULT 'SUBMITTED',
    submitted_by bigint,
    created_by   bigint,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    deleted      smallint    NOT NULL DEFAULT 0,
    version      integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_lowcode_record PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_lowcode_record_no ON hub_lowcode_record (tenant_id, record_no) WHERE deleted = 0;
CREATE INDEX idx_hub_lowcode_record_app ON hub_lowcode_record (tenant_id, app_id, created_at DESC) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_hub_lowcode_record_idem ON hub_lowcode_record (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = 0;
CREATE TRIGGER trg_hub_lowcode_record_updated_at BEFORE UPDATE ON hub_lowcode_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_lowcode_app ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_lowcode_form ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_lowcode_page ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_lowcode_model ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_lowcode_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_lowcode_publish ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_lowcode_record ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_lowcode_app_tenant ON hub_lowcode_app
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_app_platform ON hub_lowcode_app
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_lowcode_form_tenant ON hub_lowcode_form
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_form_platform ON hub_lowcode_form
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_lowcode_page_tenant ON hub_lowcode_page
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_page_platform ON hub_lowcode_page
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_lowcode_model_tenant ON hub_lowcode_model
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_model_platform ON hub_lowcode_model
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_lowcode_version_tenant ON hub_lowcode_version
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_version_platform ON hub_lowcode_version
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_lowcode_publish_tenant ON hub_lowcode_publish
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_publish_platform ON hub_lowcode_publish
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_lowcode_record_tenant ON hub_lowcode_record
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_lowcode_record_platform ON hub_lowcode_record
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_app TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_form TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_page TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_model TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_version TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_publish TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_lowcode_record TO fl_app, fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8171, 'tenant:lowcode:app:design', '低代码应用设计', 'API', 'lowcode', 71, 0),
       (8172, 'tenant:lowcode:app:publish', '低代码应用发布', 'API', 'lowcode', 72, 0),
       (8173, 'biz:lowcode:app:use', '低代码应用使用', 'API', 'lowcode', 73, 0),
       (8174, 'biz:lowcode:app:data:view', '低代码数据查询（扩展接口）', 'API', 'lowcode', 74, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_lowcode_app IS '低代码应用（HUB-API-1201/1202/1203）';
COMMENT ON TABLE hub_lowcode_form IS '低代码表单 Schema（补充表）';
COMMENT ON TABLE hub_lowcode_page IS '低代码页面 Schema（补充表）';
COMMENT ON TABLE hub_lowcode_model IS '低代码数据模型字段（补充表）';
COMMENT ON TABLE hub_lowcode_version IS '低代码版本快照，支持回滚（补充表）';
COMMENT ON TABLE hub_lowcode_publish IS '低代码环境发布记录（补充表）';
COMMENT ON TABLE hub_lowcode_record IS '低代码运行时表单数据（HUB-API-1204）';
