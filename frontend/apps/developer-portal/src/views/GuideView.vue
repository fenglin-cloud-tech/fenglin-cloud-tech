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
      <ul>
        <li><code>METHOD</code> 大写；<code>PATH</code> 为不含 query string 的请求路径（如 <code>/openapi/ping/v1/ping</code>），查询参数不参与签名。</li>
        <li><code>sha256_hex(body)</code>：对原始请求体的字节做 SHA-256，取小写十六进制；无请求体时对空字节串计算。</li>
        <li><code>X-Signature</code> 为小写十六进制 HMAC-SHA256 结果，服务端按小写比较。</li>
        <li>时间戳偏差需在 <strong>300 秒</strong>内；nonce 使用后 <strong>5 分钟</strong>内不可重复（同一 nonce 重放返回 401 / 0009002）。</li>
        <li>可选携带 <code>X-Request-Id</code>；不传时平台生成，并在响应与调用日志中回显。</li>
      </ul>
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
        <li>限流按<strong>应用维度</strong>执行：QPS 上限按秒窗口计数，日配额按自然日累计；任一超限返回 <strong>HTTP 429</strong>、业务码 <strong>0009003</strong>。</li>
        <li>429 响应不计入成功调用，但会出现在调用日志中，可按 request_id 定位。</li>
      </ul>
    </div>

    <div class="fl-dev-card">
      <h2>5. 错误处理</h2>
      <p>平台统一返回 <code>code / message / request_id</code> 信封；排查问题时请提供 request_id，便于平台按链路定位。</p>
      <table class="fl-dev-table">
        <thead>
          <tr><th>HTTP</th><th>业务码</th><th>含义</th><th>处理建议</th></tr>
        </thead>
        <tbody>
          <tr><td>401</td><td>0009002</td><td>签名校验失败：缺头、时间戳超窗、nonce 重放、AppKey 不存在/未启用/过期、签名不匹配、AppSecret 不可用</td><td>核对四个签名头与 AppSecret；校验本机时钟；每次重试必须换新 nonce 与时间戳</td></tr>
          <tr><td>429</td><td>0009003</td><td>QPS 或日配额超限</td><td>按下方退避策略重试，或申请调整配额</td></tr>
        </tbody>
      </table>
      <p><strong>重试策略</strong>：仅对 GET 等幂等请求自动重试；429 与 5xx 采用指数退避（如 200ms → 500ms → 1s，最多 3 次）。每次重试都必须重新生成 <code>X-Timestamp</code>、<code>X-Nonce</code>、<code>X-Signature</code>（nonce 一次性），并复用同一个 <code>X-Request-Id</code> 便于串链路。401 / 0009002 不要重试，先修签名。</p>
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
.fl-dev-table {
  width: 100%;
  margin-top: 12px;
  border-collapse: collapse;
  font-size: 12px;
}
.fl-dev-table th,
.fl-dev-table td {
  padding: 8px 10px;
  border: 1px solid var(--fl-color-border, #e7ecf5);
  text-align: left;
  vertical-align: top;
  line-height: 1.6;
}
.fl-dev-table th {
  background: var(--fl-color-bg, #f7f9fc);
  font-weight: 600;
}
</style>
