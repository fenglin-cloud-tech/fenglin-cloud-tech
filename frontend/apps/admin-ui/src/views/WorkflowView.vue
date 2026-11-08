<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice, StatCard } from '@fenglin/ui-admin'
import { BarChart } from '@fenglin/charts'
import { workflowApi, type DeployDefinitionRequest, type DefinitionView, type MonitorStatsResult } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canManage = computed(() => auth.can('tenant:workflow:def:manage'))
const canMonitor = computed(() => auth.can('tenant:workflow:monitor:view'))

const rows = ref<DefinitionView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ category: '', status: '', page: 1, size: 10 })

const stats = ref<MonitorStatsResult | null>(null)
const statsLoading = ref(false)
const statsError = ref('')

const deployVisible = ref(false)
const deploying = ref(false)
const deployError = ref('')
const deployForm = reactive<DeployDefinitionRequest>({
  process_key: '',
  name: '',
  category: '',
  bpmn_xml: '',
  form_key: '',
})

const nodeChart = computed(() => (stats.value?.by_node ?? []).map((item) => ({ label: item.node_key, value: item.count })))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await workflowApi.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true
  statsError.value = ''
  try {
    stats.value = await workflowApi.stats()
  } catch (err) {
    statsError.value = describeError(err)
  } finally {
    statsLoading.value = false
  }
}

function openDeploy() {
  Object.assign(deployForm, { process_key: '', name: '', category: '', bpmn_xml: '', form_key: '' })
  deployError.value = ''
  deployVisible.value = true
}

async function submitDeploy() {
  if (!deployForm.process_key || !deployForm.name || !deployForm.bpmn_xml) {
    deployError.value = '流程标识、名称与 BPMN XML 为必填项'
    return
  }
  deploying.value = true
  deployError.value = ''
  try {
    await workflowApi.deploy({
      ...deployForm,
      category: deployForm.category || undefined,
      form_key: deployForm.form_key || undefined,
    })
    ElMessage.success('流程定义部署成功')
    deployVisible.value = false
    await load()
  } catch (err) {
    deployError.value = describeError(err)
  } finally {
    deploying.value = false
  }
}

onMounted(() => {
  void load()
  void loadStats()
})
</script>

<template>
  <div>
    <PageHeader module="工作流中台 · A-16/A-17" title="流程中心" description="流程定义部署与监控；发起、办理、转办、驳回由业务端接口承载。">
      <template #actions>
        <el-button v-perm="'tenant:workflow:def:manage'" type="primary" @click="openDeploy">部署流程</el-button>
        <el-button @click="load(); loadStats()">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        'A-16 流程设计器：后端仅接收 BPMN XML 部署，未提供可视化设计器与表单设计器接口，本页以 XML 文本提交。',
        'A-17 流程监控只提供统计聚合接口，未提供实例明细列表接口；实例明细请使用流程实例查询接口。',
        '读接口权限：tenant:workflow:def:manage=' + (canManage ? '具备' : '缺失') + '，tenant:workflow:monitor:view=' + (canMonitor ? '具备' : '缺失') + '。',
      ]"
    />

    <div class="fl-grid fl-grid--4" style="margin-bottom: 16px">
      <StatCard label="运行中" :value="stats?.running ?? '—'" :loading="statsLoading" hint="monitor/stats" />
      <StatCard label="已完成" :value="stats?.completed ?? '—'" :loading="statsLoading" tone="success" hint="monitor/stats" />
      <StatCard label="超时" :value="stats?.timeout ?? '—'" :loading="statsLoading" tone="warning" hint="monitor/stats" />
      <StatCard
        label="平均耗时"
        :value="stats ? `${stats.avg_duration_minutes.toFixed(1)} 分钟` : '—'"
        :loading="statsLoading"
        hint="monitor/stats"
      />
    </div>

    <el-alert v-if="statsError" type="warning" :closable="false" show-icon :title="`监控统计不可用：${statsError}`" style="margin-bottom: 16px" />

    <div class="fl-card" style="margin-bottom: 16px">
      <div class="fl-section-title">
        <span>节点耗时分布</span>
        <span class="fl-muted">按节点实例数</span>
      </div>
      <BarChart :data="nodeChart" :height="200" empty-text="暂无节点统计数据或接口不可用" />
    </div>

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.category" placeholder="流程分类" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="SUSPENDED" value="SUSPENDED" />
        </el-select>
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
      </div>
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="definition_id" label="定义 ID" width="100" />
        <el-table-column prop="process_key" label="流程标识" min-width="170" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="def_version" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="form_key" label="表单键" width="140" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
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

    <el-dialog v-model="deployVisible" title="部署流程定义" width="760px">
      <el-alert v-if="deployError" type="error" :closable="false" show-icon :title="deployError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="流程标识" required><el-input v-model="deployForm.process_key" placeholder="例如 leave_apply" /></el-form-item>
        <el-form-item label="流程名称" required><el-input v-model="deployForm.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="deployForm.category" placeholder="可选" /></el-form-item>
        <el-form-item label="表单键"><el-input v-model="deployForm.form_key" placeholder="可选" /></el-form-item>
        <el-form-item label="BPMN XML" required>
          <el-input v-model="deployForm.bpmn_xml" type="textarea" :rows="14" class="fl-mono" placeholder="<?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?> ..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deployVisible = false">取消</el-button>
        <el-button type="primary" :loading="deploying" @click="submitDeploy">部署</el-button>
      </template>
    </el-dialog>
  </div>
</template>
