<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabObjective, type CollabPeriod } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref('periods')

const periods = ref<CollabPeriod[]>([])
const periodLoading = ref(false)
const periodError = ref('')

const periodVisible = ref(false)
const periodSaving = ref(false)
const periodFormError = ref('')
const periodForm = reactive({
  name: '',
  start_date: '',
  end_date: '',
  score_policy: '',
  status: 'OPEN',
})

const objectives = ref<CollabObjective[]>([])
const objLoading = ref(false)
const objError = ref('')
const objQuery = reactive({ period_id: '', owner_id: '' })

const objVisible = ref(false)
const objSaving = ref(false)
const objFormError = ref('')
const objForm = reactive({
  period_id: '',
  obj_type: 'COMPANY',
  title: '',
  description: '',
  visibility: 'ALL',
})

const krVisible = ref(false)
const krSaving = ref(false)
const krFormError = ref('')
const krObjectiveId = ref('')
const krForm = reactive({
  title: '',
  metric_type: 'PERCENT',
  target_value: undefined as number | undefined,
  current_value: undefined as number | undefined,
  confidence: 'MID',
})

const progressVisible = ref(false)
const progressSaving = ref(false)
const progressFormError = ref('')
const progressKrId = ref('')
const progressKrTitle = ref('')
const progressForm = reactive({ progress: 0 as number | undefined, confidence: 'MID', note: '' })

const alignVisible = ref(false)
const alignSaving = ref(false)
const alignFormError = ref('')
const alignObjectiveId = ref('')
const alignForm = reactive({
  parent_objective_id: '',
  align_type: 'VERTICAL',
  weight: undefined as number | undefined,
})

const graphLoading = ref(false)
const graphError = ref('')
const graphQuery = reactive({ period_id: '' })
const graphNodes = ref<Array<{ id: string; title: string; obj_type: string; owner_id?: number }>>([])
const graphEdges = ref<Array<{ child_id: string; parent_id: string; align_type: string }>>([])

const objTypeOptions = [
  { value: 'COMPANY', label: '公司级' },
  { value: 'DEPT', label: '部门级' },
  { value: 'TEAM', label: '团队级' },
  { value: 'PERSONAL', label: '个人级' },
]
const visibilityOptions = [
  { value: 'PRIVATE', label: '仅自己' },
  { value: 'DEPT', label: '本部门' },
  { value: 'ALL', label: '全员可见' },
]
const metricTypeOptions = [
  { value: 'PERCENT', label: '百分比' },
  { value: 'NUMBER', label: '数值' },
  { value: 'BOOLEAN', label: '布尔' },
]
const confidenceOptions = [
  { value: 'HIGH', label: '高' },
  { value: 'MID', label: '中' },
  { value: 'LOW', label: '低' },
]
const alignTypeOptions = [
  { value: 'VERTICAL', label: '纵向对齐' },
  { value: 'HORIZONTAL', label: '横向对齐' },
]

const parentOptions = computed(() => objectives.value.filter((item) => item.objective_id !== alignObjectiveId.value))

const gapItems = [
  '对齐关系用节点/边表格展示，未引入图形库。',
  '周期关闭后没有重开接口；目标/ KR 没有删除或编辑接口。',
  'KR 进度接口校验 biz:collab:okr:progress（已与权限种子对齐），目标/KR 维护仍需 biz:collab:okr:manage。',
  '目标列表只能按周期/负责人过滤，没有部门树或跨周期汇总。',
]

async function loadPeriods() {
  periodLoading.value = true
  periodError.value = ''
  try {
    periods.value = await collabApi.okr.periods()
  } catch (err) {
    periodError.value = describeError(err)
  } finally {
    periodLoading.value = false
  }
}

async function loadObjectives() {
  objLoading.value = true
  objError.value = ''
  try {
    let ownerId: number | undefined
    if (objQuery.owner_id.trim()) {
      ownerId = Number(objQuery.owner_id.trim())
      if (!Number.isFinite(ownerId)) {
        objError.value = '负责人 ID 必须是数字'
        objLoading.value = false
        return
      }
    }
    objectives.value = await collabApi.okr.objectives({
      periodId: objQuery.period_id || undefined,
      ownerId,
    })
  } catch (err) {
    objError.value = describeError(err)
  } finally {
    objLoading.value = false
  }
}

async function loadGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    const result = await collabApi.okr.alignmentGraph({ periodId: graphQuery.period_id || undefined })
    graphNodes.value = result.nodes
    graphEdges.value = result.edges
  } catch (err) {
    graphError.value = describeError(err)
  } finally {
    graphLoading.value = false
  }
}

function refreshActive() {
  if (activeTab.value === 'periods') {
    void loadPeriods()
  } else if (activeTab.value === 'objectives') {
    void loadObjectives()
  } else {
    void loadGraph()
  }
}

function openPeriod() {
  Object.assign(periodForm, { name: '', start_date: '', end_date: '', score_policy: '', status: 'OPEN' })
  periodFormError.value = ''
  periodVisible.value = true
}

async function submitPeriod() {
  if (!periodForm.name.trim()) {
    periodFormError.value = '周期名称为必填项'
    return
  }
  periodSaving.value = true
  periodFormError.value = ''
  try {
    await collabApi.admin.upsertPeriod({
      name: periodForm.name.trim(),
      start_date: periodForm.start_date || undefined,
      end_date: periodForm.end_date || undefined,
      score_policy: periodForm.score_policy.trim() || undefined,
      status: periodForm.status,
    })
    ElMessage.success('周期已保存')
    periodVisible.value = false
    await loadPeriods()
  } catch (err) {
    periodFormError.value = describeError(err)
  } finally {
    periodSaving.value = false
  }
}

async function closePeriod(row: CollabPeriod) {
  try {
    await ElMessageBox.confirm(`确认关闭周期「${row.name}」？关闭后无法重开。`, '关闭周期', {
      type: 'warning',
      confirmButtonText: '确认关闭',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await collabApi.okr.closePeriod(row.period_id)
    ElMessage.success('周期已关闭')
    await loadPeriods()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openObjective() {
  Object.assign(objForm, {
    period_id: objQuery.period_id || '',
    obj_type: 'COMPANY',
    title: '',
    description: '',
    visibility: 'ALL',
  })
  objFormError.value = ''
  objVisible.value = true
}

async function submitObjective() {
  if (!objForm.period_id) {
    objFormError.value = '请选择所属周期'
    return
  }
  if (!objForm.title.trim()) {
    objFormError.value = '目标标题为必填项'
    return
  }
  objSaving.value = true
  objFormError.value = ''
  try {
    await collabApi.okr.createObjective({
      period_id: objForm.period_id,
      obj_type: objForm.obj_type,
      title: objForm.title.trim(),
      description: objForm.description.trim() || undefined,
      visibility: objForm.visibility,
    })
    ElMessage.success('目标已创建')
    objVisible.value = false
    await loadObjectives()
  } catch (err) {
    objFormError.value = describeError(err)
  } finally {
    objSaving.value = false
  }
}

function openKr(objectiveId: string) {
  krObjectiveId.value = objectiveId
  Object.assign(krForm, {
    title: '',
    metric_type: 'PERCENT',
    target_value: undefined,
    current_value: undefined,
    confidence: 'MID',
  })
  krFormError.value = ''
  krVisible.value = true
}

async function submitKr() {
  if (!krForm.title.trim()) {
    krFormError.value = 'KR 标题为必填项'
    return
  }
  krSaving.value = true
  krFormError.value = ''
  try {
    await collabApi.okr.createKr(krObjectiveId.value, {
      title: krForm.title.trim(),
      metric_type: krForm.metric_type,
      target_value: krForm.target_value,
      current_value: krForm.current_value,
      confidence: krForm.confidence,
    })
    ElMessage.success('KR 已创建')
    krVisible.value = false
    await loadObjectives()
  } catch (err) {
    krFormError.value = describeError(err)
  } finally {
    krSaving.value = false
  }
}

function openProgress(krId: string, krTitle: string) {
  progressKrId.value = krId
  progressKrTitle.value = krTitle
  Object.assign(progressForm, { progress: 0, confidence: 'MID', note: '' })
  progressFormError.value = ''
  progressVisible.value = true
}

async function submitProgress() {
  if (progressForm.progress === undefined || progressForm.progress === null) {
    progressFormError.value = '进度为必填项'
    return
  }
  const progressValue = progressForm.progress
  progressSaving.value = true
  progressFormError.value = ''
  try {
    await collabApi.okr.progress(progressKrId.value, {
      progress: progressValue,
      confidence: progressForm.confidence,
      note: progressForm.note.trim() || undefined,
    })
    ElMessage.success('进度已更新')
    progressVisible.value = false
    await loadObjectives()
  } catch (err) {
    progressFormError.value = describeError(err)
  } finally {
    progressSaving.value = false
  }
}

function openAlign(objectiveId: string) {
  alignObjectiveId.value = objectiveId
  Object.assign(alignForm, { parent_objective_id: '', align_type: 'VERTICAL', weight: undefined })
  alignFormError.value = ''
  alignVisible.value = true
}

async function submitAlign() {
  if (!alignForm.parent_objective_id) {
    alignFormError.value = '请选择上级目标'
    return
  }
  alignSaving.value = true
  alignFormError.value = ''
  try {
    await collabApi.okr.align(alignObjectiveId.value, {
      parent_objective_id: alignForm.parent_objective_id,
      align_type: alignForm.align_type,
      weight: alignForm.weight,
    })
    ElMessage.success('对齐关系已建立')
    alignVisible.value = false
  } catch (err) {
    alignFormError.value = describeError(err)
  } finally {
    alignSaving.value = false
  }
}

onMounted(() => {
  void loadPeriods()
  void loadObjectives()
  void loadGraph()
})
</script>

<template>
  <div>
    <PageHeader module="云协 Collab" title="OKR 管理" description="周期、目标与 KR、对齐关系；进度更新写入进展记录。">
      <template #actions>
        <el-button :loading="periodLoading || objLoading || graphLoading" @click="refreshActive">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="周期" name="periods">
        <div class="fl-toolbar">
          <el-button v-perm="'tenant:collab:okr:period:manage'" type="primary" @click="openPeriod">新建/更新周期</el-button>
        </div>

        <el-alert v-if="periodError" type="error" :closable="false" show-icon :title="periodError" style="margin-bottom: 12px" />

        <el-table :data="periods" v-loading="periodLoading" border stripe>
          <el-table-column prop="period_id" label="周期 ID" width="190" show-overflow-tooltip />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="start_date" label="开始日期" width="130">
            <template #default="{ row }">{{ row.start_date || '-' }}</template>
          </el-table-column>
          <el-table-column prop="end_date" label="结束日期" width="130">
            <template #default="{ row }">{{ row.end_date || '-' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'CLOSED'"
                v-perm="'tenant:collab:okr:period:manage'"
                link
                type="warning"
                @click="closePeriod(row)"
              >
                关闭周期
              </el-button>
              <el-text v-else type="info">已关闭</el-text>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="目标与 KR" name="objectives">
        <div class="fl-toolbar">
          <el-select v-model="objQuery.period_id" placeholder="全部周期" clearable style="width: 200px">
            <el-option v-for="period in periods" :key="period.period_id" :label="period.name" :value="period.period_id" />
          </el-select>
          <el-input v-model="objQuery.owner_id" placeholder="负责人 ID" clearable style="width: 150px" />
          <el-button type="primary" plain @click="loadObjectives">查询</el-button>
          <el-button @click="objQuery.period_id = ''; objQuery.owner_id = ''; loadObjectives()">重置</el-button>
          <el-button v-perm="'biz:collab:okr:manage'" type="primary" @click="openObjective">新建目标</el-button>
        </div>

        <el-alert v-if="objError" type="error" :closable="false" show-icon :title="objError" style="margin-bottom: 12px" />

        <el-table :data="objectives" v-loading="objLoading" row-key="objective_id" border stripe>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="kr-panel">
                <el-table :data="row.key_results" size="small" border>
                  <el-table-column prop="kr_id" label="KR ID" width="190" show-overflow-tooltip />
                  <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="metric_type" label="指标类型" width="100" />
                  <el-table-column prop="target_value" label="目标值" width="90">
                    <template #default="{ row: kr }">{{ kr.target_value ?? '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="current_value" label="当前值" width="90">
                    <template #default="{ row: kr }">{{ kr.current_value ?? '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="progress" label="进度(%)" width="90">
                    <template #default="{ row: kr }">{{ kr.progress ?? '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="confidence" label="信心" width="80" />
                  <el-table-column label="操作" width="100">
                    <template #default="{ row: kr }">
                      <el-button
                        v-perm="'biz:collab:okr:manage'"
                        link
                        type="primary"
                        @click="openProgress(kr.kr_id, kr.title)"
                      >
                        更新进度
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="objective_id" label="目标 ID" width="190" show-overflow-tooltip />
          <el-table-column prop="title" label="目标" min-width="180" show-overflow-tooltip />
          <el-table-column prop="obj_type" label="类型" width="100" />
          <el-table-column prop="owner_id" label="负责人" width="90" />
          <el-table-column prop="visibility" label="可见性" width="100" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="period_id" label="周期 ID" width="190" show-overflow-tooltip />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'biz:collab:okr:manage'" link type="primary" @click="openKr(row.objective_id)">新增 KR</el-button>
              <el-button v-perm="'biz:collab:okr:manage'" link type="warning" @click="openAlign(row.objective_id)">对齐</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="对齐图" name="graph">
        <div class="fl-toolbar">
          <el-select v-model="graphQuery.period_id" placeholder="全部周期" clearable style="width: 200px">
            <el-option v-for="period in periods" :key="period.period_id" :label="period.name" :value="period.period_id" />
          </el-select>
          <el-button type="primary" plain @click="loadGraph">刷新</el-button>
          <el-text type="info">节点 {{ graphNodes.length }} 个 / 边 {{ graphEdges.length }} 条</el-text>
        </div>

        <el-alert v-if="graphError" type="error" :closable="false" show-icon :title="graphError" style="margin-bottom: 12px" />

        <div v-loading="graphLoading">
          <h3 class="block-title">节点</h3>
          <el-table :data="graphNodes" border stripe size="small">
            <el-table-column prop="id" label="目标 ID" width="200" />
            <el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column prop="obj_type" label="类型" width="110" />
            <el-table-column prop="owner_id" label="负责人" width="100">
              <template #default="{ row }">{{ row.owner_id ?? '-' }}</template>
            </el-table-column>
          </el-table>

          <h3 class="block-title">对齐边</h3>
          <el-table :data="graphEdges" border stripe size="small">
            <el-table-column prop="child_id" label="子目标 ID" width="200" />
            <el-table-column prop="parent_id" label="上级目标 ID" width="200" />
            <el-table-column prop="align_type" label="对齐类型" width="120" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="periodVisible" title="新建/更新周期" width="520px">
      <el-alert v-if="periodFormError" type="error" :closable="false" show-icon :title="periodFormError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="周期名称" required>
          <el-input v-model="periodForm.name" maxlength="64" placeholder="如 2026 Q1" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="periodForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="periodForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评分策略">
          <el-input v-model="periodForm.score_policy" maxlength="128" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="periodForm.status" style="width: 100%">
            <el-option label="进行中" value="OPEN" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="periodVisible = false">取消</el-button>
        <el-button
          v-perm="'tenant:collab:okr:period:manage'"
          type="primary"
          :loading="periodSaving"
          @click="submitPeriod"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="objVisible" title="新建目标" width="560px">
      <el-alert v-if="objFormError" type="error" :closable="false" show-icon :title="objFormError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="所属周期" required>
          <el-select v-model="objForm.period_id" placeholder="选择周期" style="width: 100%">
            <el-option v-for="period in periods" :key="period.period_id" :label="period.name" :value="period.period_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="objForm.obj_type" style="width: 100%">
            <el-option v-for="opt in objTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="objForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="objForm.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="objForm.visibility" style="width: 100%">
            <el-option v-for="opt in visibilityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="objVisible = false">取消</el-button>
        <el-button v-perm="'biz:collab:okr:manage'" type="primary" :loading="objSaving" @click="submitObjective">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="krVisible" title="新增关键结果（KR）" width="540px">
      <el-alert v-if="krFormError" type="error" :closable="false" show-icon :title="krFormError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="KR 标题" required>
          <el-input v-model="krForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="指标类型">
          <el-select v-model="krForm.metric_type" style="width: 100%">
            <el-option v-for="opt in metricTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值">
          <el-input-number v-model="krForm.target_value" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="当前值">
          <el-input-number v-model="krForm.current_value" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="信心指数">
          <el-select v-model="krForm.confidence" style="width: 100%">
            <el-option v-for="opt in confidenceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="krVisible = false">取消</el-button>
        <el-button v-perm="'biz:collab:okr:manage'" type="primary" :loading="krSaving" @click="submitKr">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progressVisible" title="更新 KR 进度" width="480px">
      <el-alert v-if="progressFormError" type="error" :closable="false" show-icon :title="progressFormError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="KR">
          <el-text>{{ progressKrTitle }}</el-text>
        </el-form-item>
        <el-form-item label="进度" required>
          <el-input-number v-model="progressForm.progress" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="信心指数">
          <el-select v-model="progressForm.confidence" style="width: 100%">
            <el-option v-for="opt in confidenceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="进展说明">
          <el-input v-model="progressForm.note" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button
          v-perm="'biz:collab:okr:manage'"
          type="primary"
          :loading="progressSaving"
          @click="submitProgress"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="alignVisible" title="目标对齐" width="500px">
      <el-alert v-if="alignFormError" type="error" :closable="false" show-icon :title="alignFormError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="上级目标" required>
          <el-select v-model="alignForm.parent_objective_id" filterable placeholder="选择上级目标" style="width: 100%">
            <el-option v-for="item in parentOptions" :key="item.objective_id" :label="item.title" :value="item.objective_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对齐类型">
          <el-select v-model="alignForm.align_type" style="width: 100%">
            <el-option v-for="opt in alignTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="alignForm.weight" :min="0" :max="1" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="alignVisible = false">取消</el-button>
        <el-button v-perm="'biz:collab:okr:manage'" type="primary" :loading="alignSaving" @click="submitAlign">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.block-title {
  margin: 16px 0 8px;
  font-size: 14px;
}
.kr-panel {
  padding: 8px 16px;
}
</style>
