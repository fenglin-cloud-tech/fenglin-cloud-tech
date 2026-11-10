<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime, label, riskLevel } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')
const keyword = ref('')

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  uscc: '',
  contact_name: '',
  contact_mobile: '',
  email: '',
  legal_rep: '',
  address: '',
  risk_level: 'LOW',
  remark: '',
})

const gapItems = [
  '相对方联系人手机号后端加密存储（contact_mobile_enc），列表只回显后四位；明文查看需 legal:party:pii:view，后端当前未提供解密接口。',
  '统一社会信用代码按哈希去重，重复 USCC 创建会返回真实 4xx 错误。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.parties.list({ keyword: keyword.value || undefined, page: page.value, size: size.value })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.name.trim()) {
    ElMessage.warning('相对方名称必填')
    return
  }
  saving.value = true
  try {
    await legalApi.parties.create({
      name: form.name.trim(),
      uscc: form.uscc || undefined,
      contact_name: form.contact_name || undefined,
      contact_mobile: form.contact_mobile || undefined,
      email: form.email || undefined,
      legal_rep: form.legal_rep || undefined,
      address: form.address || undefined,
      risk_level: form.risk_level,
      remark: form.remark || undefined,
    })
    ElMessage.success('相对方已创建')
    createVisible.value = false
    form.name = ''
    form.uscc = ''
    form.contact_name = ''
    form.contact_mobile = ''
    form.email = ''
    form.legal_rep = ''
    form.address = ''
    form.remark = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
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
      module="云法 Legal"
      title="相对方"
      description="合同相对方档案与风险等级；数据来自 /api/legal/v1/contract-parties。"
    >
      <template #actions>
        <el-input v-model="keyword" placeholder="名称关键字" clearable style="width: 180px" @keyup.enter="changePage(1)" />
        <el-button v-perm="'legal:party:manage'" type="primary" @click="createVisible = true">新建相对方</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="uscc" label="统一社会信用代码" width="190" />
        <el-table-column prop="contact_name" label="联系人" width="110" />
        <el-table-column label="手机号" width="120">
          <template #default="{ row }">{{ row.contact_mobile_tail ? `****${row.contact_mobile_tail}` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="legal_rep" label="法定代表人" width="120" />
        <el-table-column label="风险" width="80">
          <template #default="{ row }">{{ label(riskLevel, row.risk_level) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新建相对方" width="600px">
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="form.uscc" maxlength="32" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact_name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.contact_mobile" placeholder="后端加密存储，仅回显后四位" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="法定代表人"><el-input v-model="form.legal_rep" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.risk_level" style="width: 100%">
            <el-option v-for="(text, code) in riskLevel" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
