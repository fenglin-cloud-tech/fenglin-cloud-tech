-- 生产/DBA 预创建角色（与 V1_0_0 幂等块保持一致）。
-- 应用登录账号请由 DBA 单独创建，并仅授予 fl_app 成员关系；不得加入 fl_platform_role。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_app') THEN
        CREATE ROLE fl_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_platform_role') THEN
        CREATE ROLE fl_platform_role NOLOGIN;
    END IF;
END
$$;
