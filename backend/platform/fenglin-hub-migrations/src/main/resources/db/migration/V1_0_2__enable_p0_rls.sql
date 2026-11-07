-- 平台租户表 RLS：租户角色仅能访问 app.tenant_id 对应数据；平台运营角色可跨租户

SET search_path TO fenglin_hub, public;

ALTER TABLE hub_subscription ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_subscription_tenant ON hub_subscription
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_subscription_platform ON hub_subscription
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_metering ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_metering_tenant ON hub_metering
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_metering_platform ON hub_metering
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_metering_default ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_metering_default_tenant ON hub_metering_default
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_metering_default_platform ON hub_metering_default
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_usage_daily ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_usage_daily_tenant ON hub_usage_daily
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_usage_daily_platform ON hub_usage_daily
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_bill ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_bill_tenant ON hub_bill
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_bill_platform ON hub_bill
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_bill_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_bill_item_tenant ON hub_bill_item
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_bill_item_platform ON hub_bill_item
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_license ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_license_tenant ON hub_license
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_license_platform ON hub_license
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
