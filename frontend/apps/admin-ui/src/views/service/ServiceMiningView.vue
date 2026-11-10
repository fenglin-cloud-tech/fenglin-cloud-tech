<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const models = ref<ServiceRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')

const detail = ref<ServiceRecord | null>(null)
const bottlenecks = ref<ServiceRecord[]>([])
const resourceLoad = ref<ServiceRecord[]>([])
const candidates = ref<ServiceRecord[]>([])
const conformance = ref<ServiceRecord | null>(null)

const discoverVisible = ref(false)
const saving = ref(false)
const discoverForm = reactive({ name: '', result_type: 'PROCESS', source_system: 'SERVICE_DESK', period_start: '', period_end: '' })

const eventVisible = ref(false)
const eventText = ref('[]')

const gapItems = [
  '流程挖掘依赖 svc_mining_event_log 已有事件；没有事件时发现模型返回空结果，本页不造数据。',
  '事件写入接口 /mining/events 接受事件数组，字段需与埋点约定一致（case_id/activity/timestamp/resource）。',
  '候选自动化转 RPA 会创建自动化草稿，需在 RPA 页继续发布。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await serviceApi.mining.models({ page: page.value, size: size.value })
    models.value = result.records
    total.value = result.total
    if (!detail.value && models.value.length) await openModel(models.value[0])
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openModel(row: ServiceRecord) {
  try {
    detail.value = await serviceApi.mining.model(Number(row.id))
    const [bottleneckResult, loadResult, candidateResult] = await Promise.all([
      serviceApi.mining.bottlenecks(Number(row.id)).catch(() => [] as ServiceRecord[]),
      serviceApi.mining.resourceLoad(Number(row.id)).catch(() => [] as ServiceRecord[]),
      serviceApi.mining.candidates(Number(row.id)).catch(() => [] as ServiceRecord[]),
    ])
    bottlenecks.value = Array.isArray(bottleneckResult) ? bottleneckResult : [bottleneckResult]
    resourceLoad.value = Array.isArray(loadResult) ? loadResult : [loadResult]
    candidates.value = candidateResult
    conformance.value = null
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function discover() {
  saving.value = true
  error.value = ''
  try {
    const created = await serviceApi.mining.discover({
      name: discoverForm.name.trim() || undefined,
      result_type: discoverForm.result_type,
      source_system: discoverForm.source_system,
      period_start: discoverForm.period_start || undefined,
      period_end: discoverForm.period_end || undefined,
    })
    ElMessage.success(`模型已生成（ID ${created.id}）`)
    discoverVisible.value = false
    await load()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function runConformance() {
  if (!detail.value) return
  try {
    conformance.value = await serviceApi.mining.conformance(Number(detail.value.id))
    ElMessage.success('一致性分析完成')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function archive() {
  if (!detail.value) return
  try {
    await serviceApi.mining.archive(Number(detail.value.id))
    ElMessage.success('模型已归档')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function toAutomation(row: ServiceRecord) {
  try {
    const created = await serviceApi.mining.toAutomation(Number(row.id), {})
    ElMessage.success(`已生成 RPA 自动化草稿（ID ${created.id ?? '-'}）`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function ingestEvents() {
  let events: ServiceRecord[]
  try {
    events = JSON.parse(eventText.value || '[]') as ServiceRecord[]
  } catch {
    ElMessage.error('事件 JSON 不合法')
    return
  }
  try {
    const result = await serviceApi.mining.importEvents(events)
    ElMessage.success(`事件已写入：${JSON.stringify(result)}`)
    eventVisible.value = false
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="流程挖掘"
      description="事件写入、流程发现、一致性、瓶颈与自动化候选；数据来自 /api/service/v1/mining。"
    >
      <template #actions>
        <el-button v-perm="'service:mining:log:ingest'" plain @click="eventVisible = true">写入事件</el-button>
        <el-button v-perm="'service:mining:model:create'" type="primary" @click="discoverVisible = true">发现流程</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :span="9">
        <el-card shadow="never">
          <template #header><span>挖掘模型</span></template>
          <el-table :data="models" v-loading="loading" border stripe size="small" max-height="420" @row-click="openModel">
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="result_type" label="类型" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="computed_at" label="计算时间" width="160" />
          </el-table>
          <el-pagination
            style="margin-top: 8px; justify-content: flex-end"
            layout="prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="changePage"
          />
        </el-card>
      </el-col>
      <el-col :span="15">
        <el-card shadow="never">
          <template #header>
            <div class="fl-toolbar">
              <span>模型详情 {{ detail?.name ? `· ${detail.name}` : '' }}</span>
              <el-space>
                <el-button v-perm="'service:mining:conformance:view'" size="small" @click="runConformance">一致性分析</el-button>
                <el-button v-perm="'service:mining:model:create'" size="small" type="warning" plain @click="archive">归档</el-button>
              </el-space>
            </div>
          </template>
          <el-empty v-if="!detail" description="选择左侧模型查看详情" :image-size="70" />
          <template v-else>
            <el-descriptions :column="3" border size="small" style="margin-bottom: 12px">
              <el-descriptions-item label="类型">{{ detail.result_type }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ detail.source_system }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left">瓶颈</el-divider>
            <el-table :data="bottlenecks" size="small" border max-height="180">
              <el-table-column prop="activity" label="活动" min-width="150" />
              <el-table-column prop="avg_duration_hours" label="平均耗时(h)" width="130" />
              <el-table-column prop="case_count" label="案例数" width="100" />
            </el-table>

            <el-divider content-position="left">资源负载</el-divider>
            <el-table :data="resourceLoad" size="small" border max-height="180">
              <el-table-column prop="resource" label="资源" min-width="150" />
              <el-table-column prop="case_count" label="处理量" width="110" />
              <el-table-column prop="avg_duration_hours" label="平均耗时(h)" width="130" />
            </el-table>

            <el-divider content-position="left">自动化候选</el-divider>
            <el-table :data="candidates" size="small" border max-height="200">
              <el-table-column prop="name" label="候选" min-width="160" />
              <el-table-column prop="score" label="评分" width="90" />
              <el-table-column prop="frequency" label="频次" width="90" />
              <el-table-column label="操作" width="130">
                <template #default="{ row }">
                  <el-button v-perm="'service:mining:to:automation'" link type="primary" @click="toAutomation(row)">转自动化</el-button>
                </template>
              </el-table-column>
            </el-table>

            <template v-if="conformance">
              <el-divider content-position="left">一致性</el-divider>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="拟合度">{{ conformance.fitness ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="案例数">{{ conformance.case_count ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="偏差数">{{ conformance.deviation_count ?? '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="discoverVisible" title="发现流程模型" width="500px">
      <el-form label-width="100px">
        <el-form-item label="模型名称"><el-input v-model="discoverForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="结果类型">
          <el-select v-model="discoverForm.result_type" style="width: 100%">
            <el-option label="流程模型" value="PROCESS" />
            <el-option label="瓶颈" value="BOTTLENECK" />
            <el-option label="资源负载" value="RESOURCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源系统"><el-input v-model="discoverForm.source_system" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="discoverForm.period_start" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="discoverForm.period_end" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="discoverVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="discover">开始发现</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="eventVisible" title="写入挖掘事件" width="600px">
      <el-input v-model="eventText" type="textarea" :rows="14" spellcheck="false" />
      <template #footer>
        <el-button @click="eventVisible = false">取消</el-button>
        <el-button type="primary" @click="ingestEvents">写入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
