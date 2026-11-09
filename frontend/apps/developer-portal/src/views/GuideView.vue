<script setup lang="ts">
const gatewayBase = `${window.location.protocol}//${window.location.hostname}:18080`
</script>

<template>
  <div>
    <div class="head">
      <h2>接入指引</h2>
      <p>开放平台统一经网关调用；应用凭证与可访问接口以管理端「开放平台应用」中配置的权限为准。</p>
    </div>

    <div class="fl-dev-card">
      <h2>1. 调用地址</h2>
      <p>本地联调网关地址：</p>
      <pre class="fl-dev-code">{{ gatewayBase }}</pre>
      <p>生产环境由平台分配独立域名，请勿在客户端硬编码本地地址。</p>
    </div>

    <div class="fl-dev-card">
      <h2>2. 鉴权方式</h2>
      <p>使用平台签发的应用凭证换取访问令牌，并在请求头携带：</p>
      <pre class="fl-dev-code">Authorization: Bearer &lt;access_token&gt;
Content-Type: application/json</pre>
      <p>令牌过期后使用 refresh_token 刷新；应用被停用或超出日配额时网关会返回对应错误码。</p>
    </div>

    <div class="fl-dev-card">
      <h2>3. 调用与配额</h2>
      <ul>
        <li>「我的应用」页展示每个应用今日调用量与日配额，数据来自平台实时统计。</li>
        <li>超出配额后请求将被拒绝，请在管理端调整配额或错峰调用。</li>
        <li>签名、IP 白名单、回调地址等高级配置在管理端开放平台应用中维护。</li>
      </ul>
    </div>

    <div class="fl-dev-card">
      <h2>4. 错误处理</h2>
      <p>平台统一返回 <code>code / message / request_id</code> 信封；排查问题时请提供 request_id，便于平台按链路定位。</p>
    </div>
  </div>
</template>

<style scoped>
.head {
  margin-bottom: 16px;
}
.head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.head p {
  margin: 0;
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
