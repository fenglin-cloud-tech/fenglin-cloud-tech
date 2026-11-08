<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { summarizeSchema } from '@fenglin/lowcode-runtime'
import { lowcodeApi, type LowcodeAppView, type LowcodeVersionView } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canDesign = () => auth.can('tenant:lowcode:app:design')
const canPublish = () => auth.can('tenant:lowcode:app:publish')

const rows = ref<LowcodeAppView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ env: '', status: '', page: 1, size: 10 })

const createVisible = ref(false)
const saving = ref(false)
const formError = ref('')
const form = reactive({ code: '', name: '', app_type: 'FORM', env: 'DEV', schemaText: '{\n  "fields": []\n}', dataModelText: '{\n  "fields": [],\n  "publishProcessKey": ""\n}' })

const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versionsError = ref('')
const versions = ref<LowcodeVersionView[]>([])
const versionTarget = ref<LowcodeAppView | null>(null)
const rollbackVersion = ref<number | undefined>(undefined)

const previewVisible = ref(false)
const previewApp = ref<LowcodeAppView | null>(null)
const previewSummary = ref(summarizeSchema(null))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await lowcodeApi.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { code: '', name: '', app_type: 'FORM', env: 'DEV', schemaText: '{\n  "fields": []\n}', dataModelText: '{\n  "fields": [],\n  "publishProcessKey": ""\n}' })
  formError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!form.code || !form.name) {
    formError.value = '应用编码与名称为必填项'
    return
  }
  let schema: unknown
  let dataModel: unknown
  try {
    schema = JSON.parse(form.schemaText)
    dataModel = JSON.parse(form.dataModelText)
  } catch {
    formError.value = 'schema 或 dataModel 不是合法 JSON'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await lowcodeApi.create({
      code: form.code,
      name: form.name,
      app_type: form.app_type,
      env: form.env,
      schema,
      data_model: dataModel,
    })
    ElMessage.success('低代码应用已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function publish(row: LowcodeAppView) {
  try {
    await ElMessageBox.confirm(`确认发布应用「${row.name}」？重复发布将幂等返回当前版本。`, '发布确认', { type: 'warning' })
    const result = await lowcodeApi.publish(row.id)
    ElMessage.success(`已发布，版本 ${result.version_no}${result.process_instance_id ? `，审批实例 ${result.process_instance_id}` : ''}`)
    await load()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

async function openVersions(row: LowcodeAppView) {
  versionTarget.value = row
  versionsVisible.value = true
  versionsLoading.value = true
  versionsError.value = ''
  try {
    versions.value = await lowcodeApi.versions(row.id)
    rollbackVersion.value = row.latest_version || undefined
  } catch (err) {
    versionsError.value = describeError(err)
  } finally {
    versionsLoading.value = false
  }
}

async function doRollback() {
  if (!versionTarget.value || !rollbackVersion.value) return
  try {
    await ElMessageBox.confirm(`回滚到版本 ${rollbackVersion.value}？`, '回滚确认', { type: 'warning' })
    await lowcodeApi.rollback(versionTarget.value.id, rollbackVersion.value)
    ElMessage.success('回滚成功')
    versionsVisible.value = false
    await load()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

function openPreview(row: LowcodeAppView) {
  previewApp.value = row
  previewSummary.value = summarizeSchema(row.schema)
  previewVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="低代码中台 · A-25" title="低代码" description="应用创建、发布、版本与回滚；schema 为后端自由 JSON。">
      <template #actions>
        <el-button v-perm="'tenant:lowcode:app:design'" type="primary" @click="openCreate">新建应用</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        'A-25 低代码设计器：后端仅存储自由 JSON schema，未定义可视化设计器协议；管理台提供 JSON 编辑与防御式预览，不臆造渲染规则。',
        '发布审批通过 data_model.publishProcessKey 关联工作流；未配置流程键时直接发布。',
        '版本回滚为扩展接口（总纲无接口编号），需 tenant:lowcode:app:publish 权限。',
        '当前权限：设计=' + (canDesign() ? '具备' : '缺失') + '，发布/回滚=' + (canPublish() ? '具备' : '缺失') + '。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-select v-model="query.env" placeholder="全部环境" clearable style="width: 140px">
          <el-option label="DEV" value="DEV" />
          <el-option label="TEST" value="TEST" />
          <el-option label="PROD" value="PROD" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="DRAFT" value="DRAFT" />
          <el-option label="PUBLISHED" value="PUBLISHED" />
          <el-option label="OFFLINE" value="OFFLINE" />
        </el-select>
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
      </div>
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="app_type" label="类型" width="100" />
        <el-table-column prop="env" label="环境" width="90" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="latest_version" label="最新版本" width="100" />
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">{{ row.published_at ? new Date(row.published_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button v-perm="'tenant:lowcode:app:publish'" link type="success" @click="publish(row)">发布</el-button>
            <el-button link @click="openVersions(row)">版本</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <el-dialog v-model="createVisible" title="新建低代码应用" width="760px">
      <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="应用编码" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="应用名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="应用类型">
          <el-select v-model="form.app_type" style="width: 100%">
            <el-option label="FORM" value="FORM" />
            <el-option label="FLOW" value="FLOW" />
            <el-option label="REPORT" value="REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="form.env" style="width: 100%">
            <el-option label="DEV" value="DEV" />
            <el-option label="TEST" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="schema JSON"><el-input v-model="form.schemaText" type="textarea" :rows="8" class="fl-mono" /></el-form-item>
        <el-form-item label="数据模型"><el-input v-model="form.dataModelText" type="textarea" :rows="6" class="fl-mono" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionsVisible" title="版本与回滚" width="720px">
      <div v-if="versionTarget" class="fl-muted" style="margin-bottom: 12px">
        应用：{{ versionTarget.name }}（{{ versionTarget.code }}）
      </div>
      <el-alert v-if="versionsError" type="error" :closable="false" show-icon :title="versionsError" style="margin-bottom: 12px" />
      <div class="fl-toolbar">
        <el-input-number v-model="rollbackVersion" :min="1" placeholder="版本号" />
        <el-button v-perm="'tenant:lowcode:app:publish'" type="warning" @click="doRollback">回滚到该版本</el-button>
      </div>
      <el-table :data="versions" v-loading="versionsLoading" border size="small" max-height="360">
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="process_instance_id" label="审批实例" min-width="160" />
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">{{ row.published_at ? new Date(row.published_at).toLocaleString() : '-' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-drawer v-model="previewVisible" title="schema 预览" size="520px">
      <div v-if="previewApp">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="应用">{{ previewApp.name }}（{{ previewApp.code }}）</el-descriptions-item>
          <el-descriptions-item label="字段数">{{ previewSummary.fieldCount }}</el-descriptions-item>
          <el-descriptions-item label="最新版本">{{ previewApp.latest_version }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-for="warning in previewSummary.warnings"
          :key="warning"
          type="warning"
          :closable="false"
          show-icon
          :title="warning"
          style="margin-top: 12px"
        />
        <el-table v-if="previewSummary.fields.length" :data="previewSummary.fields" border size="small" style="margin-top: 12px">
          <el-table-column prop="name" label="字段" />
          <el-table-column prop="label" label="标签" />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="required" label="必填" width="80" />
        </el-table>
        <h4>原始 schema</h4>
        <pre class="fl-pre">{{ JSON.stringify(previewApp.schema ?? {}, null, 2) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
h4 {
  margin: 16px 0 8px;
  font-size: 13px;
}
</style>
