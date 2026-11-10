<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { dataLevel, eventStatus, fmtTime, label } from './labels'

const tab = ref('events')

const events = ref<LegalRecord[]>([])
const eventTotal = ref(0)
const eventLoading = ref(false)
const eventError = ref('')
const eventFilter = reactive({ data_level: '', channel_code: '', status: '', event_type: '' })

const eventDrawer = ref(false)
const eventDetail = ref<LegalRecord | null>(null)
const eventDetailLoading = ref(false)
const handleVisible = ref(false)
const handleSaving = ref(false)
const handleForm = reactive({ id: 0, action: 'TRIAGE', conclusion: '', rectify_item: '', sla_deadline: '' })

const releases = ref<LegalRecord[]>([])
const releaseTotal = ref(0)
const releaseLoading = ref(false)
const releaseError = ref('')
const releaseFilter = reactive({ applicant_id: '', status: '', from: '', to: '' })

const releaseDrawer = ref(false)
const releaseDetail = ref<LegalRecord | null>(null)
const releaseTimeline = ref<LegalRecord[]>([])
const releaseDetailLoading = ref(false)
const approveVisible = ref(false)
const approveSaving = ref(false)
const approveForm = reactive({ id: 0, action: 'APPROVE', comment: '' })

const gapItems = [
  '后端事件查询实际校验 legal:dlp:audit:view（不是 legal:dlp:event:view），处置校验 legal:dlp:event:handle；页面权限码已按后端真实校验对齐。',
  '放行申请查询复用 legal_dlp_event(event_type=RELEASE_APPLY)，不另建表；审批需 legal:dlp:release:approve，非审批人只能看本人申请。',
  '放行申请的“用途”在后端落在 matched_snippet 字段，页面按真实字段展示；target 只传给审批工作流，不落库。',
]

async function loadEvents() {
  eventLoading.value = true
  eventError.value = ''
  try {
    const result = await legalApi.dlp.events({
      data_level: eventFilter.data_level || undefined,
      channel_code: eventFilter.channel_code || undefined,
      status: eventFilter.status || undefined,
      event_type: eventFilter.event_type || undefined,
      page: 1,
      size: 50,
    })
    events.value = result.records
    eventTotal.value = result.total
  } catch (err) {
    events.value = []
    eventError.value = describeError(err)
  } finally {
    eventLoading.value = false
  }
}

async function openEvent(row: LegalRecord) {
  eventDrawer.value = true
  eventDetailLoading.value = true
  try {
    eventDetail.value = await legalApi.dlp.event(Number(row.id))
  } catch (err) {
    eventDetail.value = null
    ElMessage.error(describeError(err))
  } finally {
    eventDetailLoading.value = false
  }
}

function openHandle(row: LegalRecord, action: string) {
  handleForm.id = Number(row.id)
  handleForm.action = action
  handleForm.conclusion = ''
  handleForm.rectify_item = ''
  handleForm.sla_deadline = ''
  handleVisible.value = true
}

async function submitHandle() {
  handleSaving.value = true
  try {
    await legalApi.dlp.handleEvent(handleForm.id, {
      action: handleForm.action,
      conclusion: handleForm.conclusion || undefined,
      rectify_item: handleForm.rectify_item || undefined,
      sla_deadline: handleForm.sla_deadline || undefined,
    })
    ElMessage.success('事件已处置')
    handleVisible.value = false
    await loadEvents()
    if (eventDrawer.value && eventDetail.value) await openEvent(eventDetail.value)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    handleSaving.value = false
  }
}

async function loadReleases() {
  releaseLoading.value = true
  releaseError.value = ''
  try {
    const result = await legalApi.dlp.releases({
      applicant_id: releaseFilter.applicant_id ? Number(releaseFilter.applicant_id) : undefined,
      status: releaseFilter.status || undefined,
      from: releaseFilter.from || undefined,
      to: releaseFilter.to || undefined,
      page: 1,
      size: 50,
    })
    releases.value = result.records
    releaseTotal.value = result.total
  } catch (err) {
    releases.value = []
    releaseError.value = describeError(err)
  } finally {
    releaseLoading.value = false
  }
}

async function openRelease(row: LegalRecord) {
  releaseDrawer.value = true
  releaseDetailLoading.value = true
  try {
    const [detail, progress] = await Promise.all([
      legalApi.dlp.release(Number(row.id)),
      legalApi.dlp.releaseProgress(Number(row.id)),
    ])
    releaseDetail.value = detail
    releaseTimeline.value = progress.timeline ?? progress.progress ?? []
  } catch (err) {
    releaseDetail.value = null
    releaseTimeline.value = []
    ElMessage.error(describeError(err))
  } finally {
    releaseDetailLoading.value = false
  }
}

function openApprove(row: LegalRecord, action: string) {
  approveForm.id = Number(row.id)
  approveForm.action = action
  approveForm.comment = ''
  approveVisible.value = true
}

async function submitApprove() {
  approveSaving.value = true
  try {
    await legalApi.dlp.approveRelease(approveForm.id, { action: approveForm.action, comment: approveForm.comment || undefined })
    ElMessage.success(approveForm.action === 'APPROVE' ? '放行申请已通过' : '放行申请已驳回')
    approveVisible.value = false
    await loadReleases()
    if (releaseDrawer.value && releaseDetail.value) await openRelease(releaseDetail.value)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    approveSaving.value = false
  }
}

function switchTab(value: string) {
  tab.value = value
  if (value === 'releases' && releases.value.length === 0) loadReleases()
}

onMounted(loadEvents)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="DLP 事件处置"
      description="敏感数据事件查询与处置、放行申请审批与进度；数据来自 /api/legal/v1/dlp/*。"
    >
      <template #actions>
        <el-button :loading="eventLoading" @click="loadEvents">刷新事件</el-button>
        <el-button :loading="releaseLoading" plain @click="loadReleases">刷新放行</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane label="敏感事件" name="events">
        <el-alert v-if="eventError" type="error" :closable="false" show-icon :title="eventError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-select v-model="eventFilter.data_level" placeholder="级别" clearable style="width: 120px">
              <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
            </el-select>
            <el-input v-model="eventFilter.channel_code" placeholder="通道编码" clearable style="width: 140px" />
            <el-input v-model="eventFilter.event_type" placeholder="事件类型" clearable style="width: 140px" />
            <el-select v-model="eventFilter.status" placeholder="状态" clearable style="width: 130px">
              <el-option label="待处理" value="NEW" />
              <el-option label="分级中" value="TRIAGING" />
              <el-option label="调查中" value="INVESTIGATING" />
              <el-option label="已处置" value="HANDLED" />
              <el-option label="误报" value="FALSE_POSITIVE" />
              <el-option label="已升级" value="ESCALATED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
            <el-button :loading="eventLoading" @click="loadEvents">查询</el-button>
          </div>
          <el-table :data="events" v-loading="eventLoading" border stripe max-height="520">
            <el-table-column prop="event_no" label="事件编号" width="170" />
            <el-table-column prop="event_type" label="类型" width="140" />
            <el-table-column label="级别" width="100">
              <template #default="{ row }">{{ label(dataLevel, row.data_level) }}</template>
            </el-table-column>
            <el-table-column prop="channel_code" label="通道" width="120" />
            <el-table-column prop="user_id" label="用户" width="90" />
            <el-table-column prop="file_name" label="文件" min-width="150" show-overflow-tooltip />
            <el-table-column prop="action_taken" label="处置动作" width="120" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">{{ label(eventStatus, row.status) }}</template>
            </el-table-column>
            <el-table-column prop="risk_score" label="风险分" width="90" />
            <el-table-column label="发生时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.occurred_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="210">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEvent(row)">详情</el-button>
                <el-button v-perm="'legal:dlp:event:handle'" link type="warning" @click="openHandle(row, 'TRIAGE')">分级</el-button>
                <el-button v-perm="'legal:dlp:event:handle'" link type="success" @click="openHandle(row, 'CONFIRM')">确认</el-button>
                <el-button v-perm="'legal:dlp:event:handle'" link type="info" @click="openHandle(row, 'FALSE_POSITIVE')">误报</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ eventTotal }} 条</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="放行申请" name="releases">
        <el-alert v-if="releaseError" type="error" :closable="false" show-icon :title="releaseError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="releaseFilter.applicant_id" placeholder="申请人ID" clearable style="width: 130px" />
            <el-select v-model="releaseFilter.status" placeholder="状态" clearable style="width: 120px">
              <el-option label="待审批" value="PENDING" />
              <el-option label="已放行" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
            <el-date-picker v-model="releaseFilter.from" type="date" value-format="YYYY-MM-DD" placeholder="起始日期" style="width: 150px" />
            <el-date-picker v-model="releaseFilter.to" type="date" value-format="YYYY-MM-DD" placeholder="截止日期" style="width: 150px" />
            <el-button :loading="releaseLoading" @click="loadReleases">查询</el-button>
          </div>
          <el-table :data="releases" v-loading="releaseLoading" border stripe max-height="520">
            <el-table-column prop="event_no" label="申请编号" width="170" />
            <el-table-column prop="user_id" label="申请人" width="90" />
            <el-table-column prop="file_name" label="文件" min-width="160" show-overflow-tooltip />
            <el-table-column label="级别" width="100">
              <template #default="{ row }">{{ label(dataLevel, row.data_level) }}</template>
            </el-table-column>
            <el-table-column prop="matched_snippet" label="用途" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ label(eventStatus, row.status) }}</template>
            </el-table-column>
            <el-table-column label="申请时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.occurred_at ?? row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRelease(row)">详情/进度</el-button>
                <template v-if="row.status === 'PENDING'">
                  <el-button v-perm="'legal:dlp:release:approve'" link type="success" @click="openApprove(row, 'APPROVE')">通过</el-button>
                  <el-button v-perm="'legal:dlp:release:approve'" link type="danger" @click="openApprove(row, 'REJECT')">驳回</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ releaseTotal }} 条</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="eventDrawer" title="事件详情" size="600px">
      <div v-loading="eventDetailLoading">
        <el-descriptions v-if="eventDetail" :column="2" border size="small">
          <el-descriptions-item label="事件编号">{{ eventDetail.event_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ label(eventStatus, eventDetail.status) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ eventDetail.event_type }}</el-descriptions-item>
          <el-descriptions-item label="级别">{{ label(dataLevel, eventDetail.data_level) }}</el-descriptions-item>
          <el-descriptions-item label="通道">{{ eventDetail.channel_code ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ eventDetail.user_id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件" :span="2">{{ eventDetail.file_name ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="匹配片段" :span="2">{{ eventDetail.matched_snippet ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="处置动作">{{ eventDetail.action_taken ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="风险分">{{ eventDetail.risk_score ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ fmtTime(eventDetail.occurred_at) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ eventDetail.source ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">处置记录</el-divider>
        <el-table :data="eventDetail?.handles ?? eventDetail?.audit_logs ?? []" size="small" border>
          <el-table-column prop="action" label="动作" width="100" />
          <el-table-column prop="handler_id" label="处理人" width="90" />
          <el-table-column prop="conclusion" label="结论" min-width="150" />
          <el-table-column prop="rectify_item" label="整改项" min-width="120" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.acted_at) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-drawer v-model="releaseDrawer" title="放行申请详情" size="620px">
      <div v-loading="releaseDetailLoading">
        <template v-if="releaseDetail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="申请编号">{{ releaseDetail.event_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ label(eventStatus, releaseDetail.status) }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ releaseDetail.user_id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="级别">{{ label(dataLevel, releaseDetail.data_level) }}</el-descriptions-item>
            <el-descriptions-item label="文件" :span="2">{{ releaseDetail.file_name ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请用途" :span="2">{{ releaseDetail.matched_snippet ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请时间" :span="2">{{ fmtTime(releaseDetail.occurred_at ?? releaseDetail.created_at) }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">审批进度</el-divider>
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in releaseTimeline"
              :key="index"
              :timestamp="fmtTime(item.occurred_at ?? item.created_at)"
              placement="top"
            >
              <div>{{ item.action ?? '-' }} / {{ item.result ?? item.status ?? '-' }}</div>
              <div class="muted">操作人：{{ item.operator_id ?? '-' }}；{{ item.detail ?? item.comment ?? '' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="releaseTimeline.length === 0" description="暂无审批进度" :image-size="60" />
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="handleVisible" title="处置敏感事件" width="480px">
      <el-form label-width="80px">
        <el-form-item label="动作">
          <el-select v-model="handleForm.action" style="width: 100%">
            <el-option label="分级" value="TRIAGE" />
            <el-option label="调查" value="INVESTIGATE" />
            <el-option label="确认违规" value="CONFIRM" />
            <el-option label="误报" value="FALSE_POSITIVE" />
            <el-option label="升级" value="ESCALATE" />
            <el-option label="关闭" value="CLOSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="结论"><el-input v-model="handleForm.conclusion" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="整改项"><el-input v-model="handleForm.rectify_item" /></el-form-item>
        <el-form-item label="SLA 截止">
          <el-date-picker v-model="handleForm.sla_deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleSaving" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="approveVisible" :title="approveForm.action === 'APPROVE' ? '通过放行申请' : '驳回放行申请'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="意见"><el-input v-model="approveForm.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approveSaving" @click="submitApprove">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.total {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: right;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
