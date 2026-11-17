-- RND-09 方案 A：租户集成配置增加环境维度（原地加列，不新增表）
-- 基线：devops_tenant_config 原为「一租户一条」，无 dev/test/staging/prod 区分；
--       devops_repo 本身已支持一租户多行（多实例），本脚本只补配置侧的环境维度。
--
-- 执行要求（重要）：
--   1) 本脚本含 CREATE/DROP INDEX CONCURRENTLY（非事务语句），与 ALTER TABLE 混在同一脚本，
--      Flyway 必须开启 mixed=true（或 executeInTransaction=false + mixed=true）才能执行；
--      纯 psql 请逐条执行，不要包 BEGIN/COMMIT。
--   2) 若 Flyway 使用事务性锁（postgresql.transactional.lock=true），其历史表事务会挡住
--      CONCURRENTLY 建索引（实测 virtualxid 等待直至卡死）；改为 advisory lock 后不阻塞。
--   3) 语句顺序不可调整：先加列 → 再建新唯一索引 → 最后删旧唯一索引。
--      这样 (tenant_id, env) 的唯一性在切换期间始终有索引兜底，不会出现同环境重复行。
--
-- 回滚顺序（必须遵守，RND-09 代价报告结论）：
--   a) 若已存在同一租户的多环境行，旧唯一索引 uk_tenant_config (tenant_id) **建不回去**：
--      必须先导出并删除非 'prod' 的行（或把数据合并回 prod），再重建唯一索引。
--   b) 应用回滚必须「索引先回、代码后回」：旧版 TenantConfigMapper.upsert 使用
--      ON CONFLICT (tenant_id) WHERE deleted = 0，该冲突目标要求旧唯一索引存在；
--      若先回滚代码再回滚索引，旧版写入会直接报
--      "there is no unique or exclusion constraint matching the ON CONFLICT specification"。
--   回滚脚本（同样非事务模式执行）：
--     DROP INDEX CONCURRENTLY uk_tenant_config_env;
--     CREATE UNIQUE INDEX CONCURRENTLY uk_tenant_config
--         ON devops_tenant_config (tenant_id) WHERE deleted = 0;   -- 存在多环境行时会失败
--     ALTER TABLE devops_tenant_config DROP COLUMN env;            -- 可选，最后执行
SET search_path TO fenglin_devops, public;

ALTER TABLE devops_tenant_config
    ADD COLUMN env varchar(16) NOT NULL DEFAULT 'prod';

COMMENT ON COLUMN devops_tenant_config.env IS '部署环境：dev/test/staging/prod，默认 prod（RND-09 多环境配置）';

ALTER TABLE devops_tenant_config
    ADD CONSTRAINT ck_tenant_config_env CHECK (env IN ('dev', 'test', 'staging', 'prod'));

CREATE UNIQUE INDEX CONCURRENTLY uk_tenant_config_env
    ON devops_tenant_config (tenant_id, env) WHERE deleted = 0;

DROP INDEX CONCURRENTLY uk_tenant_config;
