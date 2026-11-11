# OPEN-06 全局超管设计方案（v1）

> 范围：**只出设计，不改代码**。本文给出两种可行方案在"改动面 / 对现有授权查询的影响 / 迁移风险 / 回滚难度"四个维度的对比与推荐。
> 复核时间：2026-11-11。结论基于当前仓库真实代码与迁移脚本，不是纸面推演。

## 一、现状与根因

缺口台账原始描述：平台角色按租户建模（`hub_role` 唯一 `tenant_id + code`），跨租户运维需逐租户授权，无全局超管。

当前实现的关键事实：

1. **用户是单租户的**：`hub_user` 唯一键 `uk_hub_user_username (tenant_id, username)`（[V1_0_27__create_iam_tables.sql](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/V1_0_27__create_iam_tables.sql#L33-L33)）。登录必须带 `tenant_code`，JWT 固化 `tenant_id`。
2. **角色/授权也是单租户的**：`hub_role` 唯一 `(tenant_id, code)`（[同文件 L161](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/V1_0_27__create_iam_tables.sql#L145-L163)），`hub_user_role` 带 `tenant_id` 且唯一 `(tenant_id, user_id, role_id)`（[同文件 L207-L220](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/V1_0_27__create_iam_tables.sql#L207-L220)）。
3. **PLATFORM_ADMIN 已存在，但每个租户一行**：`V1_0_48` 为每个租户各插一条 `PLATFORM_ADMIN` 并授予 `platform:*`（[V1_0_48__add_platform_admin_role.sql](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/V1_0_48__add_platform_admin_role.sql#L5-L22)）；授予/撤销只能"在当前租户内"进行（[PlatformAdminService.java](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-tenant-service/src/main/java/com/fenglin/tenant/service/PlatformAdminService.java#L41-L71)）。
4. **网关强校验租户一致性**：`X-Tenant-Id` 非空时必须等于 JWT 里的 `tenant_id`，否则 403（[AuthGlobalFilter.java L105-L109](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-gateway/src/main/java/com/fenglin/gateway/filter/AuthGlobalFilter.java#L105-L109)）；PORTAL 端别禁访 `/api/hub/v1/admin`（[L110-L112](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-gateway/src/main/java/com/fenglin/gateway/filter/AuthGlobalFilter.java#L110-L112)）。
5. **权限回源按"用户 + 当前租户"裁决**：`permissionsOf(userId)` 先取 `TenantContext.requireTenantId()`（来自网关注入的 `X-Tenant-Id`），用户不属于该租户直接返回空权限（[AuthService.java L332-L346](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-auth-service/src/main/java/com/fenglin/auth/service/AuthService.java#L332-L346)）。内部接口本身**不接收租户参数**，租户来自头（[InternalAuthController.java L42-L47](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-auth-service/src/main/java/com/fenglin/auth/web/InternalAuthController.java#L42-L47)）。
6. **权限 SQL 本身不按租户过滤**：`PermissionQueryMapper` 只按 `user_id` 联查 `hub_user_role → hub_role → hub_role_permission`（[PermissionQueryMapper.java L12-L34](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-auth-service/src/main/java/com/fenglin/auth/mapper/PermissionQueryMapper.java#L12-L34)），租户约束实际由"用户属于哪个租户"和 RLS 承担。
7. **RLS 有平台角色旁路，但业务连接用不到**：hub 表同时有租户策略 `tenant_id = current_tenant_id()` 和 `FOR ALL TO fl_platform_role USING (true)`（[V1_0_29__enable_p1_rls.sql L43-L59](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/V1_0_29__enable_p1_rls.sql#L43-L59)）；业务服务以 `fl_app` 连接，平台策略在当前运行态不生效。
8. **权限解析有缓存**：网关按 `(tenant, user, pv)` 做 L1 60s / L2 10min 缓存，auth 不可用 fail-close 503（[PermissionResolver.java L76-L117](file:///e:/fenglin-cloud-tech/backend/platform/fenglin-gateway/src/main/java/com/fenglin/gateway/security/PermissionResolver.java#L76-L117)）。

**根因不是缺一个权限点，而是"身份、会话、租户上下文"三层都是单租户模型**：一个账号只属于一个租户，一个 JWT 只对应一个租户，网关不允许租户头与令牌不一致。因此任何"全局超管"方案都必须解决"同一身份如何进入其他租户"，而不是只加角色。

## 二、目标与非目标

目标：

- 单一平台运维身份，一次登录即可进入授权范围内的任意租户，不必维护 N 套账号；
- 进入某租户后，数据面严格只看到该租户数据，不因超管身份绕过 RLS 做全库扫描；
- 全过程可审计：谁、何时、以什么理由、切到哪个租户、在其中做了什么；
- 授权可收回、可过期，收回后 ≤60s 生效。

非目标：跨租户聚合报表、跨租户批量写、修改各业务库 RLS、让租户内普通用户具备多租户归属。

## 三、方案 A：平台级独立身份 + 显式租户切换（推荐）

### 3.1 数据模型（一表一迁移）

| 迁移 | 表 | 关键列 |
| --- | --- | --- |
| V1_0_57 | `hub_platform_role` | id / code / name / status / builtin / 审计软删列 |
| V1_0_58 | `hub_platform_role_permission` | id / role_id / permission_code，唯一 `(role_id, permission_code)` |
| V1_0_59 | `hub_platform_user_role` | id / user_id / role_id / granted_at / expire_at / granted_by，唯一 `(user_id, role_id)` |

- 三张表**不带 `tenant_id`、不启用租户 RLS**，只 `GRANT` 给认证服务的连接角色；业务服务不授予任何权限，避免经通用查询接口泄露。
- 权限码复用 `hub_permission`（全局表），新增 `platform:tenant:switch`（切换）与 `platform:tenant:switch:write`（切入已停用租户/写操作，见待决策 1）。
- 一表一迁移，符合"一个新表一个迁移版本"的既有约束。

### 3.2 登录与切换流程

1. 登录**不变**：仍按 `tenant_code + username + password` 签发 home 租户令牌。
2. 新增 `POST /api/hub/v1/sso/tenant:switch`，body `{target_tenant_id, reason}`：
   - 校验调用者有启用中的平台角色、且持 `platform:tenant:switch`；
   - 目标租户必须存在且为 `ACTIVE`；`SUSPENDED` 仅持 `platform:tenant:switch:write` 且必须填 `reason` 才允许（排障场景）；
   - 签发**短时切换令牌**（TTL ≤30min），claims 增加：`tenant_id = target`、`user_id = operator`、`platform_ops = true`、`act = {sub: operator, home_tenant: x, switch_id: uuid}`；
   - refresh token 不换绑，只能续签 home 租户，防止"借刷新永久驻留他租户"；
   - 写审计 `PLATFORM_TENANT_SWITCH`（操作者、目标租户、理由、jti、IP）。
3. 切换令牌**不允许再次切换**（已有 `act` 直接拒绝），避免形成链式身份。

### 3.3 网关改动

- 仅当令牌同时满足 `platform_ops=true` 且 `act` 声明完整、签名校验通过时，才放行 `X-Tenant-Id` 与 home 租户不一致；即便如此仍要求 `X-Tenant-Id` 必填且等于令牌 `tenant_id`（即目标租户）。
- 注入 `X-Impersonated-By = operator_id`、`X-Tenant-Switch-Id`；下游审计记录"实际租户 + 冒充者"。
- PORTAL 端别仍禁管理端；切换端点只允许 ADMIN 端调用。
- 旧令牌没有这些 claim，走原分支，行为完全不变。

### 3.4 权限解析（关键改动）

- `permissionsOf` 增加一个**平台分支**：当 `user.tenant_id != 当前租户` 时（原逻辑直接返回空），先查 `hub_platform_user_role`（角色启用、未过期），有 `platform:tenant:switch` 才继续，否则维持空权限——**不能只看请求头就放权**。
- 有效权限口径（建议）：**目标租户 TENANT_ADMIN 角色权限 ∪ 该操作者平台角色权限**。即切入后具备该租户管理员能力，同时保留 `platform:*`。
- 缓存：`PermissionResolver` 的 key 已是 `(tenant, user, pv)`，切换令牌 tenant=目标租户，天然分键；平台角色授予/撤销时 bump `hub_user.permission_version`，保证 ≤60s 失效。
- `PermissionQueryMapper` 不改；新增 `PlatformPermissionQueryMapper` 专查平台三表。

### 3.5 审计与安全

- 审计上下文新增 `impersonated_by` 与 `switch_id`；所有切入后的写操作都带操作者真实身份。
- 高风险事件（切换、切入停用租户、平台角色变更）进审计告警。
- 平台表仅认证服务可读；任何管理端接口不得返回平台管理员名单给租户管理员。

### 3.6 改动面清单

| 模块 | 改动 |
| --- | --- |
| hub-migrations | 3 个建表迁移 + 1 个权限码/初始角色 seed |
| auth-service | `PlatformPermissionQueryMapper`、`permissionsOf` 平台分支、`TenantSwitchService`、SSO 切换端点、令牌 claim |
| gateway | `AuthGlobalFilter` 平台放行分支、`GatewayPrincipal` 增加 `act`、header 注入 |
| tenant-service | 存量 `PLATFORM_ADMIN` 持有者**白名单**导入平台角色；新增全局授予/撤销接口（可放第二阶段） |
| admin-ui | 顶部租户切换器（仅平台角色可见）、平台管理员管理页 |
| 业务服务 | 无逻辑改动；公共审计库增加 `impersonated_by` 透传 |

### 3.7 对现有授权查询的影响

- 普通用户路径**零变化**：仍是一次 `permissionsOf`，租户不匹配仍返回空。
- 只有原本"租户不匹配直接返回空"的分支新增一次平台表索引查询，最坏多 1 次点查。
- `hub_role` / `hub_role_permission` / `hub_user_role` 不加行、不改唯一键、不改 RLS，现有角色管理与分页无感知。
- 缓存 key 需增加平台维度（建议 key 前缀区分普通/切换），否则同一 `(tenant,user,pv)` 可能混用两种权限集合。

### 3.8 迁移风险

- 纯新增表 + 代码分支，**无存量业务数据改写**，风险低。
- 主要风险是"初始超管名单"：**必须人工白名单**，不能把所有已持 `PLATFORM_ADMIN` 的账号自动升级为全局超管，否则等于一次性权限放大。
- 历史令牌无新 claim，天然兼容。
- 平台表无租户 RLS，必须靠数据库授权收口（只授认证服务），需要在上线检查项里显式验证"业务连接 `fl_app` 查平台表被拒"。

### 3.9 回滚难度：低

- 代码回滚 + `DROP` 三张表即可，无业务数据依赖。
- 已签发的切换令牌在新代码下因缺少平台分支、`X-Tenant-Id` 与 home 租户不一致，会被 403 拒绝，不会留下越权通道。
- 唯一保留物是审计事件，符合合规要求，无需清理。

## 四、方案 B：租户内授权扇出（不新增身份模型）

### 4.1 做法

- 保持 `PLATFORM_ADMIN` 每租户一行，平台超管在每个租户内**各有一条 `hub_user_role`**（甚至各有一个 `hub_user` 账号）。
- 维护一份"全局超管名单"（配置或表），租户创建时扇出授权；存量租户一次性补齐。
- 登录仍需按 `tenant_code` 逐个租户登录；若要求一次登录，就必须改造会话层，那就退化成方案 A 的 3.2。

### 4.2 改动面

| 模块 | 改动 |
| --- | --- |
| tenant-service | 创建租户末尾扇出授权；存量补齐接口；全局名单维护 |
| iam/tenant | 每租户建账号或复用账号（密码同步问题） |
| admin-ui | 无需切换器，但运维需保存 N 套登录入口 |
| auth / gateway / RLS | 零改动 |

### 4.3 对现有授权查询的影响

- 无。这是方案 B 唯一明显的优势。

### 4.4 迁移风险：中高

- 数据写入型迁移：`租户数 × 超管数` 行授权，中途失败会半扇出，必须幂等重跑。
- 新增租户若漏挂勾子，会静默失去超管入口，且没有全局视角能发现。
- **租户管理员可在本租户内禁用/改密超管账号**，导致平台失去该租户入口——本地权限能影响全局运维，这是结构性风险。
- 账号/密码在 N 个租户重复，离职回收要 N 处操作，易漏。

### 4.5 回滚难度：中

- 没有中央授权表，回滚需按租户枚举删除 `hub_user_role`（甚至账号）；漏删即残留高权限。
- 审计分散在各租户，事后追溯需要跨库汇总。

## 五、两种方案对比

| 维度 | 方案 A：平台身份 + 切换 | 方案 B：租户内扇出 |
| --- | --- | --- |
| 单一身份 | 是 | 否（N 套账号或 N 套登录） |
| 数据隔离 | 切入后仍受目标租户 RLS 约束 | 同左，但本地管理员可反制 |
| 审计完整性 | 集中（切换事件 + 冒充者透传） | 分散在各租户 |
| 改动面 | 3 张表 + auth/gateway 各一处分支 + 前端 | tenant-service 扇出 + 账号维护 |
| 对现有授权查询影响 | 仅新增一个"租户不匹配"分支 | 无 |
| 迁移风险 | 低（纯新增；白名单需人工确认） | 中高（数据写入、漏扇出、密码漂移） |
| 回滚难度 | 低（drop 表 + 回代码） | 中（需枚举清理授权） |
| 越权面 | 集中在平台角色，可过期可审计 | 分散且可被租户管理员间接影响 |

## 六、推荐

**推荐方案 A**。理由：身份唯一、数据面继续受 RLS 约束、审计集中、回滚干净；代价只是 auth / gateway 各一处分支和 3 张新增表，且不触碰现有租户角色与授权查询。

若批准实施，建议按以下顺序拆成独立里程碑：

1. 三张平台表 + 权限码/初始角色 seed（纯迁移，可先合）；
2. auth 平台权限解析 + 切换端点；
3. gateway 放行分支与 header 注入；
4. admin-ui 租户切换器；
5. 平台管理员全局授予/撤销；
6. 真实联调：A 租户超管切入 B 租户只看到 B 数据、切回、撤销后 ≤60s 失效、旧令牌不受影响、PORTAL 仍禁管理端、直接改 `X-Tenant-Id` 仍 403。

## 七、待决策项

1. **切换后的写权限**：直接给目标租户 `TENANT_ADMIN` 全权，还是只读？只读需要额外的"平台只读权限映射"，工作量增加。
2. 切入生产租户是否要求双人审批或工单号。
3. 平台角色是否允许自我繁殖（管理平台管理员名单），还是只允许初始种子账号。
4. 切换令牌 TTL（建议 30 分钟）以及是否允许续期。
5. 是否允许切入 `SUSPENDED` 租户（建议仅限持 `platform:tenant:switch:write` 的排障只读场景）。

---

> 本文仅为设计，未新增任何表、接口或代码。方案确定后再按里程碑实施。
