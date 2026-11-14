<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
import {
  rndApi,
  type RndBuild,
  type RndDeployment,
  type RndId,
  type RndProduct,
  type RndRelease,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const releases = ref<RndRelease[]>([])
const deployments = ref<RndDeployment[]>([])
const selectedProductId = ref<RndId | ''>('')
const envFilter = ref('')
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  release_version: '',
  env: 'TEST',
  build_id: '' as RndId | '',
  window_start: '',
  window_end: '',
})

const builds = ref<RndBuild[]>([])
const buildsLoading = ref(false)

const gapItems = [
  '构建下拉来自 /builds（按产品过滤），只做选择不做构建触发；构建产物由流水线运行回调落库。',
  '发布审批会调用工作流 rndReleaseApproval，工作流服务不可用时提交/审批返回真实错误。',
  '部署与回滚接口只记录部署结果，不校验目标环境实际健康状态。',
]

async function loadBuilds() {
  if (!selectedProductId.value) {
    builds.value = []
    return
  }
  buildsLoading.value = true
  try {
    builds.value = await rndApi.builds.list({ product_id: selectedProductId.value, limit: 100 })
  } catch (err) {
    builds.value = []
    createError.value = describeError(err)
  } finally {
    buildsLoading.value = false
  }
}

function openCreate() {
  createVisible.value = true
  form.build_id = ''
  loadBuilds()
}

const statusLabels: Record<string, string> = {
  draft: '草稿',
  pending_approval: '待审批',
  approved: '已审批',
  rejected: '已驳回',
  deploying: '部署中',
  deployed: '已部署',
  rolled_back: '已回滚',
}

const envLabels: Record<string, string> = {
  DEV: '开发',
  TEST: '测试',
  STAGING: '预发',
  PROD: '生产',
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'deployed' || value === 'approved') return 'success'
  if (value === 'rejected') return 'danger'
  if (value === 'pending_approval' || value === 'deploying') return 'warning'
  return 'info'
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadReleases()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadReleases() {
  if (!selectedProductId.value) {
    releases.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    releases.value = await rndApi.releases.list({
      productId: selectedProductId.value,
      env: envFilter.value || undefined,
      page: 1,
      size: 100,
    })
  } catch (err) {
    releases.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadDeployments(row: RndRelease) {
  try {
    deployments.value = await rndApi.releases.deployments(row.id)
    ElMessage.success(`已加载 ${deployments.value.length} 条部署记录`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitCreate() {
  if (!selectedProductId.value || !form.release_version.trim()) {
    createError.value = '请选择产品并填写版本号'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await rndApi.releases.create({
      product_id: selectedProductId.value,
      release_version: form.release_version.trim(),
      env: form.env,
      build_id: form.build_id ? form.build_id : undefined,
      window_start: form.window_start || undefined,
      window_end: form.window_end || undefined,
    })
    ElMessage.success('发布单已创建')
    createVisible.value = false
    form.release_version = ''
    await loadReleases()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function releaseAction(row: RndRelease, action: 'submit' | 'approve' | 'reject' | 'deploy' | 'rollback') {
  try {
    if (action === 'submit') await rndApi.releases.submit(row.id)
    else if (action === 'approve') await rndApi.releases.approve(row.id)
    else if (action === 'reject') await rndApi.releases.reject(row.id)
    else if (action === 'deploy') await rndApi.releases.deploy(row.id, 'ROLLING')
    else await rndApi.releases.rollback(row.id)
    ElMessage.success('操作成功')
    await loadReleases()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="发布管理"
      description="发布单、审批、部署与回滚；数据来自 /api/rnd/v1/releases。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadReleases">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-select v-model="envFilter" placeholder="全部环境" clearable style="width: 130px" @change="loadReleases">
          <el-option v-for="(label, code) in envLabels" :key="code" :label="label" :value="code" />
        </el-select>
        <el-button v-perm="'biz:devops:release:create'" type="primary" @click="openCreate">新建发布单</el-button>
        <el-button :loading="loading" @click="loadReleases">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <el-table :data="releases" v-loading="loading" border stripe max-height="420">
        <el-table-column prop="release_no" label="发布单号" width="150" />
        <el-table-column prop="release_version" label="版本" width="130" />
        <el-table-column label="环境" width="100">
          <template #default="{ row }">{{ envLabels[row.env] ?? row.env }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="window_start" label="窗口开始" width="170" />
        <el-table-column prop="window_end" label="窗口结束" width="170" />
        <el-table-column prop="deployed_at" label="部署时间" width="170" />
        <el-table-column label="操作" width="290">
          <template #default="{ row }">
            <el-button v-if="row.status === 'draft'" v-perm="'biz:devops:release:create'" link type="primary" @click="releaseAction(row, 'submit')">提交审批</el-button>
            <el-button v-if="row.status === 'pending_approval'" v-perm="'biz:devops:release:approve'" link type="success" @click="releaseAction(row, 'approve')">通过</el-button>
            <el-button v-if="row.status === 'pending_approval'" v-perm="'biz:devops:release:approve'" link type="danger" @click="releaseAction(row, 'reject')">驳回</el-button>
            <el-button v-if="row.status === 'approved'" v-perm="'biz:devops:release:deploy'" link type="primary" @click="releaseAction(row, 'deploy')">部署</el-button>
            <el-button v-if="row.status === 'deployed'" v-perm="'biz:devops:release:rollback'" link type="warning" @click="releaseAction(row, 'rollback')">回滚</el-button>
            <el-button link @click="loadDeployments(row)">部署记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span>部署记录</span></template>
      <el-table :data="deployments" border stripe size="small" max-height="300">
        <el-table-column prop="id" label="部署ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="env" label="环境" width="100" />
        <el-table-column prop="result" label="结果" width="120" />
        <el-table-column prop="strategy" label="策略" width="120" />
        <el-table-column prop="operator_id" label="操作人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column label="开始" width="170">
          <template #default="{ row }">{{ formatDateTime(row.started_at) }}</template>
        </el-table-column>
        <el-table-column label="结束" width="170">
          <template #default="{ row }">{{ formatDateTime(row.finished_at) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建发布单" width="540px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="版本号" required><el-input v-model="form.release_version" placeholder="如 1.4.0" /></el-form-item>
        <el-form-item label="目标环境">
          <el-select v-model="form.env" style="width: 100%">
            <el-option v-for="(label, code) in envLabels" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="构建">
          <el-select v-model="form.build_id" :loading="buildsLoading" clearable placeholder="选填，选择本产品构建" style="width: 100%">
            <el-option
              v-for="build in builds"
              :key="String(build.id)"
              :label="`${build.build_no}（${build.status}${build.artifact_version ? ' · ' + build.artifact_version : ''}）`"
              :value="build.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="窗口开始">
          <el-date-picker v-model="form.window_start" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="窗口结束">
          <el-date-picker v-model="form.window_end" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
