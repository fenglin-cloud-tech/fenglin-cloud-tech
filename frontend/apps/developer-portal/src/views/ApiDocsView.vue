<script setup lang="ts">
import { computed } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'

const gatewayBase = `${window.location.protocol}//${window.location.hostname}:18080`

const gapItems = [
  '后端当前未提供接口文档元数据接口与在线联调沙箱，因此本页不能渲染由平台托管的接口清单，也不提供在线发送请求。',
  '下方签名说明与 ping 探针来自 openapi-service 真实实现（OpenApiSignatureFilter / OpenApiSignatureService），可用于本地联调自检。',
  '业务域开放接口需由平台按开放资源清单发布；未发布前不会出现在门户中。',
]

const curlExample = computed(
  () => `# 1. 计算 bodyHash = sha256_hex(body)，空 body 时对空字节串求 sha256
# 2. payload = METHOD + PATH + TIMESTAMP + NONCE + bodyHash
# 3. signature = hmac_sha256_hex(AppSecret, payload)，小写十六进制

curl -X GET '${gatewayBase}/openapi/ping/v1/ping' \\
  -H 'X-App-Key: <AppKey>' \\
  -H 'X-Timestamp: <秒级时间戳>' \\
  -H 'X-Nonce: <一次性随机串>' \\
  -H 'X-Signature: <签名>'`,
)
</script>

<template>
  <div>
    <PageHeader module="开放平台" title="接口文档与沙箱" description="展示后端真实签名规则与自检探针；文档/沙箱能力缺口如实标注。">
    </PageHeader>

    <GapNotice title="接口文档与联调沙箱缺口" :items="gapItems" />

    <el-card shadow="never" class="section">
      <template #header><strong>当前可用的开放接口</strong></template>
      <el-table :data="[{ method: 'GET', path: '/openapi/ping/v1/ping', desc: '验签自检，回显 app_id 与 tenant_id' }]" border size="small">
        <el-table-column prop="method" label="方法" width="90" />
        <el-table-column prop="path" label="路径" min-width="240" />
        <el-table-column prop="desc" label="说明" min-width="240" />
      </el-table>
      <p class="muted">除 ping 外，业务域开放接口尚未在本服务内发布；接入方应以平台正式发布的开放资源清单为准。</p>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header><strong>签名算法（HMAC-SHA256）</strong></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="必填请求头">
          X-App-Key、X-Timestamp（秒级）、X-Nonce、X-Signature
        </el-descriptions-item>
        <el-descriptions-item label="待签串">
          METHOD + PATH + TIMESTAMP + NONCE + sha256_hex(body)
        </el-descriptions-item>
        <el-descriptions-item label="签名">
          hmac_sha256_hex(AppSecret, 待签串)，小写十六进制
        </el-descriptions-item>
        <el-descriptions-item label="防重放">
          时间戳偏差不超过 300 秒；nonce 使用后写入 Redis，5 分钟内不可重复
        </el-descriptions-item>
        <el-descriptions-item label="启用条件">
          AppKey 状态 ACTIVE、应用状态 ENABLED、未过期
        </el-descriptions-item>
      </el-descriptions>
      <pre class="code">{{ curlExample }}</pre>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header><strong>联调建议</strong></template>
      <ul class="tips">
        <li>在「我的应用」创建应用并等待审核通过，再在应用详情签发 AppKey/AppSecret。</li>
        <li>AppSecret 只显示一次，请先保存在服务端环境变量中，再发起签名请求。</li>
        <li>签名失败时重点核对：时间戳单位是否为秒、body 是否与待签串一致、签名字母大小写。</li>
        <li>调用结果可在「调用日志」按应用查看，配额与限流状态在「配额与用量」查看。</li>
      </ul>
    </el-card>
  </div>
</template>

<style scoped>
.section {
  margin-bottom: 12px;
}
.muted {
  margin: 8px 0 0;
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.code {
  margin: 12px 0 0;
  padding: 12px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: var(--fl-radius-md);
  font-size: 12px;
  line-height: 1.7;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.tips {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
  color: var(--fl-color-text-secondary);
  font-size: 13px;
}
</style>
