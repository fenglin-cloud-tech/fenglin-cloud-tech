# 高优批次 4：OPEN-04 + PLAT-27 修复报告

> 日期：2026-11-15
> 范围：开放平台应用审核意见回显（OPEN-04，方案 A）、登录 `client_type` 白名单收敛（PLAT-27，方案 B）
> 规程：造数走真实接口；仅 OPEN-04 获批 DDL；真实浏览器回归；截图逐张校验；台账脚本复算；提交不推送。

## 一、OPEN-04 应用详情回显审核意见/审核时间（方案 A）

### 1.1 变更

| 层 | 变更 |
| --- | --- |
| 迁移 | 新增 `backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/V1_0_65__add_open_app_review_fields.sql`：`hub_open_app` 增加 `review_comment varchar(500)`、`reviewed_at timestamptz`，均可空；`COMMENT ON COLUMN` 写明用途与「历史数据为 NULL」。未回填历史行。 |
| Mapper | `OpenAppMapper.findById/listByOwner/listAdmin` 查询增加两列；`updateReview` 写入 `review_comment`、无论通过/驳回统一 `reviewed_at = now()`。 |
| Service | `OpenAppService.review`：`reason` 去空白后落库；超 500 字 400；`REJECT` 无意见 400「驳回时必须填写审核意见」。三处 DTO 构造回填两字段。 |
| DTO | `PortalAppView`、`AdminAppView`、`PortalAppDetail` 增加 `review_comment`、`reviewed_at`。 |
| 前端契约 | `api-client` 类型补两字段；新增 `apps({status, review_status, page, size})` 管理端列表端点。 |
| admin-ui | `OpenApiView` 新增「应用审核历史 OPEN-04」卡片：app_id/编码/名称/归属类型/审核状态/应用状态/审核意见/审核时间（NULL 显示 `—`）；驳回意见必填校验；归属类型选项对齐后端真实枚举 ISV/INTERNAL/CUSTOMER。 |
| developer-portal | `AppDetailView`：REJECTED 顶部红色提示「审核未通过：<review_comment>」（缺意见时提示联系管理员）；基本信息新增审核状态/审核时间/审核意见（NULL 显示 `—`）。 |

### 1.2 回滚方式

```sql
ALTER TABLE hub_open_app
    DROP COLUMN IF EXISTS review_comment,
    DROP COLUMN IF EXISTS reviewed_at;
```

两列可空、无索引/外键/触发器依赖；回滚后旧代码读写不受影响。历史单据两列保持 NULL，前端显示 `—`，不报错。

### 1.3 影响面

- 仅开放平台应用审核链路（管理端审核接口、门户应用详情、管理端应用列表）。
- 不改变审核状态机与既有列；重复审核仍返回 409（实测）。
- 新增必填约束仅作用于 REJECT：此前无意见也能驳回，现返回 400。

### 1.4 回归证据

样例（真实网关接口创建并审核）：

| 应用 | code | 审核结果 | 意见 | 审核时间 |
| --- | --- | --- | --- | --- |
| 2121883111918100481 | APP2026000010 | REJECTED / DISABLED | 缺少生产回调域名白名单说明，请补充后重新提交 | 2026-11-15T08:28:51.378145Z |
| 2121883113071534082 | APP2026000011 | APPROVED / ENABLED | 回调地址与接口范围说明齐全，准予上线 | 2026-11-15T08:28:51.603599Z |

同一应用审核前后对照（`work/evidence/`）：

| 证据文件 | 结果 |
| --- | --- |
| `open04-detail-reject-before.json` | 200，`review_status=PENDING`，**无** `review_comment`/`reviewed_at` |
| `open04-reject-no-reason.json` | 400 `FL-COMMON-0400`「驳回时必须填写审核意见」 |
| `open04-reject-with-reason.json` | 200，REJECTED |
| `open04-detail-reject-after.json` | 200，`review_comment=缺少生产回调域名白名单说明，请补充后重新提交`，`reviewed_at=2026-11-15T08:28:51.378145Z` |
| `open04-detail-approve-before.json` | 200，PENDING，无两字段 |
| `open04-approve-with-reason.json` | 200，APPROVED |
| `open04-detail-approve-after.json` | 200，`review_comment=回调地址与接口范围说明齐全，准予上线`，`reviewed_at=2026-11-15T08:28:51.603599Z` |
| `open04-admin-apps.json` | 200，列表两条记录均含 `review_comment`/`reviewed_at` |

浏览器截图（`work/shots/`，逐张已打开核对）：

| 文件 | 核对内容 |
| --- | --- |
| `open04-dev-reject-1115.jpg` | 开发者端 APP2026000010：顶部红色「审核未通过：缺少生产回调域名白名单说明，请补充后重新提交」，基本信息行显示 REJECTED、审核时间、审核意见 |
| `open04-dev-approve-1115.jpg` | 开发者端 APP2026000011：APPROVED/ENABLED，基本信息显示审核时间与意见 |
| `open04-admin-history-1115.jpg` | 管理端「应用审核历史 OPEN-04」：0011/0010 两行同屏显示编码、名称、审核状态、应用状态、审核意见、审核时间 |

说明：历史表中 APP2026000004/0005/0008/0009 为同轮证据采集的中间产物，其意见曾因 PowerShell 以 GBK 读取脚本字面量而乱码（`????`/`缂哄皯…`），已被 0010/0011 取代；审核接口对终态应用返回 409，无法原地重审修正，特此标注，不影响 0010/0011 的结论。

## 二、PLAT-27 `client_type` 白名单收敛（方案 B）

### 2.1 变更

- `AuthService.login` 增加白名单常量 `CLIENT_TYPES = {ADMIN, PORTAL, SYSTEM}`；`client_type` 去空、转大写后校验，不在白名单即抛 `BizException(AuthErrorCode.INVALID_PARAMETER)`（HTTP 400，code `0000003`），消息包含允许值与当前值（空/缺省显示 `(空)`）。
- 删除原先「缺省/空值静默降级 PORTAL」的默认行为：缺省即 400。
- 同步把 `.tmp/emp-login.json`、`.tmp/e2e/emp-employee.json` 的 `client_type` 改为 `PORTAL`；历史 JWT claims 证据文件未篡改，在 `design-review/2026-11-14-prod-phase1/emp-leads-my-evidence/evidence.md` 加注。
- 历史报告加注「EMPLOYEE 可登录」现象作废，不追改正文：《07-重打包与补测报告》《14-全量点击测试-批6批7-门户与联动》《15-全量点击测试总结》。

### 2.2 回归证据（`work/evidence/`）

| 场景 | 请求 | 结果 |
| --- | --- | --- |
| EMPLOYEE | `plat27-employee.json` | 400「client_type 仅支持 ADMIN / PORTAL / SYSTEM，当前值: EMPLOYEE」 |
| 乱写字符串 | `plat27-foo.json`（FOO） | 400「…当前值: FOO」 |
| 空字符串 | `plat27-blank.json` | 400「…当前值: (空)」 |
| 缺省字段 | `plat27-missing.json` | 400「…当前值: (空)」 |
| ADMIN | `plat27-admin.json` | 200，token 换取成功 |
| PORTAL | `plat27-portal.json` | 200，token 换取成功 |
| userinfo | `plat27-admin-userinfo.json` / `plat27-portal-userinfo.json` | 均 200，`client_type` 分别为 ADMIN/PORTAL |

### 2.3 IAM 菜单按 `client_type` 取数对照（改前/改后一致）

| client_type | `hub_menu` 行数（只读 SQL） | `GET /api/hub/v1/admin/menus?clientType=` |
| --- | --- | --- |
| ADMIN | 5 | 200，5 条 |
| PORTAL | 0 | 200，0 条 |
| SYSTEM | 0 | 200，0 条 |

本轮未改 `hub_menu` 数据与菜单接口逻辑，白名单收敛只影响登录入口，故三端菜单数与改前完全一致（证据 `plat27-menu-{ADMIN,PORTAL,SYSTEM}.json`）。

### 2.4 影响面

- 任何仍以 `EMPLOYEE` 或其它值调用登录的脚本/客户端将直接 400；本轮已同步 2 个测试 JSON 与 1 份历史证据说明。
- 前端三端分别使用 ADMIN / PORTAL / SYSTEM，浏览器实测三端登录成功：`plat27-admin-login.jpg`、`plat27-portal-login.jpg`、`plat27-dev-login.jpg`。

## 三、台账复算

`powershell -NoProfile -ExecutionPolicy Bypass -File .tmp/ledger-count2.ps1` 输出：

```
total_rows=136
impact[待验证]=1
impact[可延后]=32
impact[已关闭]=2
impact[已关闭（误判）]=1
impact[已修复]=54
impact[影响体验]=38
impact[影响体验（已修复）]=8
open_total=70
open_block=0
open_exp=38
open_defer=32
```

与台账总览一致：开放 70（阻塞 0 + 影响体验 38 + 可延后 32）+ 已修复 62（54+8）+ 已关闭 3 + 待验证 1 = 136。
