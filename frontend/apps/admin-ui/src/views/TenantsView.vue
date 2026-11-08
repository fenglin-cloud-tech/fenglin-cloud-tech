<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { tenantApi, type CreateTenantRequest, type TenantView } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useTenantStore } from '@/stores/tenant'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const tenantStore = useTenantStore()

const rows = ref<TenantView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ status: '', keyword: '', page: 1, size: 10 })

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const created = ref<{ tenant_id: number; code: string; status: string; initial_password?: string } | null>(null)
const form = reactive<CreateTenantRequest>({
  name: '',
  code: '',
  contact_name: '',
  admin_username: 'admin',
  admin_mobile: '',
  admin_password: '',
  trial_days: 14,
  package_code: '',
})

const detailVisible = ref(false)
const detail = ref<TenantView | null>(null)
const detailLoading = ref(false)

const canManage = auth.can('platform:tenant:manage')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await tenantApi.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    code: '',
    contact_name: '',
    admin_username: 'admin',
    admin_mobile: '',
    admin_password: '',
    trial_days: 14,
    package_code: '',
  })
  created.value = null
  createError.value = ''
}

function openCreate() {
  resetForm()
  createVisible.value = true
}

async function submitCreate() {
  if (!form.name || !form.admin_username) {
    createError.value = '租户名称与管理员账号为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    created.value = await tenantApi.create({
      ...form,
      code: form.code || undefined,
      contact_name: form.contact_name || undefined,
      admin_mobile: form.admin_mobile || undefined,
      admin_password: form.admin_password || undefined,
      trial_days: form.trial_days || undefined,
      package_code: form.package_code || undefined,
    })
    ElMessage.success('租户开通成功')
    await load()
    await tenantStore.loadList()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function openDetail(row: TenantView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  try {
    detail.value = await tenantApi.get(row.id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function changeStatus(row: TenantView, action: 'SUSPEND' | 'RESUME' | 'CLOSE') {
  const actionText = { SUSPEND: '暂停', RESUME: '恢复', CLOSE: '关闭' }[action]
  try {
    const { value } = await ElMessageBox.prompt(`请输入${actionText}租户「${row.name}」的原因`, `${actionText}租户`, {
      inputPlaceholder: '原因将写入审计日志',
      confirmButtonText: actionText,
      cancelButtonText: '取消',
    })
    await tenantApi.updateStatus(row.id, { action, reason: value })
    ElMessage.success(`已${actionText}`)
    await load()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

onMounted(() => {
  void load()
  void tenantStore.loadList()
})
</script>

<template>
  <div>
    <PageHeader module="租户中台 · A-03~A-08" title="租户管理" description="租户开通、状态变更与详情；套餐、账单、计量页面在后端未提供接口。">
      <template #actions>
        <el-button v-perm="'platform:tenant:manage'" type="primary" @click="openCreate">开通租户</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        'HUB-API-201 开通租户：后端支持 code/adminPassword 缺省，初始管理员密码仅本次返回；契约未定义这两个字段。',
        '租户套餐（A-05）、账单（A-06）、计量（A-07）后端未提供查询接口，本页不展示假数据。',
        '租户切换：头部选择器仅切换管理视角；后端未提供令牌换发/租户模拟接口，业务接口仍按登录令牌租户隔离。',
        '当前账号写权限：' + (canManage ? 'platform:tenant:manage 已具备' : '缺少 platform:tenant:manage（开通/状态变更将返回 403）'),
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="名称/编码关键词" clearable style="width: 220px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="TRIAL" value="TRIAL" />
          <el-option label="SUSPENDED" value="SUSPENDED" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.keyword = ''; query.status = ''; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="租户编码" min-width="140" />
        <el-table-column prop="name" label="租户名称" min-width="160" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'SUSPENDED' ? 'warning' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contact_name" label="联系人" width="120" />
        <el-table-column label="到期时间" width="180">
          <template #default="{ row }">{{ row.expire_at ? new Date(row.expire_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 'SUSPENDED'" v-perm="'platform:tenant:manage'" link type="warning" @click="changeStatus(row, 'SUSPEND')">暂停</el-button>
            <el-button v-if="row.status === 'SUSPENDED'" v-perm="'platform:tenant:manage'" link type="success" @click="changeStatus(row, 'RESUME')">恢复</el-button>
            <el-button v-perm="'platform:tenant:manage'" link type="danger" @click="changeStatus(row, 'CLOSE')">关闭</el-button>
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

    <el-dialog v-model="createVisible" title="开通租户" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-alert
        v-if="created"
        type="success"
        :closable="false"
        show-icon
        title="租户已创建"
        style="margin-bottom: 12px"
      >
        <div>租户 ID：{{ created.tenant_id }}，编码：{{ created.code }}，状态：{{ created.status }}</div>
        <div v-if="created.initial_password">初始管理员密码（仅本次显示）：<strong>{{ created.initial_password }}</strong></div>
      </el-alert>
      <el-form label-width="110px" :disabled="!!created">
        <el-form-item label="租户名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="租户编码">
          <el-input v-model="form.code" placeholder="4~32 位小写字母/数字/中划线，可缺省自动生成" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contact_name" />
        </el-form-item>
        <el-form-item label="管理员账号" required>
          <el-input v-model="form.admin_username" />
        </el-form-item>
        <el-form-item label="管理员手机">
          <el-input v-model="form.admin_mobile" />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input v-model="form.admin_password" type="password" show-password placeholder="可缺省，由平台生成" />
        </el-form-item>
        <el-form-item label="试用天数">
          <el-input-number v-model="form.trial_days" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="套餐编码">
          <el-input v-model="form.package_code" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ created ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!created" type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="租户详情" size="480px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="租户 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ detail.code }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contact_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{ detail.expire_at ? new Date(detail.expire_at).toLocaleString() : '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.created_at ? new Date(detail.created_at).toLocaleString() : '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="套餐、账单、计量"
          description="后端未提供对应查询接口，详情页仅展示租户主数据。"
          style="margin-top: 16px"
        />
      </div>
    </el-drawer>
  </div>
</template>
