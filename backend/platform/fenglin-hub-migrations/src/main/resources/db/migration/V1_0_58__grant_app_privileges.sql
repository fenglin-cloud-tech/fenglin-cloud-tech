-- 方案A 第一步：补齐 fl_app 表级授权与 Activiti/雪花序列 USAGE
-- 说明：
--   1) bootstrap_history / 默认分区可能由 bootstrap 脚本创建，统一用 to_regclass 守卫，空库全链也能执行；
--   2) flyway_schema_history 只授 SELECT，应用不得写入迁移历史（历史只能由 Flyway 维护）；
--   3) 表级 GRANT 幂等，可被 Flyway 正常记录。
SET search_path TO fenglin_hub, public;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'collab_bootstrap_history',
        'crm_bootstrap_history',
        'finance_bootstrap_history',
        'intelligence_bootstrap_history',
        'legal_bootstrap_history',
        'rnd_bootstrap_history',
        'service_bootstrap_history',
        'talent_bootstrap_history',
        'hub_api_call_log_default',
        'hub_message_default',
        'hub_metering_default',
        'hub_api_doc'
    ] LOOP
        IF to_regclass('fenglin_hub.' || t) IS NOT NULL THEN
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_hub.%I TO fl_app', t);
        END IF;
    END LOOP;

    IF to_regclass('fenglin_hub.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'GRANT SELECT ON fenglin_hub.flyway_schema_history TO fl_app';
    END IF;
END
$$;

-- workflow 降权后 Activiti 历史/事件表通过序列取号，必须补 USAGE
DO $$
DECLARE
    s text;
BEGIN
    FOREACH s IN ARRAY ARRAY[
        'act_evt_log_log_nr__seq',
        'act_hi_tsk_log_id__seq',
        'hub_snowflake_seq'
    ] LOOP
        IF to_regclass('fenglin_hub.' || s) IS NOT NULL THEN
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE fenglin_hub.%I TO fl_app', s);
        END IF;
    END LOOP;
END
$$;
