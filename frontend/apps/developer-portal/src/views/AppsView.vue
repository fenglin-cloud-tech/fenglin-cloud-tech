<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { openApiPortal, type OpenAppCreateResponse, type PortalAppView } from '@fenglin/api-client'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const router = useRouter()
const auth = useAuthStore()

const rows = ref<PortalAppView[]>([])
const loading = ref(false)
const error = ref('')

const canView = computed(() => auth.can('biz:openapi:portal:view'))
const canManage = computed(() => auth.can('biz:openapi:app:manage'))

const totalCalls = computed(() => rows.value.reduce((sum, row) => sum + (row.call_today ?? 0), 0))
const enabledCount = computed(() => rows.value.filter((row) => row.status === 'ENABLED').length)
const pendingCount = computed(() => rows.value.filter((row) => row.review_status === 'PENDING').length)

const gapItems = [
  '1001 原始注册接口位于管理端 /api/hub/v1/admin/openapi/apps，PORTAL 端不可访问；门户使用等价接口 POST /openapi/portal/apps，应用归属当前登录用户。',
  '创建后应用为 PENDING / DISABLED，需平台运营在管理端完成 1002 审核后才会启用；审核通过前无法签发可用密钥。',
  '应用审核、停用、配额配置属于管理端能力，开发者门户不提供越权操作。',
]

const createVisible = ref(false)
const createSubmitting = ref(false)
const created = ref<OpenAppCreateResponse | null>(null)
const form = reactive({
  name: '',
  owner_type: 'INTERNAL',
})

async function load() {
  if (!canView.value) {
    rows.value = []
    error.value = '当前账号缺少 biz:openapi:portal:view，无法查看应用。'
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await openApiPortal.apps()
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.name = ''
  form.owner_type = 'INTERNAL'
  created.value = null
  createVisible.value = true
}

async function submitCreate() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入应用名称')
    return
  }
  createSubmitting.value = true
  try {
    created.value = await openApiPortal.createApp({
      name: form.name.trim(),
      owner_type: form.owner_type,
    })
    ElMessage.success('应用已创建，等待平台审核')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    createSubmitting.value = false
  }
}

function goDetail(row: PortalAppView) {
  router.push({ name: 'app-detail', params: { id: row.app_id } })
}

function goCreatedDetail() {
  if (!created.value) return
  const id = created.value.app_id
  createVisible.value = false
  router.push({ name: 'app-detail', params: { id } })
}

function statusTag(status: string) {
  return status === 'ENABLED' ? 'success' : status === 'DISABLED' ? 'info' : 'warning'
}

function reviewTag(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="开放平台" title="我的应用" description="查看本人创建的应用、审核状态与今日用量。">
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">创建应用</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <div class="summary">
      <span>应用数：<strong>{{ rows.length }}</strong></span>
      <span>已启用：<strong>{{ enabledCount }}</strong></span>
      <span>待审核：<strong>{{ pendingCount }}</strong></span>
      <span>今日调用：<strong>{{ totalCalls }}</strong></span>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="app_id" label="应用 ID" width="110" />
      <el-table-column prop="name" label="应用名称" min-width="160" />
      <el-table-column prop="code" label="应用编码" min-width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核" width="110">
        <template #default="{ row }">
          <el-tag :type="reviewTag(row.review_status)" size="small">{{ row.review_status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="call_today" label="今日调用" width="110" />
      <el-table-column label="日配额" width="130">
        <template #default="{ row }">{{ row.quota_daily ?? '未配置' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无应用。创建后需等待平台审核。" />
      </template>
    </el-table>

    <el-dialog v-model="createVisible" title="创建应用" width="520px">
      <template v-if="created">
        <el-result icon="success" title="应用已创建" sub-title="状态为 PENDING / DISABLED，等待平台审核。">
          <template #extra>
            <p>应用编码：<strong>{{ created.code }}</strong></p>
            <p>应用 ID：{{ created.app_id }}</p>
            <el-button type="primary" @click="goCreatedDetail">前往应用详情</el-button>
          </template>
        </el-result>
      </template>
      <el-form v-else label-width="92px">
        <el-form-item label="应用名称" required>
          <el-input v-model="form.name" maxlength="128" show-word-limit placeholder="例如：营销数据同步" />
        </el-form-item>
        <el-form-item label="应用类型">
          <el-select v-model="form.owner_type" style="width: 100%">
            <el-option label="内部系统（INTERNAL）" value="INTERNAL" />
            <el-option label="服务商（ISV）" value="ISV" />
            <el-option label="客户自建（CUSTOMER）" value="CUSTOMER" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <span class="muted">回调地址与接口订阅在创建后于应用详情中配置。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <template v-if="created">
          <el-button @click="createVisible = false">关闭</el-button>
        </template>
        <template v-else>
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" :loading="createSubmitting" @click="submitCreate">提交创建</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid var(--fl-color-border);
  border-radius: var(--fl-radius-md);
  font-size: 13px;
  color: var(--fl-color-text-secondary);
}
.summary strong {
  color: var(--fl-color-text-primary);
  font-size: 16px;
}
.muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
