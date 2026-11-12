<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { contractCategory, fmtTime, label } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')
const filters = reactive({ category: '', status: '' })

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', category: 'SALES', visible_scope: 'TENANT', remark: '' })

const publishVisible = ref(false)
const publishing = ref(false)
const publishForm = reactive({ id: 0, content_ref: '', change_summary: '' })

const varVisible = ref(false)
const varSaving = ref(false)
const varForm = reactive({ id: 0, version_id: undefined as number | undefined, variables: '' })

const gapItems = [
  '模板正文存储于文件中台，本页只登记 content_ref 与内容哈希，不承载富文本编辑器。',
  '模板变量保存要求 tenant:legal:template:variable，变量以 JSON 数组提交（var_key/var_label/data_type）。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.templates.list({
      category: filters.category || undefined,
      status: filters.status || undefined,
      page: page.value,
      size: size.value,
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
  if (!form.name.trim()) {
    ElMessage.warning('模板名称必填')
    return
  }
  saving.value = true
  try {
    await legalApi.templates.create({
      name: form.name.trim(),
      category: form.category,
      visible_scope: form.visible_scope,
      remark: form.remark || undefined,
    })
    ElMessage.success('模板已创建')
    createVisible.value = false
    form.name = ''
    form.remark = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

function openPublish(row: LegalRecord) {
  publishForm.id = row.id
  publishForm.content_ref = ''
  publishForm.change_summary = ''
  publishVisible.value = true
}

async function publish() {
  if (!publishForm.content_ref.trim()) {
    ElMessage.warning('请填写正文引用 content_ref')
    return
  }
  publishing.value = true
  try {
    await legalApi.templates.publish(publishForm.id, {
      content_ref: publishForm.content_ref.trim(),
      change_summary: publishForm.change_summary || undefined,
    })
    ElMessage.success('模板版本已发布')
    publishVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    publishing.value = false
  }
}

function openVariables(row: LegalRecord) {
  varForm.id = row.id
  varForm.version_id = row.current_version_id ? row.current_version_id : undefined
  varForm.variables = '[\n  { "var_key": "party_name", "var_label": "相对方名称", "data_type": "STRING", "required": true }\n]'
  varVisible.value = true
}

async function saveVariables() {
  let parsed: unknown
  try {
    parsed = JSON.parse(varForm.variables)
  } catch {
    ElMessage.error('变量不是合法 JSON')
    return
  }
  if (!Array.isArray(parsed)) {
    ElMessage.error('变量必须是 JSON 数组')
    return
  }
  varSaving.value = true
  try {
    await legalApi.templates.saveVariables(varForm.id, { version_id: varForm.version_id, variables: parsed })
    ElMessage.success('变量已保存')
    varVisible.value = false
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    varSaving.value = false
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
      title="合同模板"
      description="模板登记、版本发布与变量配置；数据来自 /api/legal/v1/contract-templates。"
    >
      <template #actions>
        <el-select v-model="filters.category" placeholder="全部类别" clearable style="width: 130px" @change="changePage(1)">
          <el-option v-for="(text, code) in contractCategory" :key="code" :label="text" :value="code" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px" @change="changePage(1)">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
        <el-button v-perm="'legal:template:manage'" type="primary" @click="createVisible = true">新建模板</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="template_no" label="模板编号" width="150" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="110">
          <template #default="{ row }">{{ label(contractCategory, row.category) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="visible_scope" label="可见范围" width="110" />
        <el-table-column prop="owner_id" label="负责人" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-perm="'legal:template:version'" link type="primary" @click="openPublish(row)">发布版本</el-button>
            <el-button v-perm="'tenant:legal:template:variable'" link type="primary" @click="openVariables(row)">变量</el-button>
          </template>
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

    <el-dialog v-model="createVisible" title="新建模板" width="520px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="(text, code) in contractCategory" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="form.visible_scope" style="width: 100%">
            <el-option label="全租户" value="TENANT" />
            <el-option label="指定组织" value="ORG" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publishVisible" title="发布模板版本" width="520px">
      <el-form label-width="110px">
        <el-form-item label="正文引用" required><el-input v-model="publishForm.content_ref" placeholder="文件中台 file_ref 或 URL" /></el-form-item>
        <el-form-item label="变更说明"><el-input v-model="publishForm.change_summary" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="varVisible" title="模板变量" width="640px">
      <el-form label-width="100px">
        <el-form-item label="版本ID"><el-input-number v-model="varForm.version_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="变量JSON">
          <el-input v-model="varForm.variables" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varVisible = false">取消</el-button>
        <el-button type="primary" :loading="varSaving" @click="saveVariables">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
