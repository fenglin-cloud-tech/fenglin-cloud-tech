<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  openApiPortal,
  type OpenApiKeyResponse,
  type PortalAppDetail,
  type PortalKeyView,
  type PortalScopeView,
} from '@fenglin/api-client'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const appId = String(route.params.id)
const detail = ref<PortalAppDetail | null>(null)
const keys = ref<PortalKeyView[]>([])
const scopeCatalog = ref<PortalScopeView[]>([])
const callbackText = ref('')
const selectedScopes = ref<string[]>([])
const loading = ref(false)
const savingCallback = ref(false)
const savingScopes = ref(false)
const issuing = ref(false)
const error = ref('')

const secretVisible = ref(false)
const issuedKey = ref<OpenApiKeyResponse | null>(null)

const canView = computed(() => auth.can('biz:openapi:portal:view'))
const canManage = computed(() => auth.can('biz:openapi:app:manage'))
const canKey = computed(() => auth.can('biz:openapi:key:manage'))
const approved = computed(() => detail.value?.review_status === 'APPROVED')
const enabled = computed(() => detail.value?.status === 'ENABLED')

const scopeGroups = computed(() => {
  const groups = new Map<string, PortalScopeView[]>()
  for (const scope of scopeCatalog.value) {
    const items = groups.get(scope.domain) ?? []
    items.push(scope)
    groups.set(scope.domain, items)
  }
  return Array.from(groups.entries()).map(([domain, items]) => ({ domain, items }))
})

const gapItems = [
  '1001/1002 的创建与审核在管理端完成；门户创建的应用为 PENDING / DISABLED，审核通过前不能签发可用密钥、也不能调用开放 API。',
  'AppSecret 明文只在创建或轮换成功的响应中返回一次；本页密钥列表只展示掩码（SK-****** + 指纹），无法再次查看明文。',
  '1004 配额配置与 1005 全租户日志查询属于管理端能力；门户仅展示本人应用的配额、用量与日志。',
  '接口文档与联调沙箱已在「接口文档」页提供；应用审核通过并签发密钥后可在线试调用。',
]

async function load() {
  if (!canView.value) {
    error.value = '当前账号缺少 biz:openapi:portal:view，无法查看应用详情。'
    return
  }
  loading.value = true
  error.value = ''
  try {
    detail.value = await openApiPortal.appDetail(appId)
    callbackText.value = detail.value.redirect_uris.join('\n')
    selectedScopes.value = [...detail.value.scopes]
    if (canManage.value) {
      scopeCatalog.value = await openApiPortal.scopes({ app_id: appId })
    }
    if (canKey.value) {
      keys.value = await openApiPortal.keys(appId)
    }
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function saveCallback() {
  savingCallback.value = true
  try {
    const uris = callbackText.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    detail.value = await openApiPortal.updateCallback(appId, { redirect_uris: uris })
    callbackText.value = detail.value.redirect_uris.join('\n')
    ElMessage.success('回调地址已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    savingCallback.value = false
  }
}

async function saveScopes() {
  savingScopes.value = true
  try {
    detail.value = await openApiPortal.updateScopes(appId, { scopes: selectedScopes.value })
    selectedScopes.value = [...detail.value.scopes]
    scopeCatalog.value = await openApiPortal.scopes({ app_id: appId })
    ElMessage.success('接口订阅已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    savingScopes.value = false
  }
}

async function issueKey() {
  const rotate = keys.value.length > 0
  try {
    await ElMessageBox.confirm(
      rotate
        ? '轮换会立即使现有密钥失效，调用方需同步更新；确定继续？'
        : '将生成新的 AppKey/AppSecret，AppSecret 只显示一次。确定继续？',
      rotate ? '轮换密钥' : '签发密钥',
      { type: 'warning' },
    )
  } catch {
    return
  }
  issuing.value = true
  try {
    issuedKey.value = await openApiPortal.issueKey(appId, {
      action: rotate ? 'ROTATE' : 'CREATE',
      grace_hours: 0,
    })
    secretVisible.value = true
    keys.value = await openApiPortal.keys(appId)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    issuing.value = false
  }
}

async function copy(value: string) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('当前浏览器不允许自动复制，请手动选择文本')
  }
}

function formatTime(value?: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}

function statusTag(status: string) {
  return status === 'ENABLED' ? 'success' : status === 'DISABLED' ? 'info' : 'warning'
}

function reviewTag(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function keyStatusTag(status: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'EXPIRED') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageHeader module="开放平台" title="应用详情" description="回调地址、接口订阅与密钥均按应用归属校验，服务端强制收敛。">
      <template #tags>
        <el-tag v-if="detail" :type="statusTag(detail.status)" size="small">{{ detail.status }}</el-tag>
        <el-tag v-if="detail" :type="reviewTag(detail.review_status)" size="small">{{ detail.review_status }}</el-tag>
      </template>
      <template #actions>
        <el-button @click="router.push({ name: 'apps' })">返回列表</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <template v-if="detail">
      <el-card shadow="never" class="section">
        <template #header><strong>基本信息</strong></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="应用 ID">{{ detail.app_id }}</el-descriptions-item>
          <el-descriptions-item label="应用编码">{{ detail.code }}</el-descriptions-item>
          <el-descriptions-item label="应用名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="应用类型">{{ detail.owner_type }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.created_at) }}</el-descriptions-item>
          <el-descriptions-item label="今日调用">{{ detail.call_today }}</el-descriptions-item>
          <el-descriptions-item label="日配额">{{ detail.quota_daily ?? '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="接口订阅数">{{ detail.scopes.length }}</el-descriptions-item>
          <el-descriptions-item label="回调地址数">{{ detail.redirect_uris.length }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="section">
        <template #header>
          <div class="card-head">
            <strong>回调地址</strong>
            <span class="muted">每行一个，必须以 http:// 或 https:// 开头，最多 10 个</span>
          </div>
        </template>
        <el-input
          v-model="callbackText"
          type="textarea"
          :rows="4"
          :disabled="!canManage"
          placeholder="https://example.com/openapi/callback"
        />
        <div class="actions">
          <el-button v-if="canManage" type="primary" :loading="savingCallback" @click="saveCallback">
            保存回调地址
          </el-button>
          <span v-else class="muted">缺少 biz:openapi:app:manage，无法修改。</span>
        </div>
      </el-card>

      <el-card shadow="never" class="section">
        <template #header>
          <div class="card-head">
            <strong>接口权限订阅</strong>
            <span class="muted">目录来源为 hub_permission 真实 API 权限点</span>
          </div>
        </template>
        <el-select
          v-model="selectedScopes"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :disabled="!canManage"
          placeholder="选择需要订阅的接口权限"
          style="width: 100%"
        >
          <el-option-group v-for="group in scopeGroups" :key="group.domain" :label="group.domain">
            <el-option
              v-for="scope in group.items"
              :key="scope.code"
              :label="`${scope.name}（${scope.code}）`"
              :value="scope.code"
            />
          </el-option-group>
        </el-select>
        <div class="actions">
          <el-button v-if="canManage" type="primary" :loading="savingScopes" @click="saveScopes">
            保存订阅
          </el-button>
          <span v-else class="muted">缺少 biz:openapi:app:manage，无法修改订阅。</span>
        </div>
      </el-card>

      <el-card shadow="never" class="section">
        <template #header>
          <div class="card-head">
            <strong>密钥管理</strong>
            <span class="muted">AppSecret 明文只显示一次，之后仅保留掩码与指纹</span>
          </div>
        </template>
        <el-alert
          v-if="!approved"
          type="warning"
          :closable="false"
          show-icon
          title="应用尚未审核通过，暂不能签发可用密钥"
          style="margin-bottom: 12px"
        />
        <el-table :data="keys" border stripe size="small">
          <el-table-column prop="app_key" label="AppKey" min-width="200" />
          <el-table-column prop="secret_masked" label="AppSecret" min-width="180" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="keyStatusTag(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="过期时间" width="180">
            <template #default="{ row }">{{ formatTime(row.expire_at) }}</template>
          </el-table-column>
          <template #empty><el-empty description="暂无密钥" /></template>
        </el-table>
        <div class="actions">
          <el-button
            v-if="canKey"
            type="primary"
            :loading="issuing"
            :disabled="!approved || !enabled"
            @click="issueKey"
          >
            {{ keys.length ? '轮换密钥' : '签发密钥' }}
          </el-button>
          <span v-if="!canKey" class="muted">缺少 biz:openapi:key:manage，无法签发或轮换密钥。</span>
          <span v-else-if="approved && !enabled" class="muted">应用未启用，无法签发可用密钥。</span>
        </div>
      </el-card>

      <el-card shadow="never" class="section">
        <template #header><strong>按应用查看</strong></template>
        <div class="actions" style="margin-top: 0">
          <el-button @click="router.push({ name: 'call-logs', query: { app_id: String(detail.app_id) } })">
            调用日志
          </el-button>
          <el-button @click="router.push({ name: 'quotas', query: { app_id: String(detail.app_id) } })">
            配额与用量
          </el-button>
        </div>
      </el-card>
    </template>

    <el-dialog v-model="secretVisible" title="密钥已签发" width="560px" :close-on-click-modal="false">
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="AppSecret 只显示这一次，关闭后无法再次查看，请立即妥善保存。"
        style="margin-bottom: 12px"
      />
      <template v-if="issuedKey">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="AppKey">
            <span class="mono">{{ issuedKey.app_key }}</span>
            <el-button link type="primary" @click="copy(issuedKey.app_key)">复制</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="AppSecret">
            <span class="mono">{{ issuedKey.app_secret }}</span>
            <el-button link type="primary" @click="copy(issuedKey.app_secret)">复制</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="指纹">{{ issuedKey.secret_fingerprint ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="签发时间">{{ formatTime(issuedKey.issued_at) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section {
  margin-bottom: 12px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.mono {
  font-family: var(--fl-font-mono, monospace);
  word-break: break-all;
}
</style>
