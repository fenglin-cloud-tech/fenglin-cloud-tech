<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice, formatMoney } from '@fenglin/ui-admin'
import { crmApi, type OverduePlanView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<OverduePlanView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ customer_id: undefined as number | undefined, page: 1, size: 10 })

const collectionVisible = ref(false)
const collectionSaving = ref(false)
const collectionError = ref('')
const collectionTarget = ref<OverduePlanView | null>(null)
const collectionForm = reactive({ collection_flag: 'REMINDED', note: '' })

const flags: Array<{ value: string; label: string }> = [
  { value: 'NONE', label: '未催收' },
  { value: 'REMINDED', label: '已提醒' },
  { value: 'PROMISED', label: '承诺回款' },
  { value: 'ESCALATED', label: '已升级' },
]

function flagLabel(value?: string | null): string {
  if (!value) return '-'
  return flags.find((item) => item.value === value)?.label ?? value
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.payments.overdue(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCollection(row: OverduePlanView) {
  collectionTarget.value = row
  collectionForm.collection_flag = row.collection_flag || 'REMINDED'
  collectionForm.note = row.collection_note || ''
  collectionError.value = ''
  collectionVisible.value = true
}

async function submitCollection() {
  if (!collectionTarget.value) return
  collectionSaving.value = true
  collectionError.value = ''
  try {
    await crmApi.payments.markCollection(collectionTarget.value.plan_id, {
      collection_flag: collectionForm.collection_flag,
      note: collectionForm.note || undefined,
    })
    ElMessage.success('催收标记已更新')
    collectionVisible.value = false
    await load()
  } catch (err) {
    collectionError.value = describeError(err)
  } finally {
    collectionSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 催收" title="逾期催收" description="查询租户内已逾期未结清的回款计划并登记催收标记；数据来自真实回款计划表。">
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '逾期口径由后端按 due_date < CURRENT_DATE 且未结清计算，前端不自行推断。',
        '催收标记仅允许 NONE/REMINDED/PROMISED/ESCALATED，已结清计划后端拒绝标记。',
        '短信/外呼等催收触达能力属于消息中台与云服，本页只做记录与升级。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input-number v-model="query.customer_id" :min="1" placeholder="客户 ID" :controls="false" style="width: 150px" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.customer_id = undefined; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="contract_no" label="合同编号" width="190" />
        <el-table-column prop="customer_name" label="客户" min-width="150" />
        <el-table-column prop="period_no" label="期次" width="80" />
        <el-table-column prop="due_date" label="到期日" width="120" />
        <el-table-column prop="remaining_amount" label="未回款" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.remaining_amount) }}</template>
        </el-table-column>
        <el-table-column prop="overdue_days" label="逾期天数" width="110">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.overdue_days }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="collection_flag" label="催收标记" width="120">
          <template #default="{ row }">{{ flagLabel(row.collection_flag) }}</template>
        </el-table-column>
        <el-table-column prop="collection_note" label="催收备注" min-width="160" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'crm:contract:manage'" link type="warning" @click="openCollection(row)">标记催收</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <el-dialog v-model="collectionVisible" title="登记催收" width="480px">
      <el-alert v-if="collectionError" type="error" :closable="false" show-icon :title="collectionError" style="margin-bottom: 12px" />
      <el-descriptions v-if="collectionTarget" :column="1" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="合同">{{ collectionTarget.contract_no }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ collectionTarget.customer_name }}</el-descriptions-item>
        <el-descriptions-item label="未回款">{{ collectionTarget.remaining_amount }}</el-descriptions-item>
        <el-descriptions-item label="逾期">{{ collectionTarget.overdue_days }} 天</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item label="催收标记">
          <el-select v-model="collectionForm.collection_flag" style="width: 100%">
            <el-option v-for="flag in flags" :key="flag.value" :label="flag.label" :value="flag.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="collectionForm.note" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="collectionVisible = false">取消</el-button>
        <el-button type="primary" :loading="collectionSaving" @click="submitCollection">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
