-- P2 搜索中心（HUB-API-901~904）
-- 文档缺口兜底：数据库设计未定义索引任务与检索行为表，新增 hub_search_index_task / hub_search_query_log；
--               检索引擎为 deploy/compose 中的 Elasticsearch 8.11，文档索引按 hub_{tenantId}_{indexName} 建物理索引。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_search_index_task (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    index_name  varchar(128) NOT NULL,
    task_type   varchar(16)  NOT NULL DEFAULT 'FULL',
    status      varchar(16)  NOT NULL DEFAULT 'PENDING',
    doc_count   bigint,
    error_msg   varchar(512),
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_search_index_task PRIMARY KEY (id)
);

CREATE INDEX idx_search_index_task_status ON hub_search_index_task (status, created_at);
CREATE INDEX idx_search_index_task_tenant ON hub_search_index_task (tenant_id, index_name, created_at DESC);
CREATE TRIGGER trg_hub_search_index_task_updated_at BEFORE UPDATE ON hub_search_index_task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_search_query_log (
    id             bigint       NOT NULL,
    tenant_id      bigint       NOT NULL,
    user_id        bigint,
    keyword        varchar(256) NOT NULL,
    domains        varchar(256),
    mode           varchar(16),
    hit_count      integer      NOT NULL DEFAULT 0,
    latency_ms     integer,
    clicked_doc_id varchar(64),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    deleted        smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_search_query_log PRIMARY KEY (id)
);

CREATE INDEX idx_search_query_tenant_time ON hub_search_query_log (tenant_id, created_at DESC);
CREATE INDEX idx_search_query_keyword ON hub_search_query_log (tenant_id, keyword);

ALTER TABLE hub_search_index_task ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_search_query_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_search_index_task_tenant ON hub_search_index_task
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_search_query_log_tenant ON hub_search_query_log
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_search_index_task_platform ON hub_search_index_task
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_search_query_log_platform ON hub_search_query_log
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_search_index_task TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_search_query_log TO fl_app, fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8141, 'biz:search:query', '统一检索', 'API', 'search', 41, 0),
       (8142, 'platform:search:index:manage', '搜索索引与报表管理', 'API', 'search', 42, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_search_index_task IS '搜索索引任务（HUB-API-903 文档缺口兜底）';
COMMENT ON TABLE hub_search_query_log IS '检索行为日志（HUB-API-904 分析数据来源，文档缺口兜底）';
