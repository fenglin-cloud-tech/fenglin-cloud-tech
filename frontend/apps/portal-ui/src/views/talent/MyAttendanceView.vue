<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentAttendanceSummary,
  type TalentLeaveBalance,
  type TalentLeaveRequest,
  type TalentLeaveType,
} from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const month = ref(new Date().toISOString().slice(0, 7))
const summary = ref<TalentAttendanceSummary | null>(null)
const summaryLoading = ref(false)
const summaryError = ref('')

const balances = ref<TalentLeaveBalance[]>([])
const leaveTypes = ref<TalentLeaveType[]>([])
const requests = ref<TalentLeaveRequest[]>([])
const requestTotal = ref(0)
const leaveLoading = ref(false)
const leaveError = ref('')
const requestStatus = ref('')
const requestPage = ref(1)

const clocking = ref(false)
const clockType = ref('IN')

const applyVisible = ref(false)
const applySaving = ref(false)
const applyError = ref('')
const applyForm = reactive({
  leave_type_id: '' as string | number | '',
  start_date: '',
  end_date: '',
  days: 1,
  reason: '',
})

const gapItems = [
  '“我的考勤”只提供月度汇总（GET /attendance/my），后端没有员工自助的考勤明细接口；明细记录 /attendance/records 需要 talent:admin:attendance:list。',
  '请假申请提交后走工作流审批，状态为 APPROVING；撤销调用 /leave/requests/{id}/cancel，仅审批中或已通过的申请可撤销。',
  '请假天数由前端按起止日期自动带出，可按半天调整后提交；额度不足会返回后端真实错误。',
]

const requestStatusLabels: Record<string, string> = {
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
}

function statusLabel(value?: string) {
  return value ? requestStatusLabels[value] ?? value : '-'
}

function statusTag(value?: string) {
  if (value === 'APPROVED') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'CANCELLED') return 'info'
  return 'warning'
}

function hoursOf(minutes?: number) {
  return minutes === undefined || minutes === null ? '-' : (minutes / 60).toFixed(1)
}

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    summary.value = await talentApi.attendance.my({ month: month.value || undefined })
  } catch (err) {
    summaryError.value = describeError(err)
  } finally {
    summaryLoading.value = false
  }
}

async function loadLeave() {
  if (!auth.userId) {
    leaveError.value = '当前令牌未返回用户 ID，无法查询本人假期'
    return
  }
  leaveLoading.value = true
  leaveError.value = ''
  try {
    const year = Number(month.value.slice(0, 4)) || new Date().getFullYear()
    const [balanceList, typeList, requestPageData] = await Promise.all([
      talentApi.leave.balances({ year }),
      talentApi.leave.types(),
      talentApi.leave.myRequests({
        status: requestStatus.value || undefined,
        page: requestPage.value,
        size: 10,
      }),
    ])
    balances.value = balanceList
    leaveTypes.value = typeList
    requests.value = requestPageData.records
    requestTotal.value = requestPageData.total
  } catch (err) {
    leaveError.value = describeError(err)
  } finally {
    leaveLoading.value = false
  }
}

async function clock() {
  clocking.value = true
  try {
    await talentApi.attendance.clock({ clock_type: clockType.value, source: 'WEB' })
    ElMessage.success(clockType.value === 'IN' ? '上班卡已记录' : '下班卡已记录')
    await loadSummary()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    clocking.value = false
  }
}

function openApply() {
  Object.assign(applyForm, {
    leave_type_id: leaveTypes.value[0]?.id ?? '',
    start_date: '',
    end_date: '',
    days: 1,
    reason: '',
  })
  applyError.value = ''
  applyVisible.value = true
}

function recalcDays() {
  if (!applyForm.start_date || !applyForm.end_date) return
  const start = new Date(applyForm.start_date)
  const end = new Date(applyForm.end_date)
  const diff = Math.floor((end.getTime() - start.getTime()) / 86400000) + 1
  applyForm.days = diff > 0 ? diff : 1
}

async function submitApply() {
  if (!applyForm.leave_type_id || !applyForm.start_date || !applyForm.end_date) {
    applyError.value = '请选择假期类型与起止日期'
    return
  }
  if (!applyForm.days || applyForm.days <= 0) {
    applyError.value = '请假天数必须大于 0'
    return
  }
  applySaving.value = true
  applyError.value = ''
  try {
    const result = await talentApi.leave.create({
      leave_type_id: applyForm.leave_type_id,
      start_date: applyForm.start_date,
      end_date: applyForm.end_date,
      days: applyForm.days,
      reason: applyForm.reason.trim() || undefined,
      idempotency_key: `leave-${auth.userId}-${Date.now()}`,
    })
    ElMessage.success(`请假申请已提交：${result.request_no}`)
    applyVisible.value = false
    requestPage.value = 1
    await loadLeave()
  } catch (err) {
    applyError.value = describeError(err)
  } finally {
    applySaving.value = false
  }
}

async function cancelRequest(row: TalentLeaveRequest) {
  try {
    await talentApi.leave.cancel(row.id)
    ElMessage.success('请假申请已撤销')
    await loadLeave()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function changeMonth() {
  void loadSummary()
  void loadLeave()
}

onMounted(() => {
  void loadSummary()
  void loadLeave()
})
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="我的考勤与请假" description="月度考勤汇总、上下班打卡、假期额度与请假申请。">
      <template #actions>
        <el-date-picker
          v-model="month"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择月份"
          style="width: 150px"
          @change="changeMonth"
        />
        <el-button :loading="summaryLoading || leaveLoading" @click="changeMonth">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select v-model="clockType" style="width: 120px">
          <el-option label="上班打卡" value="IN" />
          <el-option label="下班打卡" value="OUT" />
        </el-select>
        <el-button type="primary" :loading="clocking" @click="clock">立即打卡</el-button>
        <span class="fl-muted">{{ auth.displayName }} · {{ month }}</span>
      </div>

      <el-alert
        v-if="summaryError"
        type="error"
        :closable="false"
        show-icon
        :title="summaryError"
        style="margin-bottom: 12px"
      />

      <div v-loading="summaryLoading" class="stat-grid">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">正常天数</div>
          <div class="stat-value">{{ summary?.normal_days ?? 0 }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">迟到天数</div>
          <div class="stat-value">{{ summary?.late_days ?? 0 }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">早退天数</div>
          <div class="stat-value">{{ summary?.early_days ?? 0 }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">迟到且早退</div>
          <div class="stat-value">{{ summary?.late_early_days ?? 0 }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">缺卡天数</div>
          <div class="stat-value">{{ summary?.missing_days ?? 0 }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">工时（小时）</div>
          <div class="stat-value">{{ hoursOf(summary?.work_minutes) }}</div>
        </el-card>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>我的假期额度</template>
      <el-alert v-if="leaveError" type="error" :closable="false" show-icon :title="leaveError" style="margin-bottom: 12px" />
      <el-table :data="balances" v-loading="leaveLoading" border stripe size="small">
        <el-table-column prop="type_name" label="假期类型" min-width="140" />
        <el-table-column prop="year" label="年度" width="90" />
        <el-table-column prop="total_days" label="总额度（天）" width="120" />
        <el-table-column prop="used_days" label="已用（天）" width="110" />
        <el-table-column prop="available_days" label="可用（天）" width="110" />
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的请假申请</span>
          <el-button type="primary" plain size="small" @click="openApply">发起请假</el-button>
        </div>
      </template>

      <div class="fl-toolbar">
        <el-select
          v-model="requestStatus"
          placeholder="申请状态"
          clearable
          style="width: 140px"
          @change="requestPage = 1; loadLeave()"
        >
          <el-option label="审批中" value="APPROVING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已撤销" value="CANCELLED" />
        </el-select>
        <span class="fl-muted">共 {{ requestTotal }} 条</span>
      </div>

      <el-table :data="requests" v-loading="leaveLoading" border stripe size="small">
        <el-table-column prop="request_no" label="申请单号" width="190" />
        <el-table-column prop="leave_type_name" label="假期类型" width="120" />
        <el-table-column label="起止日期" width="200">
          <template #default="{ row }">{{ row.start_date }} ~ {{ row.end_date }}</template>
        </el-table-column>
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.lr_status)" size="small">{{ statusLabel(row.lr_status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approve_opinion" label="审批意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="['APPROVING', 'APPROVED'].includes(row.lr_status)"
              link
              type="danger"
              @click="cancelRequest(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="requestPage"
        :page-size="10"
        :total="requestTotal"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="loadLeave"
      />
    </el-card>

    <el-dialog v-model="applyVisible" title="发起请假" width="520px">
      <el-alert v-if="applyError" type="error" :closable="false" show-icon :title="applyError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="假期类型" required>
          <el-select v-model="applyForm.leave_type_id" placeholder="选择假期类型" style="width: 100%">
            <el-option
              v-for="type in leaveTypes"
              :key="String(type.id)"
              :label="`${type.type_name}${type.paid ? '（带薪）' : '（无薪）'}`"
              :value="type.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker
            v-model="applyForm.start_date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="recalcDays"
          />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker
            v-model="applyForm.end_date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="recalcDays"
          />
        </el-form-item>
        <el-form-item label="请假天数" required>
          <el-input-number v-model="applyForm.days" :min="0.5" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="请假事由">
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySaving" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
