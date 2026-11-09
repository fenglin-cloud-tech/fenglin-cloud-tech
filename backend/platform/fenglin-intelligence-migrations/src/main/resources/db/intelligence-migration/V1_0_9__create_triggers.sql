-- 触发器：updated_at 自动维护（全部表）；不可删表防删除/防软删
SET search_path TO fenglin_intelligence, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
        WHERE schemaname = 'fenglin_intelligence' AND tablename LIKE 'ai\_%' ESCAPE '\'
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON fenglin_intelligence.%I '
                       'FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                       'trg_ai_' || t || '_updated', t);
    END LOOP;
END
$$;

-- 留痕表：检索日志、消息、引用、智能体执行、问答反馈、洞察结果、归因分析
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'ai_search_log', 'ai_message', 'ai_message_citation', 'ai_agent_execution',
        'ai_qa_feedback', 'ai_insight_result', 'ai_attribution_analysis'
    ]
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE DELETE ON fenglin_intelligence.%I '
                       'FOR EACH ROW EXECUTE FUNCTION fl_block_delete()',
                       'trg_ai_' || t || '_immutable_del', t);
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON fenglin_intelligence.%I '
                       'FOR EACH ROW WHEN (NEW.deleted IS DISTINCT FROM OLD.deleted) '
                       'EXECUTE FUNCTION fl_block_delete()',
                       'trg_ai_' || t || '_immutable_soft', t);
    END LOOP;
END
$$;
