<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtAmount, fmtTime } from './labels'

const tab = ref('nodes')

const nodes = ref<LegalRecord[]>([])
const nodeTotal = ref(0)
const nodePage = ref(1)
const nodeLoading = ref(false)
const nodeError = ref('')
const nodeFilter = reactive({ contract_id: '', status: '' })

const reminders = ref<LegalRecord[]>([])
const reminderTotal = ref(0)
const reminderPage = ref(1)
const reminderLoading = ref(false)
const reminderError = ref('')
const reminderFilter = reactive({ contract_id: '', status: '' })

const nodeVisible = ref(false)
const nodeSaving = ref(false)
const nodeForm = reactive({
  contract_id: undefined as number | undefined,
  node_type: 'PAYMENT',
  name: '',
  plan_date: '',
  amount: undefined as number | undefined,
  owner_id: undefined as number | undefined,
  remark: '',
})

const reminderVisible = ref(false)
const reminderSaving = ref(false)
const reminderForm = reactive({ contract_id: undefined as number | undefined, offset_days: 30, channel: 'INBOX' })

const gapItems = [
  '履约节点与到期提醒均为真实表（legal_performance_node / legal_expiry_reminder），不走消息中台自动发送。',
  '新建提醒要求 tenant:legal:reminder:config；提醒发送状态由后端定时任务推进，本页只读展示。',
]

async function loadNodes() {
  nodeLoading.value = true
  nodeError.value = ''
  try {
    const result = await legalApi.performance.list({
      contract_id: nodeFilter.contract_id ? nodeFilter.contract_id : undefined,
      status: nodeFilter.status || undefined,
      page: nodePage.value,
      size: 20,
    })
    nodes.value = result.records
    nodeTotal.value = result.total
  } catch (err) {
    nodes.value = []
    nodeError.value = describeError(err)
  } finally {
    nodeLoading.value = false
  }
}

async function loadReminders() {
  reminderLoading.value = true
  reminderError.value = ''
  try {
    const result = await legalApi.reminders.list({
      contract_id: reminderFilter.contract_id ? reminderFilter.contract_id : undefined,
      status: reminderFilter.status || undefined,
      page: reminderPage.value,
      size: 20,
    })
    reminders.value = result.records
    reminderTotal.value = result.total
  } catch (err) {
    reminders.value = []
    reminderError.value = describeError(err)
  } finally {
    reminderLoading.value = false
  }
}

async function createNode() {
  if (!nodeForm.contract_id || !nodeForm.name.trim() || !nodeForm.node_type) {
    ElMessage.warning('合同ID、节点类型、名称必填')
    return
  }
  nodeSaving.value = true
  try {
    await legalApi.performance.create({
      contract_id: nodeForm.contract_id,
      node_type: nodeForm.node_type,
      name: nodeForm.name.trim(),
      plan_date: nodeForm.plan_date || undefined,
      amount: nodeForm.amount ?? undefined,
      owner_id: nodeForm.owner_id ?? undefined,
      remark: nodeForm.remark || undefined,
    })
    ElMessage.success('履约节点已创建')
    nodeVisible.value = false
    nodeForm.name = ''
    await loadNodes()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    nodeSaving.value = false
  }
}

async function completeNode(row: LegalRecord) {
  try {
    await legalApi.performance.updateStatus(row.id, 'DONE')
    ElMessage.success('节点已完成')
    await loadNodes()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function createReminder() {
  if (!reminderForm.contract_id) {
    ElMessage.warning('合同ID必填')
    return
  }
  reminderSaving.value = true
  try {
    await legalApi.reminders.create({
      contract_id: reminderForm.contract_id,
      offset_days: reminderForm.offset_days,
      channel: reminderForm.channel,
    })
    ElMessage.success('到期提醒已创建')
    reminderVisible.value = false
    await loadReminders()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    reminderSaving.value = false
  }
}

function switchTab(value: string) {
  tab.value = value
  if (value === 'nodes' && nodes.value.length === 0) loadNodes()
  if (value === 'reminders' && reminders.value.length === 0) loadReminders()
}

onMounted(() => {
  loadNodes()
  loadReminders()
})
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="履约与到期提醒"
      description="履约节点跟踪与合同到期提醒；数据来自 /api/legal/v1/performance-nodes 与 /expiry-reminders。"
    >
      <template #actions>
        <el-button v-perm="'legal:performance:manage'" type="primary" @click="nodeVisible = true">新增履约节点</el-button>
        <el-button v-perm="'tenant:legal:reminder:config'" type="primary" plain @click="reminderVisible = true">新增到期提醒</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane label="履约节点" name="nodes">
        <el-alert v-if="nodeError" type="error" :closable="false" show-icon :title="nodeError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="nodeFilter.contract_id" placeholder="合同ID" clearable style="width: 140px" />
            <el-select v-model="nodeFilter.status" placeholder="全部状态" clearable style="width: 130px">
              <el-option label="待履约" value="PENDING" />
              <el-option label="已完成" value="DONE" />
              <el-option label="已逾期" value="OVERDUE" />
            </el-select>
            <el-button :loading="nodeLoading" @click="loadNodes">查询</el-button>
          </div>
          <el-table :data="nodes" v-loading="nodeLoading" border stripe max-height="520">
            <el-table-column prop="contract_id" label="合同ID" width="170" />
            <el-table-column prop="node_type" label="节点类型" width="120" />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="plan_date" label="计划日期" width="110" />
            <el-table-column prop="actual_date" label="实际日期" width="110" />
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'DONE'" v-perm="'legal:performance:manage'" link type="primary" @click="completeNode(row)">完成</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="nodeTotal"
            :page-size="20"
            :current-page="nodePage"
            @current-change="(next: number) => { nodePage = next; loadNodes() }"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="到期提醒" name="reminders">
        <el-alert v-if="reminderError" type="error" :closable="false" show-icon :title="reminderError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="reminderFilter.contract_id" placeholder="合同ID" clearable style="width: 140px" />
            <el-select v-model="reminderFilter.status" placeholder="全部状态" clearable style="width: 130px">
              <el-option label="待发送" value="PENDING" />
              <el-option label="已发送" value="SENT" />
            </el-select>
            <el-button :loading="reminderLoading" @click="loadReminders">查询</el-button>
          </div>
          <el-table :data="reminders" v-loading="reminderLoading" border stripe max-height="520">
            <el-table-column prop="contract_id" label="合同ID" width="170" />
            <el-table-column prop="offset_days" label="提前天数" width="100" />
            <el-table-column prop="trigger_date" label="触发日期" width="120" />
            <el-table-column prop="channel" label="渠道" width="100" />
            <el-table-column prop="receiver_ids" label="接收人" min-width="140" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="发送时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.sent_at) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="reminderTotal"
            :page-size="20"
            :current-page="reminderPage"
            @current-change="(next: number) => { reminderPage = next; loadReminders() }"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="nodeVisible" title="新增履约节点" width="560px">
      <el-form label-width="100px">
        <el-form-item label="合同ID" required><el-input-number v-model="nodeForm.contract_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="节点类型" required>
          <el-select v-model="nodeForm.node_type" style="width: 100%">
            <el-option label="付款" value="PAYMENT" />
            <el-option label="交付" value="DELIVERY" />
            <el-option label="验收" value="ACCEPTANCE" />
            <el-option label="续约" value="RENEWAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="nodeForm.name" maxlength="200" /></el-form-item>
        <el-form-item label="计划日期"><el-date-picker v-model="nodeForm.plan_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="nodeForm.amount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="负责人ID"><el-input-number v-model="nodeForm.owner_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="nodeForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeVisible = false">取消</el-button>
        <el-button type="primary" :loading="nodeSaving" @click="createNode">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reminderVisible" title="新增到期提醒" width="480px">
      <el-form label-width="100px">
        <el-form-item label="合同ID" required><el-input-number v-model="reminderForm.contract_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="提前天数"><el-input-number v-model="reminderForm.offset_days" :min="1" :max="365" style="width: 100%" /></el-form-item>
        <el-form-item label="通知渠道">
          <el-select v-model="reminderForm.channel" style="width: 100%">
            <el-option label="站内信" value="INBOX" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="IM" value="IM" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reminderVisible = false">取消</el-button>
        <el-button type="primary" :loading="reminderSaving" @click="createReminder">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
