<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { toIsoOrUndefined } from '@fenglin/shared'
import {
  auditApi,
  type AlertRuleItem,
  type AlertRuleView,
  type AuditExportTaskView,
  type AuditLogView,
  type ChainVerifyResult,
} from '@/api'
import { describeError } from '@/utils/error'

const activeTab = ref<'logs' | 'chain' | 'export' | 'alerts' | 'mine'>('logs')

const rows = ref<AuditLogView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({
  event_type: '',
  result: '',
  target_type: '',
  target_id: '',
  request_id: '',
  from: '',
  to: '',
  page: 1,
  size: 10,
})

const detailVisible = ref(false)
const detail = ref<AuditLogView | null>(null)

const chainForm = reactive({ tenant_id: undefined as number | undefined, from: '', to: '' })
const chainLoading = ref(false)
const chainError = ref('')
const chainResult = ref<ChainVerifyResult | null>(null)

/** 三态结果转展示文案：UNVERIFIABLE 明确说明是基线前的不可验证区间，并非被篡改。 */
const chainView = computed(() => {
  const r = chainResult.value
  if (!r) return null
  const base = `租户 ${r.tenant_id} · 校验 ${r.checked} 条 / 审计日志 ${r.audit_log_count} 条`
  if (r.status === 'UNVERIFIABLE') {
    return {
      icon: 'warning' as const,
      title: '存在不可验证区间（非篡改）',
      subTitle: `${base} · 基线之前存在不可验证区间，非篡改：基线前 ${r.unchained_count} 条审计日志未入链（历史基线重建所致），基线 log_id=${r.baseline_log_id} 起链路可验证`,
    }
  }
  if (r.status === 'FAIL') {
    const brokenSeq = r.broken_seq ? ` · 断点序号 ${r.broken_seq}` : ''
    const gap = r.gap_from_log_id ? ` · 缺口 ${r.gap_from_log_id} → ${r.gap_to_log_id}` : ''
    return { icon: 'error' as const, title: '哈希链校验失败', subTitle: `${base}${brokenSeq}${gap}` }
  }
  return { icon: 'success' as const, title: '哈希链完整', subTitle: base }
})

const exportForm = reactive({ from: '', to: '', event_type: '', format: 'CSV' })
const exportLoading = ref(false)
const exportError = ref('')
const exportTask = ref<AuditExportTaskView | null>(null)
const taskId = ref<string | undefined>(undefined)
const taskQueryLoading = ref(false)

const alertRules = ref<AlertRuleView[]>([])
const alertLoading = ref(false)
const alertError = ref('')
const alertSaving = ref(false)

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    const result = await auditApi.list({
      ...query,
      from: toIsoOrUndefined(query.from),
      to: toIsoOrUndefined(query.to),
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: AuditLogView) {
  detailVisible.value = true
  detail.value = row
  try {
    detail.value = await auditApi.get(row.log_id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  }
}

async function verifyChain() {
  chainLoading.value = true
  chainError.value = ''
  chainResult.value = null
  try {
    chainResult.value = await auditApi.verifyChain({
      tenant_id: chainForm.tenant_id || undefined,
      from: toIsoOrUndefined(chainForm.from),
      to: toIsoOrUndefined(chainForm.to),
    })
  } catch (err) {
    chainError.value = describeError(err)
  } finally {
    chainLoading.value = false
  }
}

async function createExport() {
  exportLoading.value = true
  exportError.value = ''
  exportTask.value = null
  try {
    exportTask.value = await auditApi.export({
      from: toIsoOrUndefined(exportForm.from),
      to: toIsoOrUndefined(exportForm.to),
      event_type: exportForm.event_type || 'ALL',
      format: exportForm.format,
    })
    ElMessage.success('导出任务已创建')
  } catch (err) {
    exportError.value = describeError(err)
  } finally {
    exportLoading.value = false
  }
}

async function queryTask() {
  if (!taskId.value) return
  taskQueryLoading.value = true
  try {
    exportTask.value = await auditApi.exportTask(taskId.value)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    taskQueryLoading.value = false
  }
}

async function downloadTask() {
  const id = exportTask.value?.task_id ?? taskId.value
  if (!id) return
  try {
    await auditApi.downloadExport(id)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function loadAlertRules() {
  alertLoading.value = true
  alertError.value = ''
  try {
    alertRules.value = await auditApi.alertRules()
  } catch (err) {
    alertError.value = describeError(err)
  } finally {
    alertLoading.value = false
  }
}

function addAlertRule() {
  alertRules.value.push({
    id: '',
    code: '',
    event_type: '',
    threshold: 10,
    receiver_role: 'TENANT_ADMIN',
    channels: ['INAPP'],
    enabled: true,
  })
}

async function saveAlertRules() {
  alertSaving.value = true
  alertError.value = ''
  try {
    const items: AlertRuleItem[] = alertRules.value.map((rule) => ({
      code: rule.code,
      event_type: rule.event_type,
      threshold: rule.threshold,
      receiver_role: rule.receiver_role,
      channels: rule.channels,
    }))
    alertRules.value = await auditApi.updateAlertRules(items)
    ElMessage.success('告警规则已全量保存')
  } catch (err) {
    alertError.value = describeError(err)
  } finally {
    alertSaving.value = false
  }
}

async function loadMine() {
  loading.value = true
  error.value = ''
  try {
    const result = await auditApi.myRecords({ page: query.page, size: query.size })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'alerts' && alertRules.value.length === 0) void loadAlertRules()
  if (name === 'mine') void loadMine()
}

onMounted(loadLogs)
</script>

<template>
  <div>
    <PageHeader module="审计中台 · A-20/A-21" title="审计中心" description="审计检索、哈希链校验、导出、告警规则与我的操作记录。">
      <template #actions>
        <el-button @click="loadLogs">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '审计哈希链按“租户 + 月分区”计算；计算失败会阻断审计落库并返回 5xx（链完整性优先）。',
        '哈希链校验为三态：UNVERIFIABLE 表示仅基线之前存在不可验证区间（历史基线重建所致），非篡改；基线之后仍逐条复算。',
        '导出仅支持 CSV，event_type 缺省或 ALL 表示全量；导出任务下载为鉴权下载，前端以 Blob 方式拉取。',
        '告警规则为全量替换（items 即最终规则集），请求体不含 enabled 字段，后端返回含 enabled。',
        'A-21 告警通知渠道与接收角色依赖消息中台配置，审计服务仅保存规则。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="审计检索" name="logs">
          <div class="fl-toolbar">
            <el-input v-model="query.event_type" placeholder="事件类型" clearable style="width: 170px" />
            <el-input v-model="query.target_type" placeholder="对象类型" clearable style="width: 150px" />
            <el-input v-model="query.target_id" placeholder="对象 ID" clearable style="width: 150px" />
            <el-input v-model="query.request_id" placeholder="request_id" clearable style="width: 180px" />
            <el-select v-model="query.result" placeholder="结果" clearable style="width: 130px">
              <el-option label="SUCCESS" value="SUCCESS" />
              <el-option label="FAIL" value="FAIL" />
            </el-select>
            <el-date-picker v-model="query.from" type="datetime" placeholder="开始时间" style="width: 180px" value-format="YYYY-MM-DDTHH:mm:ss" />
            <el-date-picker v-model="query.to" type="datetime" placeholder="结束时间" style="width: 180px" value-format="YYYY-MM-DDTHH:mm:ss" />
            <el-button type="primary" plain @click="query.page = 1; loadLogs()">查询</el-button>
            <el-button @click="Object.assign(query, { event_type: '', result: '', target_type: '', target_id: '', request_id: '', from: '', to: '', page: 1 }); loadLogs()">重置</el-button>
          </div>
          <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
          <el-table :data="rows" v-loading="loading" border stripe @row-click="openDetail">
            <el-table-column prop="log_id" label="ID" width="80" />
            <el-table-column prop="log_no" label="日志号" width="180" />
            <el-table-column prop="event_type" label="事件类型" width="170" />
            <el-table-column prop="module" label="模块" width="110" />
            <el-table-column prop="action" label="动作" min-width="140" />
            <el-table-column prop="operator_name" label="操作人" width="120" />
            <el-table-column prop="result" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="risk_level" label="风险" width="90" />
            <el-table-column label="发生时间" width="180">
              <template #default="{ row }">{{ row.occurred_at ? new Date(row.occurred_at).toLocaleString() : '-' }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            style="margin-top: 16px; justify-content: flex-end"
            @change="loadLogs"
          />
        </el-tab-pane>

        <el-tab-pane label="哈希链校验" name="chain">
          <el-form inline>
            <el-form-item label="租户 ID"><el-input-number v-model="chainForm.tenant_id" :min="1" placeholder="默认当前租户" /></el-form-item>
            <el-form-item label="开始"><el-date-picker v-model="chainForm.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
            <el-form-item label="结束"><el-date-picker v-model="chainForm.to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
            <el-form-item><el-button type="primary" :loading="chainLoading" @click="verifyChain">开始校验</el-button></el-form-item>
          </el-form>
          <el-alert v-if="chainError" type="error" :closable="false" show-icon :title="chainError" style="margin-bottom: 12px" />
          <el-result
            v-if="chainView"
            :icon="chainView.icon"
            :title="chainView.title"
            :sub-title="chainView.subTitle"
          />
          <el-empty v-else description="选择范围后开始校验" />
        </el-tab-pane>

        <el-tab-pane label="导出任务" name="export">
          <el-form inline>
            <el-form-item label="开始"><el-date-picker v-model="exportForm.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
            <el-form-item label="结束"><el-date-picker v-model="exportForm.to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
            <el-form-item label="事件类型"><el-input v-model="exportForm.event_type" placeholder="ALL 或具体类型" style="width: 180px" /></el-form-item>
            <el-form-item label="格式">
              <el-select v-model="exportForm.format" style="width: 110px"><el-option label="CSV" value="CSV" /></el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" :loading="exportLoading" @click="createExport">创建导出</el-button></el-form-item>
          </el-form>
          <el-alert v-if="exportError" type="error" :closable="false" show-icon :title="exportError" style="margin-bottom: 12px" />
          <div class="fl-toolbar">
            <el-input-number v-model="taskId" :min="1" placeholder="任务 ID" />
            <el-button :loading="taskQueryLoading" @click="queryTask">查询任务</el-button>
            <el-button :disabled="!exportTask && !taskId" @click="downloadTask">下载</el-button>
          </div>
          <el-descriptions v-if="exportTask" :column="2" border>
            <el-descriptions-item label="任务 ID">{{ exportTask.task_id }}</el-descriptions-item>
            <el-descriptions-item label="任务号">{{ exportTask.task_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ exportTask.status }}</el-descriptions-item>
            <el-descriptions-item label="行数">{{ exportTask.row_count ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="格式">{{ exportTask.format }}</el-descriptions-item>
            <el-descriptions-item label="错误">{{ exportTask.error_msg || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="告警规则" name="alerts">
          <div class="fl-toolbar">
            <el-button @click="addAlertRule">新增规则</el-button>
            <el-button type="primary" :loading="alertSaving" @click="saveAlertRules">全量保存</el-button>
            <el-button :loading="alertLoading" @click="loadAlertRules">刷新</el-button>
          </div>
          <el-alert v-if="alertError" type="error" :closable="false" show-icon :title="alertError" style="margin-bottom: 12px" />
          <el-table :data="alertRules" v-loading="alertLoading" border>
            <el-table-column label="规则编码" min-width="170">
              <template #default="{ row }"><el-input v-model="row.code" /></template>
            </el-table-column>
            <el-table-column label="事件类型" min-width="170">
              <template #default="{ row }"><el-input v-model="row.event_type" /></template>
            </el-table-column>
            <el-table-column label="阈值" width="130">
              <template #default="{ row }"><el-input-number v-model="row.threshold" :min="1" /></template>
            </el-table-column>
            <el-table-column label="接收角色" min-width="160">
              <template #default="{ row }"><el-input v-model="row.receiver_role" /></template>
            </el-table-column>
            <el-table-column label="渠道" min-width="200">
              <template #default="{ row }">
                <el-checkbox-group v-model="row.channels">
                  <el-checkbox value="INAPP">站内</el-checkbox>
                  <el-checkbox value="SMS">短信</el-checkbox>
                  <el-checkbox value="EMAIL">邮件</el-checkbox>
                </el-checkbox-group>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="alertRules.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的操作记录" name="mine">
          <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
          <el-table :data="rows" v-loading="loading" border stripe>
            <el-table-column prop="log_id" label="ID" width="80" />
            <el-table-column prop="event_type" label="事件类型" width="180" />
            <el-table-column prop="module" label="模块" width="110" />
            <el-table-column prop="action" label="动作" min-width="160" />
            <el-table-column prop="result" label="结果" width="100" />
            <el-table-column label="发生时间" width="180">
              <template #default="{ row }">{{ row.occurred_at ? new Date(row.occurred_at).toLocaleString() : '-' }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            layout="total, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @change="loadMine"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer v-model="detailVisible" title="审计详情" size="560px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="日志号">{{ detail.log_no }}</el-descriptions-item>
          <el-descriptions-item label="事件类型">{{ detail.event_type }}</el-descriptions-item>
          <el-descriptions-item label="模块 / 动作">{{ detail.module }} / {{ detail.action }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operator_name || detail.operator_id }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ detail.target_type }} / {{ detail.target_id }}</el-descriptions-item>
          <el-descriptions-item label="request_id">{{ detail.request_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="trace_id">{{ detail.trace_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.result }} / 风险 {{ detail.risk_level || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ detail.occurred_at ? new Date(detail.occurred_at).toLocaleString() : '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>detail</h4>
        <pre class="fl-pre">{{ JSON.stringify(detail.detail ?? {}, null, 2) }}</pre>
      </template>
    </el-drawer>
  </div>
</template>
