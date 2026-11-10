<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  rndApi,
  type RndCommit,
  type RndPipeline,
  type RndProduct,
  type RndRepo,
  type RndRun,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const repos = ref<RndRepo[]>([])
const pipelines = ref<RndPipeline[]>([])
const commits = ref<RndCommit[]>([])
const runs = ref<RndRun[]>([])
const selectedProductId = ref<number | ''>('')
const selectedRepoId = ref<number | ''>('')
const selectedPipelineId = ref<number | ''>('')
const activeTab = ref('repos')
const loading = ref(false)
const error = ref('')

const repoVisible = ref(false)
const repoSaving = ref(false)
const repoError = ref('')
const repoForm = reactive({ repo_name: '', vcs_type: 'GIT', repo_url: '', default_branch: 'main' })

const pipelineVisible = ref(false)
const pipelineSaving = ref(false)
const pipelineError = ref('')
const pipelineForm = reactive({ name: '', ci_type: 'JENKINS', trigger_mode: 'MANUAL' })

const gapItems = [
  '后端没有跨产品的代码库/流水线聚合接口，需先选择产品。',
  '提交入库依赖外部 Webhook 或 /commits 主动上报，管理端只读展示，不提供手工补录。',
  '流水线运行回调 /runs/{id}/finish 由 CI 侧调用，管理端仅提供触发与查看。',
]

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadRepos()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadRepos() {
  if (!selectedProductId.value) {
    repos.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    repos.value = await rndApi.code.repos.list(Number(selectedProductId.value))
    if (!selectedRepoId.value && repos.value.length) {
      selectedRepoId.value = repos.value[0].id
      await loadCommits()
    }
  } catch (err) {
    repos.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadPipelines() {
  if (!selectedProductId.value) {
    pipelines.value = []
    return
  }
  try {
    pipelines.value = await rndApi.pipelines.list(Number(selectedProductId.value))
    if (!selectedPipelineId.value && pipelines.value.length) {
      selectedPipelineId.value = pipelines.value[0].id
      await loadRuns()
    }
  } catch (err) {
    pipelines.value = []
    error.value = describeError(err)
  }
}

async function loadCommits() {
  if (!selectedRepoId.value) {
    commits.value = []
    return
  }
  loading.value = true
  try {
    commits.value = await rndApi.code.commits.list({ repoId: Number(selectedRepoId.value), page: 1, size: 100 })
  } catch (err) {
    commits.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadRuns() {
  if (!selectedPipelineId.value) {
    runs.value = []
    return
  }
  try {
    runs.value = await rndApi.pipelines.listRuns(Number(selectedPipelineId.value), 30)
  } catch (err) {
    runs.value = []
    error.value = describeError(err)
  }
}

async function submitRepo() {
  if (!selectedProductId.value || !repoForm.repo_name.trim()) {
    repoError.value = '请选择产品并填写代码库名称'
    return
  }
  repoSaving.value = true
  repoError.value = ''
  try {
    await rndApi.code.repos.create({
      product_id: Number(selectedProductId.value),
      repo_name: repoForm.repo_name.trim(),
      vcs_type: repoForm.vcs_type,
      repo_url: repoForm.repo_url.trim() || undefined,
      default_branch: repoForm.default_branch.trim() || undefined,
    })
    ElMessage.success('代码库已创建')
    repoVisible.value = false
    repoForm.repo_name = ''
    repoForm.repo_url = ''
    await loadRepos()
  } catch (err) {
    repoError.value = describeError(err)
  } finally {
    repoSaving.value = false
  }
}

async function syncRepo(row: RndRepo) {
  try {
    const result = await rndApi.code.repos.sync(row.id)
    ElMessage.success(`同步完成：${result.synced_commits} 条提交`)
    await loadRepos()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeRepo(row: RndRepo) {
  try {
    await ElMessageBox.confirm(`确认删除代码库 ${row.repo_name}？`, '删除代码库', { type: 'warning' })
    await rndApi.code.repos.remove(row.id)
    ElMessage.success('代码库已删除')
    if (selectedRepoId.value === row.id) {
      selectedRepoId.value = ''
      commits.value = []
    }
    await loadRepos()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

async function submitPipeline() {
  if (!selectedProductId.value || !pipelineForm.name.trim()) {
    pipelineError.value = '请选择产品并填写流水线名称'
    return
  }
  pipelineSaving.value = true
  pipelineError.value = ''
  try {
    await rndApi.pipelines.create({
      product_id: Number(selectedProductId.value),
      name: pipelineForm.name.trim(),
      ci_type: pipelineForm.ci_type,
      trigger_mode: pipelineForm.trigger_mode,
    })
    ElMessage.success('流水线已创建')
    pipelineVisible.value = false
    pipelineForm.name = ''
    await loadPipelines()
  } catch (err) {
    pipelineError.value = describeError(err)
  } finally {
    pipelineSaving.value = false
  }
}

async function triggerPipeline(row: RndPipeline) {
  try {
    const run = await rndApi.pipelines.trigger(row.id, 'MANUAL')
    ElMessage.success(`已触发运行 ${run.run_no ?? run.id}`)
    selectedPipelineId.value = row.id
    await loadRuns()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(async () => {
  await loadProducts()
  await loadPipelines()
})
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="代码与流水线"
      description="代码库、提交记录、流水线运行；数据来自 /api/rnd/v1/repos、/commits、/pipelines。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadRepos(); loadPipelines()">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-button v-perm="'biz:devops:repo:manage'" type="primary" @click="repoVisible = true">新增代码库</el-button>
        <el-button v-perm="'biz:devops:pipeline:manage'" plain @click="pipelineVisible = true">新建流水线</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="代码库" name="repos">
          <el-table :data="repos" v-loading="loading" border stripe max-height="300">
            <el-table-column prop="repo_name" label="名称" min-width="160" />
            <el-table-column prop="vcs_type" label="类型" width="90" />
            <el-table-column prop="repo_url" label="地址" min-width="220" />
            <el-table-column prop="default_branch" label="默认分支" width="120" />
            <el-table-column prop="sync_status" label="同步状态" width="120" />
            <el-table-column prop="last_sync_at" label="最近同步" width="170" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button link type="primary" @click="selectedRepoId = row.id; loadCommits()">提交</el-button>
                <el-button v-perm="'biz:devops:repo:sync'" link type="primary" @click="syncRepo(row)">同步</el-button>
                <el-button v-perm="'biz:devops:repo:manage'" link type="danger" @click="removeRepo(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="提交记录" name="commits">
          <div class="fl-toolbar">
            <el-select v-model="selectedRepoId" placeholder="选择代码库" style="width: 220px" @change="loadCommits">
              <el-option v-for="repo in repos" :key="repo.id" :label="repo.repo_name" :value="repo.id" />
            </el-select>
            <el-button :loading="loading" @click="loadCommits">刷新</el-button>
          </div>
          <el-table :data="commits" border stripe size="small" max-height="360">
            <el-table-column prop="commit_sha" label="Commit" width="150" />
            <el-table-column prop="branch" label="分支" width="110" />
            <el-table-column prop="author_name" label="作者" width="120" />
            <el-table-column prop="message" label="信息" min-width="240" />
            <el-table-column prop="add_lines" label="新增" width="80" />
            <el-table-column prop="del_lines" label="删除" width="80" />
            <el-table-column prop="committed_at" label="提交时间" width="170" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="流水线" name="pipelines">
          <el-table :data="pipelines" border stripe max-height="240">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="ci_type" label="CI 类型" width="120" />
            <el-table-column prop="trigger_mode" label="触发方式" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button v-perm="'biz:devops:pipeline:trigger'" link type="primary" @click="triggerPipeline(row)">触发</el-button>
                <el-button link @click="selectedPipelineId = row.id; loadRuns()">运行记录</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-divider content-position="left">运行记录</el-divider>
          <el-table :data="runs" border stripe size="small" max-height="300">
            <el-table-column prop="run_no" label="运行号" width="140" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="trigger_type" label="触发" width="100" />
            <el-table-column prop="started_at" label="开始" width="170" />
            <el-table-column prop="duration_ms" label="耗时(ms)" width="110" />
            <el-table-column prop="artifact_name" label="制品" min-width="150" />
            <el-table-column prop="quality_gate" label="质量门禁" width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="repoVisible" title="新增代码库" width="520px">
      <el-alert v-if="repoError" type="error" :closable="false" show-icon :title="repoError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="代码库名称" required><el-input v-model="repoForm.repo_name" maxlength="64" /></el-form-item>
        <el-form-item label="版本控制">
          <el-select v-model="repoForm.vcs_type" style="width: 100%">
            <el-option label="Git" value="GIT" />
            <el-option label="SVN" value="SVN" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库地址"><el-input v-model="repoForm.repo_url" /></el-form-item>
        <el-form-item label="默认分支"><el-input v-model="repoForm.default_branch" maxlength="64" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repoVisible = false">取消</el-button>
        <el-button type="primary" :loading="repoSaving" @click="submitRepo">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pipelineVisible" title="新建流水线" width="480px">
      <el-alert v-if="pipelineError" type="error" :closable="false" show-icon :title="pipelineError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="流水线名称" required><el-input v-model="pipelineForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="CI 类型">
          <el-select v-model="pipelineForm.ci_type" style="width: 100%">
            <el-option label="Jenkins" value="JENKINS" />
            <el-option label="GitLab CI" value="GITLAB" />
            <el-option label="GitHub Actions" value="GITHUB" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="pipelineForm.trigger_mode" style="width: 100%">
            <el-option label="手动" value="MANUAL" />
            <el-option label="推送自动" value="PUSH" />
            <el-option label="定时" value="SCHEDULE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pipelineVisible = false">取消</el-button>
        <el-button type="primary" :loading="pipelineSaving" @click="submitPipeline">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
