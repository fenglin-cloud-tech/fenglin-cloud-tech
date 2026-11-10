<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const tree = ref<ServiceRecord[]>([])
const items = ref<ServiceRecord[]>([])
const policies = ref<ServiceRecord[]>([])
const selectedCatalogId = ref<number | ''>('')
const loading = ref(false)
const error = ref('')

const catalogVisible = ref(false)
const catalogSaving = ref(false)
const catalogError = ref('')
const catalogForm = reactive({ id: '', parent_id: '', name: '', sort_no: 1, status: 'ENABLED' })

const policyVisible = ref(false)
const policySaving = ref(false)
const policyError = ref('')
const policyForm = reactive({
  name: '',
  priority: 'P3',
  ticket_type: 'INCIDENT',
  response_minutes: 60,
  resolve_minutes: 480,
  enabled: true,
})

const gapItems = [
  '后端只有目录（catalog）保存接口 /admin/catalog，没有目录项（catalog item）创建/编辑接口，目录项只能由迁移或后台数据维护。',
  'SLA 策略保存接口 /admin/sla-policies 未提供删除接口，只能新增或覆盖。',
  '目录项申请接口 /catalog/items/{id}/apply 会创建工单，员工端使用。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [treeResult, policyResult] = await Promise.all([
      serviceApi.catalog.tree(),
      serviceApi.admin.slaPolicies().catch(() => [] as ServiceRecord[]),
    ])
    tree.value = treeResult
    policies.value = policyResult
    if (!selectedCatalogId.value && treeResult.length) {
      selectedCatalogId.value = treeResult[0].id
      await loadItems()
    }
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadItems() {
  try {
    items.value = await serviceApi.catalog.items(selectedCatalogId.value ? Number(selectedCatalogId.value) : undefined)
  } catch (err) {
    items.value = []
    error.value = describeError(err)
  }
}

async function submitCatalog() {
  if (!catalogForm.name.trim()) {
    catalogError.value = '请填写目录名称'
    return
  }
  catalogSaving.value = true
  catalogError.value = ''
  try {
    await serviceApi.admin.saveCatalog({
      id: catalogForm.id ? Number(catalogForm.id) : undefined,
      parent_id: catalogForm.parent_id ? Number(catalogForm.parent_id) : undefined,
      name: catalogForm.name.trim(),
      sort_no: Number(catalogForm.sort_no),
      status: catalogForm.status,
    })
    ElMessage.success('目录已保存')
    catalogVisible.value = false
    catalogForm.name = ''
    catalogForm.id = ''
    await load()
  } catch (err) {
    catalogError.value = describeError(err)
  } finally {
    catalogSaving.value = false
  }
}

async function submitPolicy() {
  if (!policyForm.name.trim()) {
    policyError.value = '请填写策略名称'
    return
  }
  policySaving.value = true
  policyError.value = ''
  try {
    await serviceApi.admin.saveSlaPolicy({
      name: policyForm.name.trim(),
      priority: policyForm.priority,
      ticket_type: policyForm.ticket_type,
      response_minutes: Number(policyForm.response_minutes),
      resolve_minutes: Number(policyForm.resolve_minutes),
      enabled: policyForm.enabled,
    })
    ElMessage.success('SLA 策略已保存')
    policyVisible.value = false
    policyForm.name = ''
    policies.value = await serviceApi.admin.slaPolicies()
  } catch (err) {
    policyError.value = describeError(err)
  } finally {
    policySaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="服务目录与 SLA"
      description="服务目录树、目录项与 SLA 策略；数据来自 /api/service/v1/catalog 与 /admin。"
    >
      <template #actions>
        <el-button v-perm="'tenant:service:catalog:config'" type="primary" @click="catalogVisible = true">新增目录</el-button>
        <el-button v-perm="'tenant:service:sla:config'" plain @click="policyVisible = true">新增 SLA 策略</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>服务目录树</span></template>
          <el-tree
            :data="tree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            default-expand-all
            highlight-current
            @node-click="(node: ServiceRecord) => { selectedCatalogId = node.id; loadItems() }"
          />
          <el-empty v-if="!tree.length" description="暂无目录" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header><span>目录项（目录 #{{ selectedCatalogId || '-' }}）</span></template>
          <el-table :data="items" border stripe size="small" max-height="320">
            <el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="name" label="名称" min-width="170" />
            <el-table-column prop="ticket_type" label="工单类型" width="120" />
            <el-table-column prop="need_approval" label="需审批" width="90" />
            <el-table-column prop="sla_policy_id" label="SLA策略" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-card>
        <el-card shadow="never">
          <template #header><span>SLA 策略</span></template>
          <el-table :data="policies" border stripe size="small" max-height="320">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column prop="ticket_type" label="工单类型" width="120" />
            <el-table-column prop="response_minutes" label="响应(分钟)" width="110" />
            <el-table-column prop="resolve_minutes" label="解决(分钟)" width="110" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="catalogVisible" title="保存服务目录" width="480px">
      <el-alert v-if="catalogError" type="error" :closable="false" show-icon :title="catalogError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="目录ID"><el-input v-model="catalogForm.id" placeholder="留空为新建" /></el-form-item>
        <el-form-item label="上级ID"><el-input v-model="catalogForm.parent_id" placeholder="选填" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="catalogForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catalogForm.sort_no" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="catalogForm.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catalogVisible = false">取消</el-button>
        <el-button type="primary" :loading="catalogSaving" @click="submitCatalog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="policyVisible" title="SLA 策略" width="520px">
      <el-alert v-if="policyError" type="error" :closable="false" show-icon :title="policyError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="策略名称" required><el-input v-model="policyForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="policyForm.priority" style="width: 100%">
            <el-option v-for="p in ['P1', 'P2', 'P3', 'P4']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单类型">
          <el-select v-model="policyForm.ticket_type" style="width: 100%">
            <el-option label="事件" value="INCIDENT" />
            <el-option label="请求" value="REQUEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="响应(分钟)"><el-input-number v-model="policyForm.response_minutes" :min="1" /></el-form-item>
        <el-form-item label="解决(分钟)"><el-input-number v-model="policyForm.resolve_minutes" :min="1" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="policyForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="submitPolicy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
