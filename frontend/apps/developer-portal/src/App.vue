<script setup lang="ts">
import { APP_TITLE } from '@fenglin/shared'

const adminUrl = 'http://localhost:5173'
const openapiBase = '/openapi/v1'
</script>

<template>
  <header class="fl-dev-header">
    <span class="fl-dev-brand">{{ APP_TITLE }} · 开发者门户</span>
    <span>网关 18080</span>
  </header>

  <main class="fl-dev-main">
    <section class="fl-dev-card">
      <h2>开发者门户（骨架）</h2>
      <p>
        本应用为 pnpm workspace 中的开发者门户骨架，端口 5175，已配置 /api、/openapi、/actuator 到网关 18080 的代理。
        应用注册、密钥申请、配额查看与调用日志等管理动作当前在中台管理台「开放平台」页面完成。
      </p>
      <p><a :href="adminUrl" target="_blank" rel="noopener">进入中台管理台 →</a></p>
    </section>

    <section class="fl-dev-card">
      <h2>OpenAPI 入口</h2>
      <p>网关已将 /openapi/** 路由至 fenglin-openapi-service（18089）。调用示例：</p>
      <pre class="fl-dev-code">curl -H "X-App-Key: &lt;app_key&gt;" \
  -H "X-App-Secret: &lt;app_secret&gt;" \
  {{ openapiBase }}/echo</pre>
      <p>具体接口契约以开放平台运营端登记的应用与密钥为准；本期未新增开发者自助注册接口。</p>
    </section>

    <section class="fl-dev-card">
      <h2>未覆盖能力（明确标注）</h2>
      <ul>
        <li>无开发者自助注册、密钥轮换、配额自助申请接口。</li>
        <li>无在线 API 文档/调试台数据源，需后端补充 OpenAPI 描述文件。</li>
        <li>调用日志查询需开放平台管理权限，普通开发者暂无只读接口。</li>
      </ul>
    </section>
  </main>
</template>
