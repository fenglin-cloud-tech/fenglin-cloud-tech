-- 修正：data_level 需容纳 CONFIDENTIAL（12 字符），原 varchar(8) 在写入时溢出。
-- 说明：07 数据库设计未给出字段长度，此处按字典值最长项放宽至 varchar(24)，与规则/策略注释一致。
ALTER TABLE legal_sensitive_rule ALTER COLUMN data_level TYPE varchar(24);
ALTER TABLE legal_dlp_policy ALTER COLUMN data_level TYPE varchar(24);
ALTER TABLE legal_dlp_event ALTER COLUMN data_level TYPE varchar(24);
