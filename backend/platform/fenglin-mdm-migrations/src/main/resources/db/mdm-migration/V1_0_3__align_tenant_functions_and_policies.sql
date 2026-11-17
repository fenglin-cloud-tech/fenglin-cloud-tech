-- MDM-01：统一租户函数的位置与策略引用，消除“新库重放 / 本机历史库”形态差异
--
-- 背景：
--   V1_0_1 未显式 SET search_path，函数落在执行会话的默认 schema。
--   本机历史库是手工执行（默认 search_path=public），函数位于 public；
--   新机器由 Flyway 执行（defaultSchema=fenglin_mdm），函数位于 fenglin_mdm。
--   两种形态功能等价，但对象位置不同，后续维护与比对会持续产生差异。
--
-- 方案：
--   1) 以 fenglin_mdm 为规范位置，显式创建 current_tenant_id / set_updated_at；
--   2) 在 public 保留同名兼容包装，保证 search_path=public 的旧会话/旧调用仍可解析；
--   3) 三张表的 tenant_isolation 策略统一改为显式 fenglin_mdm 前缀，
--      不再依赖会话 search_path。
--   本迁移幂等：CREATE OR REPLACE + DROP POLICY IF EXISTS，可重复执行。
--
-- 影响与代价：
--   仅新增/替换函数与重建 3 条策略；DROP/CREATE POLICY 会对三张表各持一次短暂的
--   ACCESS EXCLUSIVE 锁（毫秒级，无数据重写），不涉及业务数据。
--
-- 回滚（应用版本前先执行）：
--   将三张表的策略重建为未限定写法，例如：
--     DROP POLICY IF EXISTS tenant_isolation ON fenglin_mdm.mdm_business_partner;
--     CREATE POLICY tenant_isolation ON fenglin_mdm.mdm_business_partner
--         USING (tenant_id = current_tenant_id())
--         WITH CHECK (tenant_id = current_tenant_id());
--   函数无需删除：fenglin_mdm 侧是 V1_0_1 在新库本来就有的对象，public 侧是历史库本来就有的对象，
--   两侧同名同义，保留不会改变行为；不建议 DROP，否则新库回滚后会丢失 V1_0_1 建立的能力。
--   只有在确认没有任何会话以 search_path=public 调用后，才可单独 DROP public 侧包装。
SET search_path TO fenglin_mdm, public;

-- 1) 规范位置：fenglin_mdm
CREATE OR REPLACE FUNCTION fenglin_mdm.current_tenant_id()
RETURNS bigint
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::bigint;
$$;

CREATE OR REPLACE FUNCTION fenglin_mdm.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- 2) 兼容层：public 同名包装
CREATE OR REPLACE FUNCTION public.current_tenant_id()
RETURNS bigint
LANGUAGE sql
STABLE
AS $$
    SELECT fenglin_mdm.current_tenant_id();
$$;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- 3) 策略统一显式 schema 前缀，避免依赖 search_path
DROP POLICY IF EXISTS tenant_isolation ON fenglin_mdm.mdm_business_partner;
CREATE POLICY tenant_isolation ON fenglin_mdm.mdm_business_partner
    USING (tenant_id = fenglin_mdm.current_tenant_id())
    WITH CHECK (tenant_id = fenglin_mdm.current_tenant_id());

DROP POLICY IF EXISTS tenant_isolation ON fenglin_mdm.mdm_partner_merge;
CREATE POLICY tenant_isolation ON fenglin_mdm.mdm_partner_merge
    USING (tenant_id = fenglin_mdm.current_tenant_id())
    WITH CHECK (tenant_id = fenglin_mdm.current_tenant_id());

DROP POLICY IF EXISTS tenant_isolation ON fenglin_mdm.mdm_partner_alias;
CREATE POLICY tenant_isolation ON fenglin_mdm.mdm_partner_alias
    USING (tenant_id = fenglin_mdm.current_tenant_id())
    WITH CHECK (tenant_id = fenglin_mdm.current_tenant_id());
