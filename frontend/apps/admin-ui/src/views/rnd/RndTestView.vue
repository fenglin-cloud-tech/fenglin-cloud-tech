<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  rndApi,
  type RndExecution,
  type RndProduct,
  type RndTestCase,
  type RndTestPlan,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const cases = ref<RndTestCase[]>([])
const selectedProductId = ref<number | ''>('')
const reviewFilter = ref('')
const loading = ref(false)
const error = ref('')
const activeTab = ref('cases')

const caseVisible = ref(false)
const caseSaving = ref(false)
const caseError = ref('')
const caseForm = reactive({ title: '', module: '', level: 'P2', case_type: 'FUNCTIONAL', preconditions: '', steps_json: '' })

const planId = ref('')
const plan = ref<RndTestPlan | null>(null)
const planVisible = ref(false)
const planSaving = ref(false)
const planError = ref('')
const planForm = reactive({ name: '', start_date: '', end_date: '' })

const execution = ref<RndExecution | null>(null)
const itemForm = reactive({ case_id: '', result: 'PASS', remark: '' })

const gapItems = [
  '后端没有测试计划列表接口（仅有按 ID 查询与创建），需要手工输入计划 ID。',
  '测试用例列表必须携带产品 ID，没有跨产品用例库查询。',
  '用例评审状态通过 query 参数 reviewStatus 提交，评审通过后仍可重新评审，后端未限制次数。',
]

const reviewLabels: Record<string, string> = {
  DRAFT: '待评审',
  PENDING: '评审中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

function reviewLabel(value?: string) {
  return value ? reviewLabels[value] ?? value : '-'
}

function reviewType(value?: string) {
  if (value === 'APPROVED') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'PENDING') return 'warning'
  return 'info'
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadCases()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadCases() {
  if (!selectedProductId.value) {
    cases.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    cases.value = await rndApi.test.cases.list({
      productId: Number(selectedProductId.value),
      reviewStatus: reviewFilter.value || undefined,
      page: 1,
      size: 200,
    })
  } catch (err) {
    cases.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submitCase() {
  if (!selectedProductId.value || !caseForm.title.trim()) {
    caseError.value = '请选择产品并填写用例标题'
    return
  }
  caseSaving.value = true
  caseError.value = ''
  try {
    await rndApi.test.cases.create({
      product_id: Number(selectedProductId.value),
      title: caseForm.title.trim(),
      module: caseForm.module.trim() || undefined,
      level: caseForm.level,
      case_type: caseForm.case_type,
      preconditions: caseForm.preconditions.trim() || undefined,
      steps_json: caseForm.steps_json.trim() || undefined,
    })
    ElMessage.success('测试用例已创建')
    caseVisible.value = false
    Object.assign(caseForm, { title: '', module: '', preconditions: '', steps_json: '' })
    await loadCases()
  } catch (err) {
    caseError.value = describeError(err)
  } finally {
    caseSaving.value = false
  }
}

async function reviewCase(row: RndTestCase, reviewStatus: string) {
  try {
    await rndApi.test.cases.review(row.id, reviewStatus)
    ElMessage.success('评审结果已提交')
    await loadCases()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function loadPlan() {
  if (!planId.value) {
    ElMessage.warning('请输入测试计划 ID')
    return
  }
  try {
    plan.value = await rndApi.test.plans.get(Number(planId.value))
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitPlan() {
  if (!selectedProductId.value || !planForm.name.trim()) {
    planError.value = '请选择产品并填写计划名称'
    return
  }
  planSaving.value = true
  planError.value = ''
  try {
    const created = await rndApi.test.plans.create({
      product_id: Number(selectedProductId.value),
      name: planForm.name.trim(),
      start_date: planForm.start_date || undefined,
      end_date: planForm.end_date || undefined,
    })
    plan.value = created
    planId.value = String(created.id)
    planVisible.value = false
    ElMessage.success(`测试计划已创建（ID ${created.id}）`)
  } catch (err) {
    planError.value = describeError(err)
  } finally {
    planSaving.value = false
  }
}

async function createExecution() {
  if (!plan.value) {
    ElMessage.warning('请先加载测试计划')
    return
  }
  try {
    execution.value = await rndApi.test.plans.createExecution(plan.value.id)
    ElMessage.success(`测试执行已创建（ID ${execution.value.id}）`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function recordItem() {
  if (!execution.value || !itemForm.case_id) {
    ElMessage.warning('请填写执行 ID 与用例 ID')
    return
  }
  try {
    execution.value = await rndApi.test.executions.recordItem(execution.value.id, {
      case_id: Number(itemForm.case_id),
      result: itemForm.result,
      remark: itemForm.remark.trim() || undefined,
    })
    ElMessage.success('用例结果已记录')
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
      title="测试管理"
      description="测试用例、测试计划与执行结果；数据来自 /api/rnd/v1/test-cases 与 /test-plans。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadCases">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-button v-perm="'biz:devops:testcase:manage'" type="primary" @click="caseVisible = true">新增用例</el-button>
        <el-button v-perm="'biz:devops:testplan:manage'" plain @click="planVisible = true">新建计划</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="测试用例" name="cases">
          <div class="fl-toolbar">
            <el-select v-model="reviewFilter" placeholder="全部评审状态" clearable style="width: 160px" @change="loadCases">
              <el-option v-for="(label, code) in reviewLabels" :key="code" :label="label" :value="code" />
            </el-select>
            <span class="fl-muted">共 {{ cases.length }} 条用例</span>
          </div>
          <el-table :data="cases" v-loading="loading" border stripe max-height="520">
            <el-table-column prop="case_no" label="用例编号" width="140" />
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column prop="module" label="模块" width="130" />
            <el-table-column prop="level" label="级别" width="80" />
            <el-table-column prop="case_type" label="类型" width="120" />
            <el-table-column label="评审" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="reviewType(row.review_status)">{{ reviewLabel(row.review_status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button v-perm="'biz:devops:testcase:review'" link type="primary" @click="reviewCase(row, 'APPROVED')">通过</el-button>
                <el-button v-perm="'biz:devops:testcase:review'" link type="danger" @click="reviewCase(row, 'REJECTED')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="测试计划与执行" name="plans">
          <div class="fl-toolbar">
            <el-input v-model="planId" placeholder="测试计划 ID" style="width: 180px" />
            <el-button @click="loadPlan">加载计划</el-button>
            <el-button v-perm="'biz:devops:testplan:execute'" type="primary" plain @click="createExecution">创建执行</el-button>
          </div>
          <el-descriptions v-if="plan" :column="3" border style="margin-bottom: 12px">
            <el-descriptions-item label="计划编号">{{ plan.plan_no }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ plan.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ plan.status }}</el-descriptions-item>
            <el-descriptions-item label="用例数">{{ plan.case_count ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="通过">{{ plan.pass_count ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="失败">{{ plan.fail_count ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="输入计划 ID 查看详情" :image-size="70" />

          <template v-if="execution">
            <el-divider content-position="left">执行 #{{ execution.id }}（{{ execution.result ?? '进行中' }}）</el-divider>
            <el-form inline>
              <el-form-item label="用例ID"><el-input v-model="itemForm.case_id" style="width: 140px" /></el-form-item>
              <el-form-item label="结果">
                <el-select v-model="itemForm.result" style="width: 120px">
                  <el-option label="通过" value="PASS" />
                  <el-option label="失败" value="FAIL" />
                  <el-option label="阻塞" value="BLOCKED" />
                  <el-option label="跳过" value="SKIP" />
                </el-select>
              </el-form-item>
              <el-form-item label="备注"><el-input v-model="itemForm.remark" style="width: 200px" /></el-form-item>
              <el-form-item>
                <el-button type="primary" @click="recordItem">记录结果</el-button>
              </el-form-item>
            </el-form>
            <el-tag type="success">通过 {{ execution.pass_count ?? 0 }}</el-tag>
            <el-tag type="danger" style="margin-left: 8px">失败 {{ execution.fail_count ?? 0 }}</el-tag>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="caseVisible" title="新增测试用例" width="560px">
      <el-alert v-if="caseError" type="error" :closable="false" show-icon :title="caseError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="用例标题" required><el-input v-model="caseForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="模块"><el-input v-model="caseForm.module" maxlength="64" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="caseForm.level" style="width: 100%">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="用例类型">
          <el-select v-model="caseForm.case_type" style="width: 100%">
            <el-option label="功能" value="FUNCTIONAL" />
            <el-option label="接口" value="API" />
            <el-option label="性能" value="PERFORMANCE" />
            <el-option label="安全" value="SECURITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="前置条件"><el-input v-model="caseForm.preconditions" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="步骤JSON"><el-input v-model="caseForm.steps_json" type="textarea" :rows="3" placeholder='[{"step":"..."}]' /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseVisible = false">取消</el-button>
        <el-button type="primary" :loading="caseSaving" @click="submitCase">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planVisible" title="新建测试计划" width="480px">
      <el-alert v-if="planError" type="error" :closable="false" show-icon :title="planError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="计划名称" required><el-input v-model="planForm.name" maxlength="120" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="planForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="planForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" :loading="planSaving" @click="submitPlan">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
