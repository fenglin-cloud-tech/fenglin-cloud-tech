<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const filters = reactive({ seal_type: '', status: '' })

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  seal_type: 'CONTRACT',
  subject_name: '',
  image_ref: '',
  cert_serial: '',
  provider_code: 'ESIGN',
  keeper_id: undefined as number | undefined,
  remark: '',
})

const gapItems = [
  '印章查看需 tenant:legal:seal:view，新建/停用需 tenant:legal:seal:config，两个权限点均已在 hub_permission 中登记。',
  '印章图片仅登记 image_ref，不承载图片上传；证书序列号与算法由电子签服务商提供。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.seals.list({
      seal_type: filters.seal_type || undefined,
      status: filters.status || undefined,
      page: page.value,
      size: 20,
    })
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
  if (!form.name.trim() || !form.seal_type) {
    ElMessage.warning('印章名称与类型必填')
    return
  }
  saving.value = true
  try {
    await legalApi.seals.create({
      name: form.name.trim(),
      seal_type: form.seal_type,
      subject_name: form.subject_name || undefined,
      image_ref: form.image_ref || undefined,
      cert_serial: form.cert_serial || undefined,
      provider_code: form.provider_code || undefined,
      keeper_id: form.keeper_id ?? undefined,
      remark: form.remark || undefined,
    })
    ElMessage.success('印章已登记')
    createVisible.value = false
    form.name = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function revoke(row: LegalRecord) {
  try {
    await ElMessageBox.confirm(`确认停用印章 ${row.seal_no}？`, '停用确认', { type: 'warning' })
    await legalApi.seals.revoke(row.id)
    ElMessage.success('印章已停用')
    await load()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(describeError(err))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="印章管理"
      description="企业印章登记与停用；数据来自 /api/legal/v1/admin/seals。"
    >
      <template #actions>
        <el-select v-model="filters.seal_type" placeholder="全部类型" clearable style="width: 130px" @change="load">
          <el-option label="合同章" value="CONTRACT" />
          <el-option label="公章" value="OFFICIAL" />
          <el-option label="财务章" value="FINANCE" />
          <el-option label="法人章" value="LEGAL_PERSON" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px" @change="load">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="REVOKED" />
        </el-select>
        <el-button v-perm="'tenant:legal:seal:config'" type="primary" @click="createVisible = true">登记印章</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="seal_no" label="印章编号" width="160" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="seal_type" label="类型" width="110" />
        <el-table-column prop="subject_name" label="主体" min-width="160" show-overflow-tooltip />
        <el-table-column prop="provider_code" label="服务商" width="100" />
        <el-table-column prop="cert_serial" label="证书序列号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="keeper_id" label="保管人" width="90" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="登记时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" v-perm="'tenant:legal:seal:config'" link type="danger" @click="revoke(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="20"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="登记印章" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.seal_type" style="width: 100%">
            <el-option label="合同章" value="CONTRACT" />
            <el-option label="公章" value="OFFICIAL" />
            <el-option label="财务章" value="FINANCE" />
            <el-option label="法人章" value="LEGAL_PERSON" />
          </el-select>
        </el-form-item>
        <el-form-item label="主体"><el-input v-model="form.subject_name" /></el-form-item>
        <el-form-item label="图片引用"><el-input v-model="form.image_ref" placeholder="文件中台 file_ref" /></el-form-item>
        <el-form-item label="证书序列号"><el-input v-model="form.cert_serial" /></el-form-item>
        <el-form-item label="服务商"><el-input v-model="form.provider_code" /></el-form-item>
        <el-form-item label="保管人ID"><el-input-number v-model="form.keeper_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>
