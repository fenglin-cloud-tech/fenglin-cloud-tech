<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
import { rndApi, type RndId, type RndMember, type RndProduct, type RndStatus } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const members = ref<RndMember[]>([])
const statuses = ref<RndStatus[]>([])
const selectedProductId = ref<RndId | ''>('')
const loading = ref(false)
const error = ref('')

const productVisible = ref(false)
const productSaving = ref(false)
const productError = ref('')
const productForm = reactive({
  product_code: '',
  name: '',
  product_line: '',
  owner_id: '',
  visibility: 'TENANT',
  sprint_cycle_days: 14,
  description: '',
})

const memberVisible = ref(false)
const memberSaving = ref(false)
const memberError = ref('')
const memberForm = reactive({ user_id: '', role_code: 'DEVELOPER', data_scope: 'ALL' })

const gapItems = [
  '后端没有产品删除/归档接口，产品只能创建与更新，无法在管理端移除。',
  '后端没有成员用户检索接口，新增成员需要手工填写用户 ID（来自 IAM 用户列表）。',
  '产品状态流转接口未开放，statuses 仅用于展示产品内需求状态配置。',
]

async function loadProducts() {
  loading.value = true
  error.value = ''
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) {
      selectedProductId.value = products.value[0].id
    }
    await loadProductDetail()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadProductDetail() {
  if (!selectedProductId.value) {
    members.value = []
    statuses.value = []
    return
  }
  const id = selectedProductId.value
  try {
    const [memberList, statusList] = await Promise.all([
      rndApi.products.members(id),
      rndApi.products.statuses(id),
    ])
    members.value = memberList
    statuses.value = statusList
  } catch (err) {
    members.value = []
    statuses.value = []
    error.value = describeError(err)
  }
}

async function submitProduct() {
  if (!productForm.product_code.trim() || !productForm.name.trim()) {
    productError.value = '产品编码与名称不能为空'
    return
  }
  productSaving.value = true
  productError.value = ''
  try {
    const created = await rndApi.products.create({
      product_code: productForm.product_code.trim(),
      name: productForm.name.trim(),
      product_line: productForm.product_line.trim() || undefined,
      owner_id: productForm.owner_id ? productForm.owner_id : undefined,
      visibility: productForm.visibility,
      sprint_cycle_days: Number(productForm.sprint_cycle_days),
      description: productForm.description.trim() || undefined,
    })
    ElMessage.success(`产品 ${created.product_code} 已创建`)
    productVisible.value = false
    Object.assign(productForm, { product_code: '', name: '', product_line: '', owner_id: '', description: '' })
    await loadProducts()
  } catch (err) {
    productError.value = describeError(err)
  } finally {
    productSaving.value = false
  }
}

async function submitMember() {
  if (!selectedProductId.value || !memberForm.user_id) {
    memberError.value = '请填写用户 ID'
    return
  }
  memberSaving.value = true
  memberError.value = ''
  try {
    await rndApi.products.addMember(selectedProductId.value, {
      user_id: memberForm.user_id,
      role_code: memberForm.role_code,
      data_scope: memberForm.data_scope,
    })
    ElMessage.success('成员已添加')
    memberVisible.value = false
    memberForm.user_id = ''
    await loadProductDetail()
  } catch (err) {
    memberError.value = describeError(err)
  } finally {
    memberSaving.value = false
  }
}

async function removeMember(member: RndMember) {
  if (!selectedProductId.value) return
  try {
    await ElMessageBox.confirm(`确认移除成员 #${member.user_id}？`, '移除成员', { type: 'warning' })
    await rndApi.products.deleteMember(selectedProductId.value, member.id)
    ElMessage.success('成员已移除')
    await loadProductDetail()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

function productLabel(product: RndProduct) {
  return `${product.product_code} ${product.name}`
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="产品与成员"
      description="产品档案、成员角色与需求状态配置；数据来自 /api/rnd/v1/products。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 240px" @change="loadProductDetail">
          <el-option v-for="product in products" :key="product.id" :label="productLabel(product)" :value="product.id" />
        </el-select>
        <el-button v-perm="'tenant:devops:product:manage'" type="primary" @click="productVisible = true">新建产品</el-button>
        <el-button :loading="loading" @click="loadProducts">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <el-table :data="products" v-loading="loading" border stripe max-height="360">
        <el-table-column prop="product_code" label="产品编码" width="150" />
        <el-table-column prop="name" label="产品名称" min-width="180" />
        <el-table-column prop="product_line" label="产品线" width="140" />
        <el-table-column prop="visibility" label="可见范围" width="110" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="sprint_cycle_days" label="迭代周期(天)" width="120" />
        <el-table-column prop="description" label="描述" min-width="200" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <el-tabs>
        <el-tab-pane label="成员">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:devops:member:manage'" type="primary" plain @click="memberVisible = true">添加成员</el-button>
            <span class="fl-muted">共 {{ members.length }} 名成员</span>
          </div>
          <el-table :data="members" border stripe max-height="420">
            <el-table-column prop="id" label="成员ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
            <el-table-column prop="user_id" label="用户ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
            <el-table-column prop="role_code" label="角色" width="140" />
            <el-table-column prop="data_scope" label="数据范围" width="130" />
            <el-table-column label="加入时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-perm="'tenant:devops:member:manage'" link type="danger" @click="removeMember(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="需求状态">
          <el-table :data="statuses" border stripe max-height="420">
            <el-table-column prop="status_code" label="状态码" width="140" />
            <el-table-column prop="status_name" label="状态名称" min-width="150" />
            <el-table-column prop="category" label="分类" width="130" />
            <el-table-column prop="color" label="颜色" width="110" />
            <el-table-column prop="sort_no" label="排序" width="90" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="productVisible" title="新建产品" width="560px">
      <el-alert v-if="productError" type="error" :closable="false" show-icon :title="productError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="产品编码" required><el-input v-model="productForm.product_code" maxlength="32" /></el-form-item>
        <el-form-item label="产品名称" required><el-input v-model="productForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="产品线"><el-input v-model="productForm.product_line" maxlength="64" /></el-form-item>
        <el-form-item label="负责人用户ID"><el-input v-model="productForm.owner_id" placeholder="选填" /></el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="productForm.visibility" style="width: 100%">
            <el-option label="租户内" value="TENANT" />
            <el-option label="产品成员" value="MEMBER" />
          </el-select>
        </el-form-item>
        <el-form-item label="迭代周期(天)">
          <el-input-number v-model="productForm.sprint_cycle_days" :min="1" :max="60" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="productForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productVisible = false">取消</el-button>
        <el-button type="primary" :loading="productSaving" @click="submitProduct">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" title="添加成员" width="480px">
      <el-alert v-if="memberError" type="error" :closable="false" show-icon :title="memberError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="用户ID" required><el-input v-model="memberForm.user_id" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role_code" style="width: 100%">
            <el-option label="产品负责人" value="OWNER" />
            <el-option label="研发" value="DEVELOPER" />
            <el-option label="测试" value="QA" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="memberForm.data_scope" style="width: 100%">
            <el-option label="全部" value="ALL" />
            <el-option label="本产品" value="PRODUCT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" :loading="memberSaving" @click="submitMember">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>
