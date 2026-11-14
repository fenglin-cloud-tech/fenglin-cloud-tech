<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const tab = ref('apps')
const error = ref('')

const tabs = [
  { key: 'apps', label: '应用治理', perm: 'tenant:service:app:govern' },
  { key: 'releases', label: '发布审批', perm: 'service:lowcode:app:publish' },
  { key: 'templates', label: '模板', perm: 'service:lowcode:template:use' },
]
const visibleTabs = computed(() => tabs.filter((item) => auth.can(item.perm)))

const gapItems = [
  '发布记录列表的 total 由后端按当前页行数返回（不是总数），分页只能逐页翻，无法显示准确总数。',
  '表单/流程/模型/页面设计器未纳入本页，只做应用治理与发布审批；设计器接口需按 appId 单独调用。',
  '发布审批通过后应用状态改为 PUBLISHED，若审批流实例失败需在审批中心处理。',
]

// ---------- 应用 ----------
const apps = ref<ServiceRecord[]>([])
const appTotal = ref(0)
const appPage = ref(1)
const appSize = ref(20)
const appStatus = ref('')
const appKeyword = ref('')
const appLoading = ref(false)

const appVisible = ref(false)
const appSaving = ref(false)
const appForm = reactive({ app_code: '', name: '', description: '' })

async function loadApps() {
  appLoading.value = true
  error.value = ''
  try {
    const result = await serviceApi.lowcode.adminApps({
      status: appStatus.value || undefined,
      keyword: appKeyword.value || undefined,
      page: appPage.value,
      size: appSize.value,
    })
    apps.value = result.records
    appTotal.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    appLoading.value = false
  }
}

async function createApp() {
  if (!appForm.name.trim()) {
    ElMessage.warning('应用名称必填')
    return
  }
  appSaving.value = true
  try {
    await serviceApi.lowcode.createApp({
      app_code: appForm.app_code.trim() || undefined,
      name: appForm.name.trim(),
      description: appForm.description || undefined,
    })
    ElMessage.success('应用已创建')
    appVisible.value = false
    await loadApps()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    appSaving.value = false
  }
}

async function copyApp(row: ServiceRecord) {
  try {
    await serviceApi.lowcode.copyApp(row.id)
    ElMessage.success('已复制应用')
    await loadApps()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function archiveApp(row: ServiceRecord) {
  try {
    await serviceApi.lowcode.archiveApp(row.id)
    ElMessage.success('应用已归档')
    await loadApps()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function freezeApp(row: ServiceRecord) {
  try {
    await serviceApi.lowcode.freezeApp(row.id)
    ElMessage.success('应用已冻结')
    await loadApps()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

// ---------- 发布 ----------
const releases = ref<ServiceRecord[]>([])
const releaseLoading = ref(false)
const releasePage = ref(1)
const releaseSize = ref(20)
const releaseHasNext = ref(false)
const releaseAppId = ref<number | undefined>()
const releaseVisible = ref(false)
const releaseSaving = ref(false)
const releaseForm = reactive({ to_env: 'PROD', release_note: '' })

async function loadReleases() {
  releaseLoading.value = true
  try {
    const result = await serviceApi.lowcode.releases({
      appId: releaseAppId.value,
      page: releasePage.value,
      size: releaseSize.value,
    })
    releases.value = result.records
    releaseHasNext.value = result.records.length === releaseSize.value
  } catch (err) {
    error.value = describeError(err)
  } finally {
    releaseLoading.value = false
  }
}

async function submitRelease() {
  if (!releaseAppId.value) {
    ElMessage.warning('请填写应用 ID')
    return
  }
  releaseSaving.value = true
  try {
    const created = await serviceApi.lowcode.submitRelease(releaseAppId.value, {
      to_env: releaseForm.to_env,
      release_note: releaseForm.release_note || undefined,
    })
    ElMessage.success(`发布单已提交：${created.release_no ?? '-'}`)
    releaseVisible.value = false
    await loadReleases()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    releaseSaving.value = false
  }
}

async function approveRelease(row: ServiceRecord, approved: boolean) {
  try {
    await serviceApi.lowcode.approveRelease(row.id, { approved })
    ElMessage.success(approved ? '已通过' : '已驳回')
    await loadReleases()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

// ---------- 模板 ----------
const templates = ref<ServiceRecord[]>([])
const templateLoading = ref(false)

async function loadTemplates() {
  templateLoading.value = true
  try {
    templates.value = await serviceApi.lowcode.templates()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    templateLoading.value = false
  }
}

async function switchTab(key: string) {
  tab.value = key
  if (key === 'apps') await loadApps()
  if (key === 'releases') await loadReleases()
  if (key === 'templates') await loadTemplates()
}

onMounted(async () => {
  if (visibleTabs.value.length) await switchTab(visibleTabs.value[0].key)
})
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="低代码应用"
      description="应用治理、发布审批与模板；数据来自 /api/service/v1/lowcode 与 /admin/lowcode。"
    >
      <template #actions>
        <el-button v-perm="'service:lowcode:app:create'" plain @click="appVisible = true">新建应用</el-button>
        <el-button v-perm="'service:lowcode:app:publish'" type="primary" @click="releaseVisible = true">提交发布</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />
    <el-alert
      v-if="visibleTabs.length === 0"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号没有低代码治理相关权限点，页面不展示数据。"
    />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane v-for="item in visibleTabs" :key="item.key" :label="item.label" :name="item.key" />
    </el-tabs>

    <el-card v-if="tab === 'apps'" shadow="never">
      <div class="fl-toolbar" style="margin-bottom: 8px">
        <el-space>
          <el-input v-model="appKeyword" placeholder="应用名称/编码" clearable style="width: 200px" @keyup.enter="loadApps" />
          <el-select v-model="appStatus" placeholder="全部状态" clearable style="width: 160px" @change="loadApps">
            <el-option v-for="status in ['DRAFT', 'PUBLISHED', 'FROZEN', 'ARCHIVED']" :key="status" :label="status" :value="status" />
          </el-select>
          <el-button @click="loadApps">查询</el-button>
        </el-space>
      </div>
      <el-table :data="apps" v-loading="appLoading" border stripe size="small">
        <el-table-column prop="app_code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="env" label="环境" width="100" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="owner_id" label="负责人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="script_risk_level" label="脚本风险" width="110" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-perm="'service:lowcode:app:create'" link type="primary" @click="copyApp(row)">复制</el-button>
            <el-button v-perm="'service:lowcode:app:create'" link type="warning" @click="archiveApp(row)">归档</el-button>
            <el-button v-perm="'tenant:service:app:govern'" link type="danger" @click="freezeApp(row)">冻结</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="appTotal"
        :page-size="appSize"
        :current-page="appPage"
        @current-change="(next: number) => { appPage = next; loadApps() }"
      />
    </el-card>

    <el-card v-if="tab === 'releases'" shadow="never">
      <div class="fl-toolbar" style="margin-bottom: 8px">
        <el-space>
          <el-input-number v-model="releaseAppId" placeholder="应用ID" :min="1" style="width: 160px" />
          <el-button @click="releasePage = 1; loadReleases()">按应用筛选</el-button>
          <el-button @click="releaseAppId = undefined; releasePage = 1; loadReleases()">全部</el-button>
        </el-space>
      </div>
      <el-table :data="releases" v-loading="releaseLoading" border stripe size="small">
        <el-table-column prop="release_no" label="发布号" width="170" />
        <el-table-column prop="app_id" label="应用ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="from_env" label="来源环境" width="110" />
        <el-table-column prop="to_env" label="目标环境" width="110" />
        <el-table-column prop="approval_status" label="审批" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="release_note" label="说明" min-width="160" />
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <template v-if="row.approval_status === 'PENDING'">
              <el-button v-perm="'tenant:service:app:approve'" link type="primary" @click="approveRelease(row, true)">通过</el-button>
              <el-button v-perm="'tenant:service:app:approve'" link type="danger" @click="approveRelease(row, false)">驳回</el-button>
            </template>
            <span v-else class="fl-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="prev, pager, next"
        :total="releaseHasNext ? releasePage * releaseSize + 1 : (releasePage - 1) * releaseSize + releases.length"
        :page-size="releaseSize"
        :current-page="releasePage"
        @current-change="(next: number) => { releasePage = next; loadReleases() }"
      />
    </el-card>

    <el-card v-if="tab === 'templates'" shadow="never">
      <el-table :data="templates" v-loading="templateLoading" border stripe size="small">
        <el-table-column prop="name" label="模板名称" min-width="180" />
        <el-table-column prop="template_type" label="类型" width="120" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column prop="created_at" label="创建时间" width="170" />
      </el-table>
    </el-card>

    <el-dialog v-model="appVisible" title="新建应用" width="500px">
      <el-form label-width="100px">
        <el-form-item label="应用编码"><el-input v-model="appForm.app_code" placeholder="留空由后端生成" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="appForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="appForm.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appVisible = false">取消</el-button>
        <el-button type="primary" :loading="appSaving" @click="createApp">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="releaseVisible" title="提交发布" width="500px">
      <el-form label-width="100px">
        <el-form-item label="应用ID"><el-input-number v-model="releaseAppId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="目标环境">
          <el-select v-model="releaseForm.to_env" style="width: 100%">
            <el-option label="生产 PROD" value="PROD" />
            <el-option label="预发 STAGING" value="STAGING" />
            <el-option label="测试 TEST" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布说明"><el-input v-model="releaseForm.release_note" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="releaseVisible = false">取消</el-button>
        <el-button type="primary" :loading="releaseSaving" @click="submitRelease">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
