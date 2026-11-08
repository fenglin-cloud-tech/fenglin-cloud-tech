-- P1 RLS：租户级 IAM/认证表按 app.tenant_id 隔离；平台全局表（hub_permission/hub_menu/hub_dict_type）不启用

SET search_path TO fenglin_hub, public;

-- 用户与组织
ALTER TABLE hub_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_user_tenant ON hub_user
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_user_platform ON hub_user
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_account ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_account_tenant ON hub_account
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_account_platform ON hub_account
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_org ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_org_tenant ON hub_org
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_org_platform ON hub_org
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_user_org ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_user_org_tenant ON hub_user_org
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_user_org_platform ON hub_user_org
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_position ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_position_tenant ON hub_position
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_position_platform ON hub_position
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_user_position ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_user_position_tenant ON hub_user_position
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_user_position_platform ON hub_user_position
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

-- 角色与权限
ALTER TABLE hub_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_role_tenant ON hub_role
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_role_platform ON hub_role
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_role_permission ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_role_permission_tenant ON hub_role_permission
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_role_permission_platform ON hub_role_permission
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_user_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_user_role_tenant ON hub_user_role
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_user_role_platform ON hub_user_role
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

-- 数据/字段权限
ALTER TABLE hub_data_scope ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_data_scope_tenant ON hub_data_scope
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_data_scope_platform ON hub_data_scope
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_field_permission ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_field_permission_tenant ON hub_field_permission
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_field_permission_platform ON hub_field_permission
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

-- 字典与参数（hub_dict_type 为全局表，不启用）
ALTER TABLE hub_dict_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_dict_item_tenant ON hub_dict_item
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_dict_item_platform ON hub_dict_item
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_param ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_param_tenant ON hub_param
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_param_platform ON hub_param
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

-- 认证域
ALTER TABLE hub_login_session ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_login_session_tenant ON hub_login_session
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_login_session_platform ON hub_login_session
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_sso_config ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_sso_config_tenant ON hub_sso_config
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_sso_config_platform ON hub_sso_config
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_auth_token ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_auth_token_tenant ON hub_auth_token
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_auth_token_platform ON hub_auth_token
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_auth_revocation ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_auth_revocation_tenant ON hub_auth_revocation
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_auth_revocation_platform ON hub_auth_revocation
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
