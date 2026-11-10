<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabObjective, type CollabPeriod } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const periods = ref<CollabPeriod[]>([])
const periodId = ref('')
const objectives = ref<CollabObjective[]>([])
const loading = ref(false)
const error = ref('')

const objVisible = ref(false)
const objSaving = ref(false)
const objError = ref('')
const objForm = reactive({ period_id: '', title: '', description: '', visibility: 'DEPT' })

const krVisible = ref(false)
const krSaving = ref(false)
const krError = ref('')
const krObjectiveId = ref('')
const krObjectiveTitle = ref('')
const krForm = reactive({
  title: '',
  metric_type: 'PERCENT',
  target_value: '',
  current_value: '',
  confidence: 'MID',
})

const progressVisible = ref(false)
const progressSaving = ref(false)
const progressError = ref('')
const progressKrId = ref('')
const progressKrTitle = ref('')
const progressForm = reactive({ progress: '', confidence: 'MID', note: '' })

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

const gapItems = [
  '员工端只展示 owner_id 等于当前账号的目标，没有团队/下属 OKR 视图。',
  'KR 进度接口校验 biz:collab:okr:progress（已与权限种子对齐），新增/编辑目标与 KR 仍需 biz:collab:okr:manage。',
  '目标/KR 没有编辑、删除接口；对齐关系只能在管理端维护。',
  'KR 指标类型后端只接受 PERCENT/NUMBER/BOOLEAN，CURRENCY 会被参数校验拒绝。',
]

function objTypeLabel(value?: string) {
  const map: Record<string, string> = { COMPANY: '公司级', DEPT: '部门级', TEAM: '团队级', PERSONAL: '个人级' }
  return value ? map[value] ?? value : '-'
}

function visibilityLabel(value?: string) {
  return visibilityOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function confidenceLabel(value?: string) {
  return confidenceOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function clampProgress(value?: number) {
  const num = Number(value)
  if (!Number.isFinite(num)) return 0
  return Math.min(100, Math.max(0, num))
}

function statusTag(value?: string) {
  if (value === 'CLOSED' || value === 'DONE') return 'info'
  if (value === 'ACTIVE' || value === 'OPEN') return 'success'
  return 'warning'
}

function parseNumber(text: string): number | undefined {
  const trimmed = text.trim()
  if (!trimmed) return undefined
  const value = Number(trimmed)
  return Number.isFinite(value) ? value : undefined
}

async function loadPeriods() {
  loading.value = true
  error.value = ''
  try {
    periods.value = await collabApi.okr.periods()
    if (!periodId.value) {
      const preferred = periods.value.find((item) => item.status === 'OPEN') ?? periods.value[0]
      periodId.value = String(preferred?.period_id ?? '')
    }
    await loadObjectives()
  } catch (err) {
    error.value = describeError(err)
    loading.value = false
  }
}

async function loadObjectives() {
  if (!auth.userId) {
    error.value = '当前令牌未返回用户 ID，无法按负责人过滤 OKR'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    objectives.value = await collabApi.okr.objectives({
      periodId: periodId.value || undefined,
      ownerId: auth.userId,
    })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openObjective() {
  Object.assign(objForm, {
    period_id: periodId.value || '',
    title: '',
    description: '',
    visibility: 'DEPT',
  })
  objError.value = ''
  objVisible.value = true
}

async function submitObjective() {
  if (!objForm.period_id) {
    objError.value = '请选择所属周期'
    return
  }
  if (!objForm.title.trim()) {
    objError.value = '目标标题为必填项'
    return
  }
  objSaving.value = true
  objError.value = ''
  try {
    await collabApi.okr.createObjective({
      period_id: objForm.period_id,
      obj_type: 'PERSONAL',
      title: objForm.title.trim(),
      description: objForm.description.trim() || undefined,
      visibility: objForm.visibility,
    })
    ElMessage.success('个人 OKR 已创建')
    objVisible.value = false
    await loadObjectives()
  } catch (err) {
    objError.value = describeError(err)
  } finally {
    objSaving.value = false
  }
}

function openKr(row: CollabObjective) {
  krObjectiveId.value = String(row.objective_id)
  krObjectiveTitle.value = row.title
  Object.assign(krForm, {
    title: '',
    metric_type: 'PERCENT',
    target_value: '',
    current_value: '',
    confidence: 'MID',
  })
  krError.value = ''
  krVisible.value = true
}

async function submitKr() {
  if (!krForm.title.trim()) {
    krError.value = 'KR 标题为必填项'
    return
  }
  if (krForm.target_value.trim() && parseNumber(krForm.target_value) === undefined) {
    krError.value = '目标值必须是数字'
    return
  }
  if (krForm.current_value.trim() && parseNumber(krForm.current_value) === undefined) {
    krError.value = '当前值必须是数字'
    return
  }
  krSaving.value = true
  krError.value = ''
  try {
    await collabApi.okr.createKr(krObjectiveId.value, {
      title: krForm.title.trim(),
      metric_type: krForm.metric_type,
      target_value: parseNumber(krForm.target_value),
      current_value: parseNumber(krForm.current_value),
      confidence: krForm.confidence,
    })
    ElMessage.success('KR 已创建')
    krVisible.value = false
    await loadObjectives()
  } catch (err) {
    krError.value = describeError(err)
  } finally {
    krSaving.value = false
  }
}

function openProgress(krId: string, krTitle: string) {
  progressKrId.value = krId
  progressKrTitle.value = krTitle
  Object.assign(progressForm, { progress: '', confidence: 'MID', note: '' })
  progressError.value = ''
  progressVisible.value = true
}

async function submitProgress() {
  const value = parseNumber(progressForm.progress)
  if (value === undefined) {
    progressError.value = '进度必须是数字'
    return
  }
  if (value < 0 || value > 100) {
    progressError.value = '进度需在 0-100 之间'
    return
  }
  progressSaving.value = true
  progressError.value = ''
  try {
    await collabApi.okr.progress(progressKrId.value, {
      progress: value,
      confidence: progressForm.confidence,
      note: progressForm.note.trim() || undefined,
    })
    ElMessage.success('进度已更新')
    progressVisible.value = false
    await loadObjectives()
  } catch (err) {
    progressError.value = describeError(err)
  } finally {
    progressSaving.value = false
  }
}

onMounted(() => {
  if (!auth.userId) {
    error.value = '当前令牌未返回用户 ID，无法按负责人过滤 OKR'
    return
  }
  void loadPeriods()
})
</script>

<template>
  <div>
    <PageHeader module="云协协作" title="我的 OKR" description="按周期查看本人目标与关键结果，进度更新写入进展记录。">
      <template #actions>
        <el-button v-perm="'biz:collab:okr:manage'" type="primary" @click="openObjective">新建个人 OKR</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select v-model="periodId" placeholder="选择周期" style="width: 220px" @change="loadObjectives">
          <el-option v-for="period in periods" :key="period.period_id" :label="period.name" :value="period.period_id" />
        </el-select>
        <el-button :loading="loading" @click="loadPeriods">刷新</el-button>
        <span class="fl-muted">负责人：{{ auth.displayName }} · 目标 {{ objectives.length }} 个</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <div v-loading="loading">
        <el-empty v-if="!objectives.length" description="当前周期暂无个人目标" :image-size="80" />

        <el-card v-for="obj in objectives" :key="obj.objective_id" shadow="hover" class="obj-card">
          <div class="obj-head">
            <div>
              <div class="obj-title">{{ obj.title }}</div>
              <div class="fl-muted">负责人 ID：{{ obj.owner_id }} · 目标 ID：{{ obj.objective_id }}</div>
            </div>
            <div class="obj-tags">
              <el-tag size="small" type="info">{{ objTypeLabel(obj.obj_type) }}</el-tag>
              <el-tag size="small" type="info">{{ visibilityLabel(obj.visibility) }}</el-tag>
              <el-tag size="small" :type="statusTag(obj.status)">{{ obj.status }}</el-tag>
              <el-button v-perm="'biz:collab:okr:manage'" link type="primary" @click="openKr(obj)">新增 KR</el-button>
            </div>
          </div>

          <el-empty v-if="!obj.key_results?.length" description="暂无关键结果" :image-size="48" />
          <el-table v-else :data="obj.key_results" size="small" border>
            <el-table-column prop="title" label="关键结果" min-width="180" show-overflow-tooltip />
            <el-table-column prop="metric_type" label="指标类型" width="100" />
            <el-table-column label="目标值" width="100">
              <template #default="{ row: kr }">{{ kr.target_value ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="当前值" width="100">
              <template #default="{ row: kr }">{{ kr.current_value ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="进度" min-width="190">
              <template #default="{ row: kr }">
                <el-progress :percentage="clampProgress(kr.progress)" :stroke-width="10" />
              </template>
            </el-table-column>
            <el-table-column label="信心" width="90">
              <template #default="{ row: kr }">{{ confidenceLabel(kr.confidence) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
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
        </el-card>
      </div>
    </el-card>

    <el-dialog v-model="objVisible" title="新建个人 OKR" width="560px">
      <el-alert v-if="objError" type="error" :closable="false" show-icon :title="objError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="所属周期" required>
          <el-select v-model="objForm.period_id" placeholder="选择周期" style="width: 100%">
            <el-option v-for="period in periods" :key="period.period_id" :label="period.name" :value="period.period_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-text>PERSONAL（个人目标）</el-text>
        </el-form-item>
        <el-form-item label="目标标题" required>
          <el-input v-model="objForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="目标描述">
          <el-input v-model="objForm.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="objForm.visibility" style="width: 100%">
            <el-option v-for="opt in visibilityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="objVisible = false">取消</el-button>
        <el-button v-perm="'biz:collab:okr:manage'" type="primary" :loading="objSaving" @click="submitObjective">
          提交
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="krVisible" title="新增关键结果（KR）" width="540px">
      <el-alert v-if="krError" type="error" :closable="false" show-icon :title="krError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="所属目标">
          <el-text>{{ krObjectiveTitle }}</el-text>
        </el-form-item>
        <el-form-item label="KR 标题" required>
          <el-input v-model="krForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="指标类型">
          <el-select v-model="krForm.metric_type" style="width: 100%">
            <el-option v-for="opt in metricTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值">
          <el-input v-model="krForm.target_value" placeholder="数字，可留空" />
        </el-form-item>
        <el-form-item label="当前值">
          <el-input v-model="krForm.current_value" placeholder="数字，可留空" />
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
      <el-alert
        v-if="progressError"
        type="error"
        :closable="false"
        show-icon
        :title="progressError"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="KR">
          <el-text>{{ progressKrTitle }}</el-text>
        </el-form-item>
        <el-form-item label="进度(%)" required>
          <el-input v-model="progressForm.progress" placeholder="0-100 的数值，原样提交" />
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
  </div>
</template>

<style scoped>
.obj-card {
  margin-bottom: 16px;
}
.obj-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.obj-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}
.obj-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
