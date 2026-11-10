<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { dataLevel, fmtTime, label, releaseStatus } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const filters = reactive({ status: '', from: '', to: '' })

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({
  channel_code: 'EMAIL',
  file_name: '',
  target: '',
  data_level: 'L3',
  purpose: '',
  content_fingerprint: '',
})

const drawer = ref(false)
const detail = ref<LegalRecord | null>(null)
const timeline = ref<LegalRecord[]>([])
const detailLoading = ref(false)

const gapItems = [
  '本页调用 /dlp/releases；无 legal:dlp:release:approve 时后端强制 applicant 为当前用户，只有 legal:dlp:release:apply 也能看到本人申请。',
  '放行申请复用 legal_dlp_event(event_type=RELEASE_APPLY)，不另建表；可按申请人、状态、时间筛选。',
  '用途会写入 matched_snippet（详情/列表显示的“用途说明”），放行对象仅传给审批工作流、不落事件表。',
  '提交会真实调用审批工作流 legalDlpReleaseApproval；工作流不可用时会返回真实错误，不做前端假成功。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.dlp.releases({
      status: filters.status || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page: page.value,
      size: 20,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.file_name.trim() || !form.channel_code.trim()) {
    ElMessage.warning('通道编码与文件名必填')
    return
  }
  saving.value = true
  try {
    await legalApi.dlp.createRelease({
      channel_code: form.channel_code.trim(),
      file_name: form.file_name.trim(),
      target: form.target || undefined,
      data_level: form.data_level,
      purpose: form.purpose || undefined,
      content_fingerprint: form.content_fingerprint || undefined,
    })
    ElMessage.success('放行申请已提交')
    createVisible.value = false
    form.file_name = ''
    form.target = ''
    form.purpose = ''
    form.content_fingerprint = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function openDetail(row: LegalRecord) {
  drawer.value = true
  detailLoading.value = true
  try {
    const [d, p] = await Promise.all([legalApi.dlp.release(Number(row.id)), legalApi.dlp.releaseProgress(Number(row.id))])
    detail.value = d
    timeline.value = p.timeline ?? p.progress ?? []
  } catch (err) {
    detail.value = null
    timeline.value = []
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="数据放行申请"
      description="敏感数据外发放行的提交、查询与进度；数据来自 /api/legal/v1/dlp/releases。"
    >
      <template #actions>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px" @change="load">
          <el-option v-for="(text, code) in releaseStatus" :key="code" :label="text" :value="code" />
        </el-select>
        <el-date-picker v-model="filters.from" type="date" value-format="YYYY-MM-DD" placeholder="起始" style="width: 140px" />
        <el-date-picker v-model="filters.to" type="date" value-format="YYYY-MM-DD" placeholder="截止" style="width: 140px" />
        <el-button type="primary" @click="createVisible = true">发起放行</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="event_no" label="申请编号" width="170" />
        <el-table-column prop="channel_code" label="通道" width="120" />
        <el-table-column prop="file_name" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column label="级别" width="100">
          <template #default="{ row }">{{ label(dataLevel, row.data_level) }}</template>
        </el-table-column>
        <el-table-column prop="matched_snippet" label="用途说明" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ label(releaseStatus, row.status) }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.occurred_at ?? row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="20"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="发起数据放行申请" width="560px">
      <el-form label-width="100px">
        <el-form-item label="通道编码" required><el-input v-model="form.channel_code" placeholder="EMAIL / WECHAT / USB" /></el-form-item>
        <el-form-item label="文件名" required><el-input v-model="form.file_name" /></el-form-item>
        <el-form-item label="放行对象"><el-input v-model="form.target" placeholder="接收人/外部系统，仅随审批流传递" /></el-form-item>
        <el-form-item label="数据级别">
          <el-select v-model="form.data_level" style="width: 100%">
            <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="内容指纹"><el-input v-model="form.content_fingerprint" placeholder="SHA-256，可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" title="放行申请详情" size="600px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="申请编号">{{ detail.event_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ label(releaseStatus, detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="通道">{{ detail.channel_code ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="级别">{{ label(dataLevel, detail.data_level) }}</el-descriptions-item>
            <el-descriptions-item label="文件名" :span="2">{{ detail.file_name ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="用途说明" :span="2">{{ detail.matched_snippet ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="内容指纹" :span="2">{{ detail.file_fingerprint ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请时间" :span="2">{{ fmtTime(detail.occurred_at ?? detail.created_at) }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">审批进度</el-divider>
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in timeline"
              :key="index"
              :timestamp="fmtTime(item.occurred_at ?? item.created_at)"
              placement="top"
            >
              <div>{{ item.action ?? '-' }} / {{ item.result ?? item.status ?? '-' }}</div>
              <div class="muted">操作人：{{ item.operator_id ?? '-' }}；{{ item.detail ?? item.comment ?? '' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="timeline.length === 0" description="暂无审批进度" :image-size="60" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
