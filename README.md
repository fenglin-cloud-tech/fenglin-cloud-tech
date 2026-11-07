# 丰麟云枢（Fenglin Cloud Hub）

丰麟云创科技有限公司企业云平台主仓，承载 **丰麟云枢平台** 及 **8 个业务域产品** 的规划、设计与交付文档。

当前阶段仓库内容为项目文档基线（共 109 份产品文档 + 1 份交付文档校验报告），暂无业务代码。

## 仓库结构

```
fenglin-cloud-tech/
├── README.md                        # 本文件：仓库说明与文档入口
├── .gitignore                       # Windows / IDE / Java / Node / 本地配置与密钥
└── 丰麟云创科技有限公司项目文档/
    ├── 00_丰麟云枢平台总纲_fenglin-cloud-Hub/          13 份
    ├── 01_丰麟云销_fenglin-cloud-CRM/                 12 份
    ├── 02_丰麟云协_fenglin-cloud-Collaborate/         12 份
    ├── 03_丰麟云才_fenglin-cloud-Talent/              12 份
    ├── 04_丰麟云财_fenglin-cloud-Finance/             12 份
    ├── 05_丰麟云研_fenglin-cloud-DevOps/              12 份
    ├── 06_丰麟云服_fenglin-cloud-Service/             12 份
    ├── 07_丰麟云法_fenglin-cloud-Legal/               12 份
    ├── 08_丰麟云智_fenglin-cloud-Intelligence/        12 份
    └── 丰麟云枢_9产品交付文档校验报告.md
```

## 文档入口

| 编号 | 产品 | 英文标识 | 业务域 | 文档目录 |
|---|---|---|---|---|
| 00 | 丰麟云枢 | fenglin-cloud-Hub | 平台总纲 | [00_丰麟云枢平台总纲_fenglin-cloud-Hub](丰麟云创科技有限公司项目文档/00_丰麟云枢平台总纲_fenglin-cloud-Hub) |
| 01 | 丰麟云销 | fenglin-cloud-CRM | 域A 客户与增长 | [01_丰麟云销_fenglin-cloud-CRM](丰麟云创科技有限公司项目文档/01_丰麟云销_fenglin-cloud-CRM) |
| 02 | 丰麟云协 | fenglin-cloud-Collaborate | 域B 协作与办公 | [02_丰麟云协_fenglin-cloud-Collaborate](丰麟云创科技有限公司项目文档/02_丰麟云协_fenglin-cloud-Collaborate) |
| 03 | 丰麟云才 | fenglin-cloud-Talent | 域C 组织与人才 | [03_丰麟云才_fenglin-cloud-Talent](丰麟云创科技有限公司项目文档/03_丰麟云才_fenglin-cloud-Talent) |
| 04 | 丰麟云财 | fenglin-cloud-Finance | 域D 财务与经营 | [04_丰麟云财_fenglin-cloud-Finance](丰麟云创科技有限公司项目文档/04_丰麟云财_fenglin-cloud-Finance) |
| 05 | 丰麟云研 | fenglin-cloud-DevOps | 域E 研发 | [05_丰麟云研_fenglin-cloud-DevOps](丰麟云创科技有限公司项目文档/05_丰麟云研_fenglin-cloud-DevOps) |
| 06 | 丰麟云服 | fenglin-cloud-Service | 域F 服务与运营 | [06_丰麟云服_fenglin-cloud-Service](丰麟云创科技有限公司项目文档/06_丰麟云服_fenglin-cloud-Service) |
| 07 | 丰麟云法 | fenglin-cloud-Legal | 域G 法务与风控 | [07_丰麟云法_fenglin-cloud-Legal](丰麟云创科技有限公司项目文档/07_丰麟云法_fenglin-cloud-Legal) |
| 08 | 丰麟云智 | fenglin-cloud-Intelligence | 域H 智能与洞察 | [08_丰麟云智_fenglin-cloud-Intelligence](丰麟云创科技有限公司项目文档/08_丰麟云智_fenglin-cloud-Intelligence) |

统一基线为 `FL-HUB-STD-001《丰麟云枢 · 平台总体规范与统一标准》`，各产品文档均以其为约束。建议先阅读平台总纲中的需求规格说明书与总体规范，再进入具体产品目录。

### 标准文档集

每个产品目录按 12 份标准文档组织（平台总纲目录额外包含总体规范与统一标准）：

1. 项目需求规格说明书（SRS）
2. 架构设计文档
3. 数据库设计文档
4. 模块详细功能与工作流设计
5. 接口文档
6. UIUX 设计规范
7. 测试用例文档
8. 性能测试报告
9. 安全评估报告
10. 部署运维手册
11. 用户操作手册
12. 系统管理员手册

## 分支与协作

- 默认分支：`main`
- 文档变更请通过分支 + 合并请求（MR/PR）提交，保留评审记录
- 提交信息建议使用 `docs(产品简称): 变更说明` 格式
- 禁止提交任何密钥、证书、`.env` 等敏感文件（见 `.gitignore`）
