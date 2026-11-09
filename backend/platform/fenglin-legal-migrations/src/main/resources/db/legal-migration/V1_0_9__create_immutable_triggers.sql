-- 云法触发器：全部表 updated_at 自动维护；11 张不可删表禁止 DELETE 与软删。
-- 不可删表清单（文档 6.2）：legal_contract_version / legal_contract_clause / legal_signatory /
--   legal_approval_record / legal_seal_record / legal_sign_record / legal_evidence /
--   legal_dlp_event / legal_dlp_event_handle / legal_watermark_record / legal_dlp_audit_log。
SET search_path TO fenglin_legal, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
        WHERE schemaname = 'fenglin_legal'
          AND tablename LIKE 'legal\_%' ESCAPE '\'
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON fenglin_legal.%I '
                       'FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                       'trg_' || t || '_updated', t);
    END LOOP;
END
$$;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'legal_contract_version', 'legal_contract_clause', 'legal_signatory',
        'legal_approval_record', 'legal_seal_record', 'legal_sign_record',
        'legal_evidence', 'legal_dlp_event', 'legal_dlp_event_handle',
        'legal_watermark_record', 'legal_dlp_audit_log'
    ]
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE DELETE ON fenglin_legal.%I '
                       'FOR EACH ROW EXECUTE FUNCTION fl_block_delete()',
                       'trg_' || t || '_immutable_del', t);
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OF deleted ON fenglin_legal.%I '
                       'FOR EACH ROW WHEN (NEW.deleted <> 0) EXECUTE FUNCTION fl_block_delete()',
                       'trg_' || t || '_immutable_soft', t);
    END LOOP;
END
$$;
