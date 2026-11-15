-- 业务编号序列与存量对账（可重复执行）
-- 背景：种子数据直接写入带编号的业务行，未调用 nextval，导致序列 is_called=false、
--       首次 nextval 返回 1，与存量 ...000001 撞号，表现为新建 500 / duplicate key。
-- 修法：把序列推进到「当前值」与「表内最大编号」中的较大者，之后 nextval 必然大于存量。
SELECT setval('fenglin_crm.crm_quotation_no_seq',
               GREATEST((SELECT last_value FROM fenglin_crm.crm_quotation_no_seq),
                        (SELECT coalesce(max(right((regexp_match(quotation_no, '([0-9]+)$'))[1], 6)::bigint), 0)
                           FROM fenglin_crm.crm_quotation WHERE deleted = 0)),
               true);

SELECT setval('fenglin_crm.crm_contract_no_seq',
               GREATEST((SELECT last_value FROM fenglin_crm.crm_contract_no_seq),
                        (SELECT coalesce(max(right((regexp_match(contract_no, '([0-9]+)$'))[1], 6)::bigint), 0)
                           FROM fenglin_crm.crm_service_contract WHERE deleted = 0)),
               true);

SELECT setval('fenglin_crm.crm_customer_no_seq',
               GREATEST((SELECT last_value FROM fenglin_crm.crm_customer_no_seq),
                        (SELECT coalesce(max(right((regexp_match(customer_no, '([0-9]+)$'))[1], 6)::bigint), 0)
                           FROM fenglin_crm.crm_customer WHERE deleted = 0)),
               true);

SELECT setval('fenglin_crm.crm_biz_no_seq',
               GREATEST((SELECT last_value FROM fenglin_crm.crm_biz_no_seq),
                        (SELECT coalesce(max(right((regexp_match(lead_no, '([0-9]+)$'))[1], 6)::bigint), 0)
                           FROM fenglin_crm.crm_lead WHERE deleted = 0),
                        (SELECT coalesce(max(right((regexp_match(opportunity_no, '([0-9]+)$'))[1], 6)::bigint), 0)
                           FROM fenglin_crm.crm_opportunity WHERE deleted = 0)),
               true);
