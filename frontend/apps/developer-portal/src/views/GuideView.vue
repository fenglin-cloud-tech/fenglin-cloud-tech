<script setup lang="ts">
const gatewayBase = `${window.location.protocol}//${window.location.hostname}:18080`
</script>

<template>
  <div>
    <div class="head">
      <h2>接入指引</h2>
      <p>开放平台统一经网关调用，使用 AppKey/AppSecret 做 HMAC-SHA256 签名，不是 Bearer Token。</p>
    </div>

    <div class="fl-dev-card">
      <h2>1. 调用地址</h2>
      <p>本地联调网关地址：</p>
      <pre class="fl-dev-code">{{ gatewayBase }}</pre>
      <p>探针：<code>GET {{ gatewayBase }}/openapi/ping/v1/ping</code>，验签通过后回显 app_id 与 tenant_id。</p>
    </div>

    <div class="fl-dev-card">
      <h2>2. 鉴权方式</h2>
      <p>每个请求携带以下请求头：</p>
      <pre class="fl-dev-code">X-App-Key: &lt;AppKey&gt;
X-Timestamp: &lt;秒级时间戳&gt;
X-Nonce: &lt;一次性随机串&gt;
X-Signature: &lt;小写十六进制签名&gt;</pre>
      <p>签名算法：<code>hmac_sha256_hex(AppSecret, METHOD + PATH + TIMESTAMP + NONCE + sha256_hex(body))</code>。</p>
      <p>时间戳偏差需在 300 秒内；nonce 使用后 5 分钟内不可重复。</p>
    </div>

    <div class="fl-dev-card">
      <h2>3. 应用与密钥</h2>
      <ul>
        <li>在「我的应用」创建应用，状态为 PENDING / DISABLED，需平台审核通过后才会启用。</li>
        <li>审核通过后在应用详情签发密钥；AppSecret 只在签发或轮换时显示一次，之后仅显示掩码。</li>
        <li>轮换会立即使旧密钥失效，请在调用方完成切换后再轮换。</li>
      </ul>
    </div>

    <div class="fl-dev-card">
      <h2>4. 调用与配额</h2>
      <ul>
        <li>「调用日志」按应用查看每次调用的路径、状态码、耗时与 request_id。</li>
        <li>「配额与用量」展示日配额、当日用量、当前 QPS 与限流状态；配额由平台在管理端配置。</li>
        <li>达到日限额或 QPS 上限时，网关返回 429 / 配额超限错误。</li>
      </ul>
    </div>

    <div class="fl-dev-card">
      <h2>5. 错误处理</h2>
      <p>平台统一返回 <code>code / message / request_id</code> 信封；排查问题时请提供 request_id，便于平台按链路定位。</p>
      <p>接口文档与联调沙箱已由后端提供：可在「接口文档」页查看平台已发布接口的结构与示例，并用已审核应用直接试调用。</p>
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
code {
  font-family: var(--fl-font-mono, monospace);
  word-break: break-all;
}
</style>
