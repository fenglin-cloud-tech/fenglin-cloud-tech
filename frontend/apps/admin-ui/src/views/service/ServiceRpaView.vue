<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const tab = ref('robots')
const error = ref('')

const tabs = [
  { key: 'robots', label: '机器人', perm: 'service:rpa:robot:manage' },
  { key: 'credentials', label: '凭据', perm: 'tenant:service:rpa:credential:config' },
  { key: 'automations', label: '自动化', perm: 'service:automation:flow:design' },
  { key: 'schedules', label: '调度', perm: 'service:automation:flow:design' },
  { key: 'tasks', label: '任务', perm: 'service:rpa:task:manage' },
]
const visibleTabs = computed(() => tabs.filter((item) => auth.can(item.perm)))

const gapItems = [
  '凭据接口只返回掩码（secret_masked），明文仅在创建时提交，后端不提供列表回显。',
  '任务回调 /internal/rpa/tasks/{id}/callback 为内部接口，管理端不暴露。',
  '机器人状态由后端原样保存，本页给出的 ONLINE/OFFLINE/MAINTENANCE/DISABLED 为运维约定值。',
]

// ---------- 机器人 ----------
const robots = ref<ServiceRecord[]>([])
const robotTotal = ref(0)
const robotPage = ref(1)
const robotSize = ref(20)
const robotLoading = ref(false)
const robotVisible = ref(false)
const robotSaving = ref(false)
const robotForm = reactive({
  robot_code: '',
  name: '',
  robot_type: 'UI',
  host: '',
  max_concurrency: 1,
  agent_version: '',
  capabilities: '{}',
})

async function loadRobots() {
  robotLoading.value = true
  error.value = ''
  try {
    const result = await serviceApi.rpa.robots({ page: robotPage.value, size: robotSize.value })
    robots.value = result.records
    robotTotal.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    robotLoading.value = false
  }
}

function parseJson(text: string, label: string): ServiceRecord | null {
  try {
    return JSON.parse(text || '{}') as ServiceRecord
  } catch {
    ElMessage.error(`${label} JSON 不合法`)
    return null
  }
}

async function registerRobot() {
  const capabilities = parseJson(robotForm.capabilities, '能力')
  if (capabilities === null) return
  if (!robotForm.robot_code.trim() || !robotForm.name.trim()) {
    ElMessage.warning('机器人编码与名称必填')
    return
  }
  robotSaving.value = true
  try {
    await serviceApi.rpa.registerRobot({
      robot_code: robotForm.robot_code.trim(),
      name: robotForm.name.trim(),
      robot_type: robotForm.robot_type,
      host: robotForm.host || undefined,
      max_concurrency: Number(robotForm.max_concurrency) || 1,
      agent_version: robotForm.agent_version || undefined,
      capabilities,
    })
    ElMessage.success('机器人已登记')
    robotVisible.value = false
    await loadRobots()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    robotSaving.value = false
  }
}

async function governRobot(row: ServiceRecord, status: string) {
  try {
    await serviceApi.rpa.governRobot({ robot_id: row.id, status })
    ElMessage.success(`机器人状态已更新为 ${status}`)
    await loadRobots()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

// ---------- 凭据 ----------
const credentials = ref<ServiceRecord[]>([])
const credentialLoading = ref(false)
const credentialVisible = ref(false)
const credentialSaving = ref(false)
const credentialForm = reactive({
  name: '',
  secret: '',
  cred_type: 'PASSWORD',
  target_system: '',
  kms_key_id: '',
  kms_version: '',
  rotate_due_at: '',
})

async function loadCredentials() {
  credentialLoading.value = true
  try {
    credentials.value = await serviceApi.rpa.credentials()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    credentialLoading.value = false
  }
}

async function saveCredential() {
  if (!credentialForm.name.trim() || !credentialForm.secret) {
    ElMessage.warning('名称与密钥/口令必填')
    return
  }
  credentialSaving.value = true
  try {
    await serviceApi.rpa.saveCredential({
      name: credentialForm.name.trim(),
      secret: credentialForm.secret,
      cred_type: credentialForm.cred_type,
      target_system: credentialForm.target_system || undefined,
      kms_key_id: credentialForm.kms_key_id || undefined,
      kms_version: credentialForm.kms_version || undefined,
      rotate_due_at: credentialForm.rotate_due_at || undefined,
    })
    ElMessage.success('凭据已保存')
    credentialVisible.value = false
    await loadCredentials()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    credentialSaving.value = false
  }
}

async function issueToken(row: ServiceRecord) {
  try {
    const result = await serviceApi.rpa.credentialToken(row.id)
    ElMessage.success(`短期令牌已签发，过期时间：${result.expires_at ?? '-'}`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

// ---------- 自动化 ----------
const automations = ref<ServiceRecord[]>([])
const automationTotal = ref(0)
const automationPage = ref(1)
const automationSize = ref(20)
const automationLoading = ref(false)
const automationVisible = ref(false)
const automationSaving = ref(false)
const automationForm = reactive({
  flow_code: '',
  name: '',
  timeout_sec: 300,
  target_whitelist: '',
  nodes: '[]',
})

async function loadAutomations() {
  automationLoading.value = true
  try {
    const result = await serviceApi.rpa.automations({ page: automationPage.value, size: automationSize.value })
    automations.value = result.records
    automationTotal.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    automationLoading.value = false
  }
}

async function saveAutomation() {
  const nodes = parseJson(automationForm.nodes, '节点')
  if (nodes === null) return
  if (!Array.isArray(nodes)) {
    ElMessage.error('节点必须是数组')
    return
  }
  automationSaving.value = true
  try {
    await serviceApi.rpa.saveAutomation({
      flow_code: automationForm.flow_code.trim(),
      name: automationForm.name.trim(),
      timeout_sec: Number(automationForm.timeout_sec) || undefined,
      target_whitelist: automationForm.target_whitelist
        ? automationForm.target_whitelist.split(',').map((each) => each.trim()).filter(Boolean)
        : undefined,
      nodes,
    })
    ElMessage.success('自动化草稿已保存')
    automationVisible.value = false
    await loadAutomations()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    automationSaving.value = false
  }
}

async function publishAutomation(row: ServiceRecord) {
  try {
    await serviceApi.rpa.publish(row.id)
    ElMessage.success('自动化已发布')
    await loadAutomations()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

// ---------- 调度 ----------
const schedules = ref<ServiceRecord[]>([])
const scheduleLoading = ref(false)
const scheduleVisible = ref(false)
const scheduleSaving = ref(false)
const scheduleForm = reactive({
  name: '',
  automation_flow_id: undefined as number | undefined,
  trigger_type: 'CRON',
  cron_expr: '',
  event_topic: '',
  concurrency_limit: 1,
  timeout_sec: 300,
})

async function loadSchedules() {
  scheduleLoading.value = true
  try {
    schedules.value = await serviceApi.rpa.schedules()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    scheduleLoading.value = false
  }
}

async function createSchedule() {
  if (!scheduleForm.name.trim() || !scheduleForm.automation_flow_id) {
    ElMessage.warning('名称与自动化流程必填')
    return
  }
  scheduleSaving.value = true
  try {
    await serviceApi.rpa.createSchedule({
      name: scheduleForm.name.trim(),
      automation_flow_id: scheduleForm.automation_flow_id,
      trigger_type: scheduleForm.trigger_type,
      cron_expr: scheduleForm.cron_expr || undefined,
      event_topic: scheduleForm.event_topic || undefined,
      concurrency_limit: Number(scheduleForm.concurrency_limit) || undefined,
      timeout_sec: Number(scheduleForm.timeout_sec) || undefined,
    })
    ElMessage.success('调度已创建')
    scheduleVisible.value = false
    await loadSchedules()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    scheduleSaving.value = false
  }
}

// ---------- 任务 ----------
const tasks = ref<ServiceRecord[]>([])
const taskTotal = ref(0)
const taskPage = ref(1)
const taskSize = ref(20)
const taskLoading = ref(false)
const taskStatus = ref('')
const taskDetail = ref<ServiceRecord | null>(null)
const taskLogs = ref<ServiceRecord[]>([])
const taskDrawer = ref(false)

async function loadTasks() {
  taskLoading.value = true
  try {
    const result = await serviceApi.rpa.tasks({
      status: taskStatus.value || undefined,
      page: taskPage.value,
      size: taskSize.value,
    })
    tasks.value = result.records
    taskTotal.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    taskLoading.value = false
  }
}

async function openTask(row: ServiceRecord) {
  try {
    taskDetail.value = await serviceApi.rpa.task(row.id)
    taskLogs.value = await serviceApi.rpa.taskLogs(row.id)
    taskDrawer.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function retryTask(row: ServiceRecord) {
  try {
    await serviceApi.rpa.retry(row.id)
    ElMessage.success('已重新入队')
    await loadTasks()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function taskAction(row: ServiceRecord, action: string) {
  try {
    await serviceApi.rpa.taskAction(row.id, { action })
    ElMessage.success(`任务已 ${action}`)
    await loadTasks()
    if (taskDetail.value && taskDetail.value.id === row.id) await openTask(row)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function switchTab(key: string) {
  tab.value = key
  if (key === 'robots') await loadRobots()
  if (key === 'credentials') await loadCredentials()
  if (key === 'automations') await loadAutomations()
  if (key === 'schedules') await loadSchedules()
  if (key === 'tasks') await loadTasks()
}

onMounted(async () => {
  if (visibleTabs.value.length) await switchTab(visibleTabs.value[0].key)
})
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="RPA 自动化"
      description="机器人、凭据、自动化流程、调度与任务；数据来自 /api/service/v1/rpa。"
    >
      <template #actions>
        <el-button v-perm="'service:rpa:robot:manage'" plain @click="robotVisible = true">登记机器人</el-button>
        <el-button v-perm="'tenant:service:rpa:credential:config'" plain @click="credentialVisible = true">新增凭据</el-button>
        <el-button v-perm="'service:automation:flow:design'" type="primary" @click="automationVisible = true">新建自动化</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />
    <el-alert
      v-if="visibleTabs.length === 0"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号没有任何 RPA 权限点，页面不展示数据。"
    />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane v-for="item in visibleTabs" :key="item.key" :label="item.label" :name="item.key" />
    </el-tabs>

    <el-card v-if="tab === 'robots'" shadow="never">
      <el-table :data="robots" v-loading="robotLoading" border stripe size="small">
        <el-table-column prop="robot_code" label="编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="robot_type" label="类型" width="100" />
        <el-table-column prop="host" label="主机" min-width="140" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="max_concurrency" label="并发" width="80" />
        <el-table-column prop="last_heartbeat_at" label="最后心跳" width="170" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-dropdown trigger="click">
              <el-button v-perm="'service:rpa:robot:manage'" link type="primary">变更状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="status in ['ONLINE', 'OFFLINE', 'MAINTENANCE', 'DISABLED']" :key="status" @click="governRobot(row, status)">
                    {{ status }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="robotTotal"
        :page-size="robotSize"
        :current-page="robotPage"
        @current-change="(next: number) => { robotPage = next; loadRobots() }"
      />
    </el-card>

    <el-card v-if="tab === 'credentials'" shadow="never">
      <el-table :data="credentials" v-loading="credentialLoading" border stripe size="small">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="cred_type" label="类型" width="110" />
        <el-table-column prop="target_system" label="目标系统" min-width="140" />
        <el-table-column prop="secret_masked" label="密文" width="120" />
        <el-table-column prop="rotate_due_at" label="轮换到期" width="160" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-perm="'tenant:service:rpa:credential:config'" link type="primary" @click="issueToken(row)">签发令牌</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="tab === 'automations'" shadow="never">
      <el-table :data="automations" v-loading="automationLoading" border stripe size="small">
        <el-table-column prop="flow_code" label="流程编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="timeout_sec" label="超时(s)" width="90" />
        <el-table-column prop="target_whitelist" label="目标白名单" min-width="160" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-perm="'service:automation:flow:design'" link type="primary" @click="publishAutomation(row)">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="automationTotal"
        :page-size="automationSize"
        :current-page="automationPage"
        @current-change="(next: number) => { automationPage = next; loadAutomations() }"
      />
    </el-card>

    <el-card v-if="tab === 'schedules'" shadow="never">
      <div class="fl-toolbar" style="margin-bottom: 8px">
        <span class="fl-muted">调度触发已发布的自动化流程</span>
        <el-button v-perm="'service:automation:flow:design'" size="small" type="primary" @click="scheduleVisible = true">新增调度</el-button>
      </div>
      <el-table :data="schedules" v-loading="scheduleLoading" border stripe size="small">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="trigger_type" label="触发" width="100" />
        <el-table-column prop="cron_expr" label="Cron" width="140" />
        <el-table-column prop="event_topic" label="事件主题" min-width="140" />
        <el-table-column prop="concurrency_limit" label="并发" width="80" />
        <el-table-column prop="enabled" label="启用" width="80" />
        <el-table-column prop="last_fire_at" label="上次触发" width="170" />
      </el-table>
    </el-card>

    <el-card v-if="tab === 'tasks'" shadow="never">
      <div class="fl-toolbar" style="margin-bottom: 8px">
        <el-select v-model="taskStatus" placeholder="全部状态" clearable style="width: 180px" @change="loadTasks">
          <el-option v-for="status in ['QUEUED', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'MANUAL_REQUIRED']" :key="status" :label="status" :value="status" />
        </el-select>
        <el-button size="small" @click="loadTasks">刷新</el-button>
      </div>
      <el-table :data="tasks" v-loading="taskLoading" border stripe size="small">
        <el-table-column prop="task_no" label="任务号" width="160" />
        <el-table-column prop="automation_flow_id" label="流程ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="robot_id" label="机器人ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="130" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column prop="retry_count" label="重试" width="80" />
        <el-table-column label="开始" width="170">
          <template #default="{ row }">{{ formatDateTime(row.started_at) }}</template>
        </el-table-column>
        <el-table-column prop="duration_sec" label="耗时(s)" width="90" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTask(row)">详情</el-button>
            <el-button v-perm="'service:rpa:task:manage'" link type="warning" @click="retryTask(row)">重试</el-button>
            <el-button v-perm="'service:rpa:task:manage'" link type="danger" @click="taskAction(row, 'CANCEL')">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="taskTotal"
        :page-size="taskSize"
        :current-page="taskPage"
        @current-change="(next: number) => { taskPage = next; loadTasks() }"
      />
    </el-card>

    <el-dialog v-model="robotVisible" title="登记机器人" width="520px">
      <el-form label-width="110px">
        <el-form-item label="编码"><el-input v-model="robotForm.robot_code" placeholder="唯一编码" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="robotForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="robotForm.robot_type" style="width: 100%">
            <el-option label="UI 自动化" value="UI" />
            <el-option label="API 自动化" value="API" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机"><el-input v-model="robotForm.host" /></el-form-item>
        <el-form-item label="最大并发"><el-input-number v-model="robotForm.max_concurrency" :min="1" /></el-form-item>
        <el-form-item label="Agent 版本"><el-input v-model="robotForm.agent_version" /></el-form-item>
        <el-form-item label="能力(JSON)"><el-input v-model="robotForm.capabilities" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="robotVisible = false">取消</el-button>
        <el-button type="primary" :loading="robotSaving" @click="registerRobot">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="credentialVisible" title="新增凭据" width="520px">
      <el-form label-width="110px">
        <el-form-item label="名称"><el-input v-model="credentialForm.name" /></el-form-item>
        <el-form-item label="密钥/口令"><el-input v-model="credentialForm.secret" type="password" show-password /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="credentialForm.cred_type" style="width: 100%">
            <el-option label="账号口令" value="PASSWORD" />
            <el-option label="API Key" value="API_KEY" />
            <el-option label="Token" value="TOKEN" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标系统"><el-input v-model="credentialForm.target_system" /></el-form-item>
        <el-form-item label="KMS Key"><el-input v-model="credentialForm.kms_key_id" /></el-form-item>
        <el-form-item label="KMS 版本"><el-input v-model="credentialForm.kms_version" /></el-form-item>
        <el-form-item label="轮换到期">
          <el-date-picker v-model="credentialForm.rotate_due_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="credentialVisible = false">取消</el-button>
        <el-button type="primary" :loading="credentialSaving" @click="saveCredential">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="automationVisible" title="新建自动化" width="560px">
      <el-form label-width="110px">
        <el-form-item label="流程编码"><el-input v-model="automationForm.flow_code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="automationForm.name" /></el-form-item>
        <el-form-item label="超时(s)"><el-input-number v-model="automationForm.timeout_sec" :min="10" /></el-form-item>
        <el-form-item label="目标白名单">
          <el-input v-model="automationForm.target_whitelist" placeholder="逗号分隔，如 https://erp.example.com" />
        </el-form-item>
        <el-form-item label="节点(JSON)"><el-input v-model="automationForm.nodes" type="textarea" :rows="8" spellcheck="false" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="automationVisible = false">取消</el-button>
        <el-button type="primary" :loading="automationSaving" @click="saveAutomation">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scheduleVisible" title="新增调度" width="520px">
      <el-form label-width="110px">
        <el-form-item label="名称"><el-input v-model="scheduleForm.name" /></el-form-item>
        <el-form-item label="自动化流程ID"><el-input-number v-model="scheduleForm.automation_flow_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="scheduleForm.trigger_type" style="width: 100%">
            <el-option label="Cron" value="CRON" />
            <el-option label="事件" value="EVENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron"><el-input v-model="scheduleForm.cron_expr" placeholder="0 0/10 * * * ?" /></el-form-item>
        <el-form-item label="事件主题"><el-input v-model="scheduleForm.event_topic" /></el-form-item>
        <el-form-item label="并发上限"><el-input-number v-model="scheduleForm.concurrency_limit" :min="1" /></el-form-item>
        <el-form-item label="超时(s)"><el-input-number v-model="scheduleForm.timeout_sec" :min="10" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleVisible = false">取消</el-button>
        <el-button type="primary" :loading="scheduleSaving" @click="createSchedule">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="taskDrawer" title="任务详情" size="640px">
      <template v-if="taskDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务号">{{ taskDetail.task_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskDetail.status }}</el-descriptions-item>
          <el-descriptions-item label="流程ID">{{ taskDetail.automation_flow_id }}</el-descriptions-item>
          <el-descriptions-item label="机器人ID">{{ taskDetail.robot_id }}</el-descriptions-item>
          <el-descriptions-item label="错误码">{{ taskDetail.error_code || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ taskDetail.error_msg || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务键">{{ taskDetail.business_key || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时(s)">{{ taskDetail.duration_sec ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">输入 / 输出</el-divider>
        <el-input :model-value="JSON.stringify(taskDetail.input_json ?? {}, null, 2)" type="textarea" :rows="5" readonly />
        <el-input :model-value="JSON.stringify(taskDetail.output_json ?? {}, null, 2)" type="textarea" :rows="5" readonly style="margin-top: 8px" />
        <el-divider content-position="left">日志</el-divider>
        <el-table :data="taskLogs" size="small" border max-height="240">
          <el-table-column prop="attempt" label="尝试" width="70" />
          <el-table-column prop="log_level" label="级别" width="80" />
          <el-table-column prop="step" label="步骤" width="120" />
          <el-table-column prop="message" label="消息" min-width="200" />
          <el-table-column prop="logged_at" label="时间" width="170" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
