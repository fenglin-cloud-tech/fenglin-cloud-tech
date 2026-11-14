<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { financeApi, type FinanceCarbonActivity } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const activities = ref<FinanceCarbonActivity[]>([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const dialogVisible = ref(false)
const periodCode = ref('')

const gapItems = [
  '缺“撤销/编辑已填报活动”的接口，提交后只能等待核验。',
  '排放因子列表 /carbon/factors 需要 biz:finance:carbon:calculate，员工端不提供因子下拉，由后端按 scope + activity_type 自动匹配因子。',
  '缺证据附件上传接口，evidence_file_id 无法从员工端填写。',
]

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待核验',
  VERIFIED: '已核验',
  LOCKED: '已锁定',
  REJECTED: '已驳回',
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'VERIFIED' || value === 'LOCKED') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'SUBMITTED') return 'warning'
  return 'info'
}

// 活动数据量不是金额，但同样按千分位 + 两位小数展示，避免 12345.6000 直出。
function qty(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  const num = Number(value)
  return Number.isNaN(num) ? String(value) : num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const today = new Date().toISOString().slice(0, 10)
const form = reactive({
  activity_type: 'ELECTRICITY',
  scope: 'SCOPE2',
  activity_date: today,
  amount: '',
  unit: 'kWh',
  data_source: '',
})

async function loadActivities() {
  loading.value = true
  error.value = ''
  try {
    activities.value = await financeApi.carbon.myActivities({
      periodCode: periodCode.value || undefined,
      page: 1,
      size: 50,
    })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!form.amount || Number(form.amount) <= 0) {
    ElMessage.warning('请填写活动数据量')
    return
  }
  saving.value = true
  try {
    await financeApi.carbon.createActivity({
      activity_type: form.activity_type,
      scope: form.scope,
      activity_date: form.activity_date,
      amount: form.amount,
      unit: form.unit,
      data_source: form.data_source || undefined,
    })
    ElMessage.success('碳活动已提交')
    dialogVisible.value = false
    form.amount = ''
    form.data_source = ''
    await loadActivities()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

onMounted(loadActivities)
</script>

<template>
  <div>
    <PageHeader module="云财 Finance" title="碳活动填报" description="提交能源/差旅等活动数据并查看核验状态；数据直接来自碳活动接口。" />

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-button type="primary" @click="dialogVisible = true">填报活动数据</el-button>
        <el-input v-model="periodCode" placeholder="期间，如 202611" style="width: 160px" clearable />
        <el-button :loading="loading" @click="loadActivities">查询</el-button>
        <span class="fl-muted">{{ auth.displayName }}</span>
      </div>

      <div v-loading="loading">
        <el-empty v-if="!activities.length && !error" description="暂无碳活动记录" :image-size="80" />
        <el-table v-else :data="activities" border stripe>
          <el-table-column prop="activity_no" label="活动单号" width="180" />
          <el-table-column prop="activity_type" label="活动类型" width="140" />
          <el-table-column prop="scope" label="范围" width="100" />
          <el-table-column prop="activity_date" label="活动日期" width="120" />
          <el-table-column prop="period_code" label="期间" width="100" />
          <el-table-column label="数据量" width="150" align="right">
            <template #default="{ row }">{{ qty(row.amount) }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column prop="data_source" label="数据来源" min-width="140" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="填报碳活动" width="520px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="活动类型">
          <el-select v-model="form.activity_type" filterable allow-create style="width: 100%">
            <el-option label="外购电力" value="ELECTRICITY" />
            <el-option label="天然气" value="NATURAL_GAS" />
            <el-option label="汽油" value="GASOLINE" />
            <el-option label="柴油" value="DIESEL" />
            <el-option label="差旅-飞机" value="FLIGHT" />
            <el-option label="办公纸张" value="PAPER" />
          </el-select>
        </el-form-item>
        <el-form-item label="排放范围">
          <el-select v-model="form.scope" style="width: 100%">
            <el-option label="范围一（直接排放）" value="SCOPE1" />
            <el-option label="范围二（间接能源）" value="SCOPE2" />
            <el-option label="范围三（价值链）" value="SCOPE3" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动日期">
          <el-date-picker v-model="form.activity_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据量">
          <el-input v-model="form.amount" placeholder="例如 1200">
            <template #append>
              <el-input v-model="form.unit" style="width: 80px" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="数据来源">
          <el-input v-model="form.data_source" placeholder="例如 电费单 / 差旅平台导出" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
