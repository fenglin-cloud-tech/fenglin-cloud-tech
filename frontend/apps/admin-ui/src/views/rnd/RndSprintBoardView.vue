<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndBoard, type RndBurndown, type RndProduct, type RndSprint } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const sprints = ref<RndSprint[]>([])
const selectedProductId = ref<number | ''>('')
const burndown = ref<RndBurndown | null>(null)
const board = ref<RndBoard | null>(null)
const boardId = ref('')
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({ name: '', goal: '', start_date: '', end_date: '', capacity_hours: '' })

const boardVisible = ref(false)
const boardSaving = ref(false)
const boardError = ref('')
const boardForm = reactive({ name: '', board_type: 'SCRUM', columns: '待处理:todo|进行中:doing|已完成:done' })

const gapItems = [
  '后端没有看板列表接口（仅按 ID 查询与创建），因此管理端需要手工输入看板 ID 才能查看。',
  '燃尽图接口 /sprints/{id}/burndown 依赖任务工时数据，缺少工时记录时曲线为全量理想线。',
  '迭代成员容量在创建时提交，后端未提供单独的成员容量维护接口。',
]

const activeSprints = computed(() => sprints.value.filter((item) => item.status !== 'COMPLETED'))

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadSprints()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadSprints() {
  if (!selectedProductId.value) {
    sprints.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    sprints.value = await rndApi.sprints.list({ productId: Number(selectedProductId.value) })
  } catch (err) {
    sprints.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!selectedProductId.value || !form.name.trim()) {
    createError.value = '请选择产品并填写迭代名称'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await rndApi.sprints.create({
      product_id: Number(selectedProductId.value),
      name: form.name.trim(),
      goal: form.goal.trim() || undefined,
      start_date: form.start_date || undefined,
      end_date: form.end_date || undefined,
      capacity_hours: form.capacity_hours || undefined,
    })
    ElMessage.success('迭代已创建')
    createVisible.value = false
    Object.assign(form, { name: '', goal: '', start_date: '', end_date: '', capacity_hours: '' })
    await loadSprints()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function sprintAction(row: RndSprint, action: 'start' | 'complete') {
  try {
    if (action === 'start') await rndApi.sprints.start(row.id)
    else await rndApi.sprints.complete(row.id)
    ElMessage.success(action === 'start' ? '迭代已开始' : '迭代已完成')
    await loadSprints()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function loadBurndown(id: number) {
  try {
    burndown.value = await rndApi.sprints.burndown(id)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function loadBoard() {
  if (!boardId.value) {
    ElMessage.warning('请输入看板 ID')
    return
  }
  try {
    board.value = await rndApi.boards.get(Number(boardId.value))
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitBoard() {
  if (!selectedProductId.value || !boardForm.name.trim()) {
    boardError.value = '请选择产品并填写看板名称'
    return
  }
  boardSaving.value = true
  boardError.value = ''
  try {
    const columns = boardForm.columns
      .split('|')
      .map((part) => part.split(':'))
      .filter((pair) => pair.length === 2)
      .map(([columnName, codes]) => ({
        column_name: columnName,
        status_codes: codes.split(',').map((code) => code.trim()).filter(Boolean),
      }))
    const created = await rndApi.boards.create({
      product_id: Number(selectedProductId.value),
      name: boardForm.name.trim(),
      board_type: boardForm.board_type,
      columns,
    })
    board.value = created
    boardId.value = String(created.id)
    ElMessage.success(`看板已创建（ID ${created.id}）`)
    boardVisible.value = false
  } catch (err) {
    boardError.value = describeError(err)
  } finally {
    boardSaving.value = false
  }
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="迭代与看板"
      description="迭代生命周期、燃尽图与看板列；数据来自 /api/rnd/v1/sprints 与 /boards。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadSprints">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-button v-perm="'biz:devops:sprint:manage'" type="primary" @click="createVisible = true">新建迭代</el-button>
        <el-button v-perm="'biz:devops:board:manage'" plain @click="boardVisible = true">创建看板</el-button>
        <el-button :loading="loading" @click="loadSprints">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <div class="fl-toolbar">
        <el-tag type="info" effect="plain">进行中迭代 {{ activeSprints.length }} / 共 {{ sprints.length }}</el-tag>
      </div>
      <el-table :data="sprints" v-loading="loading" border stripe max-height="380">
        <el-table-column prop="sprint_no" label="迭代编号" width="140" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="goal" label="目标" min-width="180" />
        <el-table-column prop="start_date" label="开始" width="120" />
        <el-table-column prop="end_date" label="结束" width="120" />
        <el-table-column prop="capacity_hours" label="容量工时" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="任务" width="110">
          <template #default="{ row }">{{ row.done_task_count ?? 0 }}/{{ row.task_count ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button v-perm="'biz:devops:sprint:manage'" v-if="row.status !== 'ACTIVE'" link type="primary" @click="sprintAction(row, 'start')">开始</el-button>
            <el-button v-perm="'biz:devops:sprint:manage'" v-if="row.status === 'ACTIVE'" link type="warning" @click="sprintAction(row, 'complete')">完成</el-button>
            <el-button v-perm="'biz:devops:sprint:view'" link @click="loadBurndown(row.id)">燃尽图</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="burndown" shadow="never" style="margin-bottom: 12px">
      <template #header>
        <span>燃尽图 · {{ burndown.name }}（任务 {{ burndown.done_tasks }}/{{ burndown.total_tasks }}）</span>
      </template>
      <el-table :data="burndown.points" border stripe size="small" max-height="260">
        <el-table-column prop="date" label="日期" width="140" />
        <el-table-column prop="remaining_hours" label="剩余工时" />
        <el-table-column prop="ideal_hours" label="理想工时" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-input v-model="boardId" placeholder="看板 ID" style="width: 180px" />
        <el-button @click="loadBoard">按 ID 查看看板</el-button>
      </div>
      <template v-if="board">
        <h4 style="margin: 8px 0">{{ board.name }}（{{ board.board_type }}）</h4>
        <el-row :gutter="12">
          <el-col v-for="column in board.columns" :key="column.id" :span="6">
            <el-card shadow="hover">
              <div class="fl-toolbar">
                <strong>{{ column.column_name }}</strong>
                <el-tag size="small" type="info">{{ column.item_count ?? 0 }}</el-tag>
              </div>
              <div class="fl-muted">状态：{{ (column.status_codes ?? []).join(' / ') || '-' }}</div>
              <div class="fl-muted">WIP 限制：{{ column.wip_limit ?? '无' }}</div>
            </el-card>
          </el-col>
        </el-row>
      </template>
      <el-empty v-else description="输入看板 ID 查看列配置" :image-size="70" />
    </el-card>

    <el-dialog v-model="createVisible" title="新建迭代" width="520px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="迭代名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item>
        <el-form-item label="迭代目标"><el-input v-model="form.goal" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="容量工时"><el-input v-model="form.capacity_hours" placeholder="如 320" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="boardVisible" title="创建看板" width="540px">
      <el-alert v-if="boardError" type="error" :closable="false" show-icon :title="boardError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="看板名称" required><el-input v-model="boardForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="看板类型">
          <el-select v-model="boardForm.board_type" style="width: 100%">
            <el-option label="Scrum" value="SCRUM" />
            <el-option label="看板" value="KANBAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="列配置">
          <el-input v-model="boardForm.columns" placeholder="列名:状态码,状态码|列名:状态码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="boardVisible = false">取消</el-button>
        <el-button type="primary" :loading="boardSaving" @click="submitBoard">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
