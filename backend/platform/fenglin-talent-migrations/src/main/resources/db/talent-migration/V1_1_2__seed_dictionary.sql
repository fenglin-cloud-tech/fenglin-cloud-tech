-- 字典初始化（占位）
-- 文档基线：03_丰麟云才_03 第 8 节 V1_1_2__seed_dictionary.sql（职级、技能分类、问卷维度字典）
-- 偏差标注：
--   1) 职级（talent_grade）、技能分类（talent_skill_tag.category）、问卷维度（talent_engagement_survey.dimensions）
--      在 fenglin_talent 库中均已是业务表/JSONB 结构，字典值本身不需要在业务库再建字典表；
--   2) 为避免跨库耦合（业务库写平台库），talent_ 前缀的枚举字典统一由 fenglin-talent-bootstrap
--      写入平台库 fenglin_hub.hub_dict_type / hub_dict_item，见
--      db/talent-bootstrap/V1_0_1__seed_talent_prerequisites.sql；
--   3) 因此本脚本只保留版本占位，不建表、不插数据，保证 Flyway 版本序列与文档清单一致。
SET search_path TO fenglin_talent, public;

-- 无 DDL / DML：枚举字典由平台库 bootstrap 模块负责（见文件头说明）。
