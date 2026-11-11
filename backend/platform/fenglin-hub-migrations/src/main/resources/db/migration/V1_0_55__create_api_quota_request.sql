-- OPEN-03：开放平台配额自助申请与审批（单表独立迁移）
-- 申请单只做状态流转（PENDING → APPROVED/REJECTED）；批准时把申请值写入 hub_api_quota。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_api_quota_request (
    id               bigint       NOT NULL,
    tenant_id        bigint       NOT NULL,
    app_id           bigint       NOT NULL,
    api_path         varchar(256) NOT NULL,
    requested_qps    integer,
    requested_daily  bigint,
    reason           varchar(512) NOT NULL,
    status           varchar(16)  NOT NULL DEFAULT 'PENDING',
    applied_by       bigint       NOT NULL,
    decided_by       bigint,
    decided_at       timestamptz,
    decision_comment varchar(512),
    created_by       bigint,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       bigint,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted          smallint     NOT NULL DEFAULT 0,
    version          integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_api_quota_request PRIMARY KEY (id),
    CONSTRAINT ck_quota_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

-- 同一应用同一接口只允许一条待审批申请，避免重复提单
CREATE UNIQUE INDEX uk_quota_request_pending
    ON hub_api_quota_request (tenant_id, app_id, api_path)
    WHERE status = 'PENDING' AND deleted = 0;
CREATE INDEX idx_quota_request_applicant
    ON hub_api_quota_request (tenant_id, applied_by, created_at DESC)
    WHERE deleted = 0;
CREATE INDEX idx_quota_request_status
    ON hub_api_quota_request (tenant_id, status, created_at DESC)
    WHERE deleted = 0;

CREATE TRIGGER trg_hub_api_quota_request_updated_at BEFORE UPDATE ON hub_api_quota_request
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_api_quota_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_api_quota_request_tenant ON hub_api_quota_request
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_api_quota_request_platform ON hub_api_quota_request
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_api_quota_request TO fl_app, fl_platform_role;

-- 权限点：开发者自助申请 / 平台审批
INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8196, 'biz:openapi:quota:apply', '开发者门户配额自助申请', 'API', 'openapi', 66, 0),
       (8197, 'platform:openapi:quota:approve', '开放平台配额申请审批', 'API', 'openapi', 67, 0)
ON CONFLICT (code) DO NOTHING;

-- 存量租户：TENANT_ADMIN 获得自助申请权限
INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id) AS id,
       r.tenant_id,
       r.id,
       'biz:openapi:quota:apply',
       now(),
       now(),
       0,
       0
FROM hub_role r
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM hub_role_permission) base
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;

-- 存量租户：PLATFORM_ADMIN 获得审批权限
INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id) AS id,
       r.tenant_id,
       r.id,
       'platform:openapi:quota:approve',
       now(),
       now(),
       0,
       0
FROM hub_role r
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM hub_role_permission) base
WHERE r.code = 'PLATFORM_ADMIN' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;

COMMENT ON TABLE hub_api_quota_request IS '开发平台配额自助申请单（OPEN-03），批准后写入 hub_api_quota';
