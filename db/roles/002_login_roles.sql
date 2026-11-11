-- 方案A 第二步：为 9 个业务库各创建一个最小权限登录角色（集群级对象，只需执行一次）。
--
-- 设计约束：
--   1) 密码只从环境变量读取，不写入本文件、不写入 Flyway 迁移、不写入 compose 明文；
--   2) 登录角色只 IN ROLE 对应库的 fl_*_app，绝不加入 fl_platform_role；
--   3) 可重复执行：角色不存在则创建，已存在则校正属性并轮换密码；
--   4) 依赖 psql >= 15（使用 \getenv）。执行方式见 apply-login-roles.ps1。
--
-- 角色映射（登录角色 -> 应用权限角色 / 默认 schema）：
--   fl_login_hub          -> fl_app               / fenglin_hub
--   fl_login_collab       -> fl_collab_app        / fenglin_collab
--   fl_login_legal        -> fl_legal_app         / fenglin_legal
--   fl_login_talent       -> fl_talent_app        / fenglin_talent
--   fl_login_finance      -> fl_finance_app       / fenglin_finance
--   fl_login_crm          -> fl_crm_app           / fenglin_crm
--   fl_login_devops       -> fl_devops_app        / fenglin_devops
--   fl_login_intelligence -> fl_intelligence_app  / fenglin_intelligence
--   fl_login_service      -> fl_service_app       / fenglin_service

\set ON_ERROR_STOP on

\getenv pwd_hub          FL_LOGIN_HUB_PASSWORD
\getenv pwd_collab       FL_LOGIN_COLLAB_PASSWORD
\getenv pwd_legal        FL_LOGIN_LEGAL_PASSWORD
\getenv pwd_talent       FL_LOGIN_TALENT_PASSWORD
\getenv pwd_finance      FL_LOGIN_FINANCE_PASSWORD
\getenv pwd_crm          FL_LOGIN_CRM_PASSWORD
\getenv pwd_devops       FL_LOGIN_DEVOPS_PASSWORD
\getenv pwd_intelligence FL_LOGIN_INTELLIGENCE_PASSWORD
\getenv pwd_service      FL_LOGIN_SERVICE_PASSWORD

\if :{?pwd_hub}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_HUB_PASSWORD'; END $$;
\endif
\if :{?pwd_collab}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_COLLAB_PASSWORD'; END $$;
\endif
\if :{?pwd_legal}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_LEGAL_PASSWORD'; END $$;
\endif
\if :{?pwd_talent}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_TALENT_PASSWORD'; END $$;
\endif
\if :{?pwd_finance}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_FINANCE_PASSWORD'; END $$;
\endif
\if :{?pwd_crm}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_CRM_PASSWORD'; END $$;
\endif
\if :{?pwd_devops}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_DEVOPS_PASSWORD'; END $$;
\endif
\if :{?pwd_intelligence}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_INTELLIGENCE_PASSWORD'; END $$;
\endif
\if :{?pwd_service}
\else
DO $$ BEGIN RAISE EXCEPTION '缺少环境变量 FL_LOGIN_SERVICE_PASSWORD'; END $$;
\endif

-- 1) 创建缺失的登录角色（属性先落地，密码在下一步统一设置）
SELECT format(
         'CREATE ROLE %I LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
         r.login_role)
FROM (VALUES
        ('fl_login_hub'),
        ('fl_login_collab'),
        ('fl_login_legal'),
        ('fl_login_talent'),
        ('fl_login_finance'),
        ('fl_login_crm'),
        ('fl_login_devops'),
        ('fl_login_intelligence'),
        ('fl_login_service')
     ) AS r(login_role)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles p WHERE p.rolname = r.login_role)
\gexec

-- 2) 校正属性（已有角色同样强制收敛为最小权限）
SELECT format(
         'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION',
         r.login_role)
FROM (VALUES
        ('fl_login_hub'),
        ('fl_login_collab'),
        ('fl_login_legal'),
        ('fl_login_talent'),
        ('fl_login_finance'),
        ('fl_login_crm'),
        ('fl_login_devops'),
        ('fl_login_intelligence'),
        ('fl_login_service')
     ) AS r(login_role)
\gexec

-- 3) 设置/轮换密码（唯一的密码落点；值来自环境变量，服务端默认 SCRAM-SHA-256 加密存储）
SELECT format('ALTER ROLE %I PASSWORD %L', r.login_role, r.pwd)
FROM (VALUES
        ('fl_login_hub',          :'pwd_hub'),
        ('fl_login_collab',       :'pwd_collab'),
        ('fl_login_legal',        :'pwd_legal'),
        ('fl_login_talent',       :'pwd_talent'),
        ('fl_login_finance',      :'pwd_finance'),
        ('fl_login_crm',          :'pwd_crm'),
        ('fl_login_devops',       :'pwd_devops'),
        ('fl_login_intelligence', :'pwd_intelligence'),
        ('fl_login_service',      :'pwd_service')
     ) AS r(login_role, pwd)
\gexec

-- 4) 授予对应应用角色成员关系（可重复执行，不报错）
SELECT format(
         'GRANT %I TO %I',
         CASE WHEN p.rolname = 'fl_login_hub' THEN 'fl_app'
              ELSE 'fl_' || replace(p.rolname, 'fl_login_', '') || '_app' END,
         p.rolname)
FROM pg_roles p
WHERE p.rolname LIKE 'fl\_login\_%' ESCAPE '\'
  AND NOT pg_has_role(p.rolname,
                      CASE WHEN p.rolname = 'fl_login_hub' THEN 'fl_app'
                           ELSE 'fl_' || replace(p.rolname, 'fl_login_', '') || '_app' END,
                      'MEMBER')
ORDER BY p.rolname
\gexec

-- 5) 确保不持有平台角色（仅对确实持有成员关系的角色执行，避免无意义告警）
SELECT format('REVOKE %I FROM %I', 'fl_platform_role', p.rolname)
FROM pg_roles p
WHERE p.rolname LIKE 'fl\_login\_%' ESCAPE '\'
  AND pg_has_role(p.rolname, 'fl_platform_role', 'MEMBER')
ORDER BY p.rolname
\gexec

-- 6) 默认 search_path 指向本库 schema，避免未限定表名落到 public
SELECT format(
         'ALTER ROLE %I SET search_path TO %I, public',
         p.rolname,
         CASE WHEN p.rolname = 'fl_login_hub' THEN 'fenglin_hub'
              ELSE 'fenglin_' || replace(p.rolname, 'fl_login_', '') END)
FROM pg_roles p
WHERE p.rolname LIKE 'fl\_login\_%' ESCAPE '\'
ORDER BY p.rolname
\gexec

-- 7) 结果自检：属性、成员关系、默认 search_path
SELECT p.rolname,
       p.rolcanlogin   AS login,
       p.rolsuper      AS superuser,
       p.rolbypassrls  AS bypassrls,
       p.rolcreatedb   AS createdb,
       p.rolcreaterole AS createrole,
       pg_has_role(p.rolname, 'fl_app', 'MEMBER') AS member_fl_app,
       EXISTS (SELECT 1 FROM pg_roles q
                WHERE q.rolname = CASE WHEN p.rolname = 'fl_login_hub' THEN 'fl_app'
                                       ELSE 'fl_' || replace(p.rolname, 'fl_login_', '') || '_app' END)
         AS app_role_exists,
       pg_has_role(p.rolname, 'fl_platform_role', 'MEMBER') AS member_platform_role,
       (SELECT string_agg(u.cfg, ',') FROM pg_db_role_setting s, unnest(s.setconfig) AS u(cfg)
         WHERE s.setrole = p.oid AND u.cfg LIKE 'search_path=%') AS search_path
FROM pg_roles p
WHERE p.rolname LIKE 'fl\_login\_%' ESCAPE '\'
ORDER BY p.rolname;
