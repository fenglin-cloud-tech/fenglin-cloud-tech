<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const catalogs = ref<ServiceRecord[]>([])
const items = ref<ServiceRecord[]>([])
const activeCatalog = ref<string | number | undefined>()
const loading = ref(false)
const error = ref('')

const applyVisible = ref(false)
const applySaving = ref(false)
const applyItem = ref<ServiceRecord | null>(null)
const applyForm = reactive({ title: '', description: '', priority: 'P3', contact: '', form_data: '{}' })

const gapItems = [
  '目录树接口返回“目录 + 其下目录项”，不含多级子目录结构，本页按一级目录展示。',
  '申请接口会直接创建工单；是否走审批由目录项 need_approval 决定，前端无法预览审批链。',
  '动态表单 form_data 后端按 JSON 透传，本页用 JSON 文本录入，未内嵌低代码表单渲染器。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    catalogs.value = await serviceApi.catalog.tree()
    if (catalogs.value.length) {
      await selectCatalog(catalogs.value[0].id)
    } else {
      items.value = []
    }
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function selectCatalog(id: string | number) {
  activeCatalog.value = id
  try {
    items.value = await serviceApi.catalog.items(id)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openApply(row: ServiceRecord) {
  applyItem.value = row
  applyForm.title = String(row.name ?? '')
  applyForm.description = ''
  applyForm.priority = 'P3'
  applyForm.contact = ''
  applyForm.form_data = '{}'
  applyVisible.value = true
}

async function submitApply() {
  if (!applyItem.value) return
  let formData: ServiceRecord
  try {
    formData = JSON.parse(applyForm.form_data || '{}') as ServiceRecord
  } catch {
    ElMessage.error('form_data JSON 不合法')
    return
  }
  applySaving.value = true
  try {
    const result = await serviceApi.catalog.apply(applyItem.value.id, {
      title: applyForm.title.trim() || undefined,
      description: applyForm.description || undefined,
      priority: applyForm.priority,
      contact: applyForm.contact || undefined,
      requester_id: auth.userId ?? null,
      form_data: formData,
    })
    const ticketNo = (result.ticket as ServiceRecord | undefined)?.ticket_no
    ElMessage.success(`申请已提交${ticketNo ? `，工单号 ${ticketNo}` : ''}`)
    applyVisible.value = false
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    applySaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服服务台"
      title="服务申请"
      description="按服务目录申请 IT 服务；数据来自 /api/service/v1/catalog。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :span="6">
        <el-card shadow="never">
          <template #header><span>服务目录</span></template>
          <el-menu :default-active="String(activeCatalog ?? '')" @select="(key: string) => selectCatalog(key)">
            <el-menu-item v-for="item in catalogs" :key="item.id" :index="String(item.id)">
              <span>{{ item.name }}</span>
            </el-menu-item>
          </el-menu>
          <el-empty v-if="catalogs.length === 0" description="暂无目录" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card shadow="never">
          <template #header><span>目录项</span></template>
          <el-table :data="items" v-loading="loading" border stripe size="small">
            <el-table-column prop="code" label="编码" width="140" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="description" label="说明" min-width="220" />
            <el-table-column prop="ticket_type" label="工单类型" width="120" />
            <el-table-column prop="need_approval" label="需审批" width="90">
              <template #default="{ row }">{{ row.need_approval ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button v-perm="'service:catalog:apply'" link type="primary" @click="openApply(row)">申请</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="items.length === 0" description="该目录下暂无可用服务" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="applyVisible" :title="`申请：${applyItem?.name ?? ''}`" width="560px">
      <el-form label-width="90px">
        <el-form-item label="标题"><el-input v-model="applyForm.title" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="applyForm.priority" style="width: 100%">
            <el-option v-for="p in ['P1', 'P2', 'P3', 'P4']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系方式"><el-input v-model="applyForm.contact" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="applyForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="表单数据">
          <el-input v-model="applyForm.form_data" type="textarea" :rows="5" spellcheck="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySaving" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>
