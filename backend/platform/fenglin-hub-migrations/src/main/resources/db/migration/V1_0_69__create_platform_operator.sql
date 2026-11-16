-- OPEN-06 一期：平台运维白名单（跨租户只读的平台身份来源）。
-- 全局表：不带 tenant_id、不启用租户 RLS；仅认证服务与平台角色可读写，业务服务不授权。
-- 不自动把存量 PLATFORM_ADMIN 升级为平台运维，初始白名单由 DBA 手工插入。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_platform_operator (
    id             bigint      NOT NULL,
    user_id        bigint      NOT NULL,
    home_tenant_id bigint      NOT NULL,
    status         varchar(16) NOT NULL DEFAULT 'ACTIVE',
    expire_at      timestamptz,
    granted_by     bigint,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deleted        smallint    NOT NULL DEFAULT 0,
    version        integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_platform_operator PRIMARY KEY (id),
    CONSTRAINT ck_hub_platform_operator_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uk_hub_platform_operator_user
    ON hub_platform_operator (user_id) WHERE deleted = 0;

CREATE TRIGGER trg_hub_platform_operator_updated_at
    BEFORE UPDATE ON hub_platform_operator
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE ON hub_platform_operator TO fl_app, fl_platform_role;

COMMENT ON TABLE hub_platform_operator IS '平台运维白名单（OPEN-06）：全局身份，跨租户只读切换的唯一授权来源';
COMMENT ON COLUMN hub_platform_operator.home_tenant_id IS '运维人员所属租户；切换令牌记录 act_home_tenant_id';

-- 平台切换权限码：仅由认证服务在解析白名单用户权限时动态追加，不授予租户角色。
INSERT INTO hub_permission (id, code, name, type, domain, parent_code, sort, created_at, deleted, version)
SELECT 8198, 'platform:tenant:switch', '跨租户只读切换', 'API', 'platform', NULL, 98, now(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM hub_permission WHERE code = 'platform:tenant:switch');
