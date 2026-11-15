<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceAssetCard,
  type FinanceAssetCategory,
  type FinanceDepreciationSchedule,
  type FinanceItAsset,
  type FinanceItAssetBorrowRecord,
  type FinanceLedger,
  type FinanceStocktaking,
  type FinanceStocktakingDetail,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref('cards')
const loading = ref(false)
const error = ref('')

const categories = ref<FinanceAssetCategory[]>([])
const cards = ref<FinanceAssetCard[]>([])
const itAssets = ref<FinanceItAsset[]>([])
const stocktakings = ref<FinanceStocktaking[]>([])
const ledgers = ref<FinanceLedger[]>([])

const categoryVisible = ref(false)
const categoryError = ref('')
const categoryForm = reactive({
  category_code: '',
  category_name: '',
  useful_life_months: 60,
  residual_rate: '0.05',
  depreciation_method: 'STRAIGHT_LINE',
  asset_account_code: '',
  depreciation_account_code: '',
  expense_account_code: '',
})

const cardVisible = ref(false)
const cardError = ref('')
const cardForm = reactive({
  asset_name: '',
  category_id: '' as string | '',
  specification: '',
  location: '',
  enable_date: '',
  original_value: '',
  it_asset_flag: false,
  device_type: '',
  brand_model: '',
  serial_no: '',
  ip_address: '',
})

const depreciationForm = reactive({ ledger_id: '' as string | '', period_code: '' })

const stocktakingVisible = ref(false)
const stocktakingError = ref('')
const stocktakingForm = reactive({
  stocktaking_name: '',
  scope_type: 'ALL',
  plan_start_date: '',
  plan_end_date: '',
})

const stocktakingDetailVisible = ref(false)
const stocktakingDetail = ref<FinanceStocktakingDetail | null>(null)
const countForm = reactive({ asset_id: '', actual_location: '', result: 'NORMAL', remark: '' })

const borrowVisible = ref(false)
const borrowError = ref('')
const borrowTarget = ref<FinanceItAsset | null>(null)
const borrowForm = reactive({ borrower_id: '', due_date: '' })

const ID_PATTERN = /^\d+$/

const editVisible = ref(false)
const editError = ref('')
const editTarget = ref<FinanceAssetCard | null>(null)
const editForm = reactive({
  asset_name: '',
  specification: '',
  location: '',
  dept_id: '',
  custodian_id: '',
  status: 'IN_USE',
})

const transferVisible = ref(false)
const transferError = ref('')
const transferTarget = ref<FinanceAssetCard | null>(null)
const transferForm = reactive({ dept_id: '', location: '', custodian_id: '', reason: '' })

const depreciationDetailVisible = ref(false)
const depreciationDetailLoading = ref(false)
const depreciationSchedules = ref<FinanceDepreciationSchedule[]>([])

const itemForm = reactive({ asset_id: '', expected_location: '', result: 'NORMAL', remark: '' })

const borrowRecordsVisible = ref(false)
const borrowRecordsLoading = ref(false)
const borrowRecords = ref<FinanceItAssetBorrowRecord[]>([])
const borrowRecordsTarget = ref<FinanceItAsset | null>(null)

const gapItems: string[] = []

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadCategories() {
  try {
    categories.value = await financeApi.assets.categories()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadCards() {
  loading.value = true
  error.value = ''
  try {
    cards.value = await financeApi.assets.cards({ size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadItAssets() {
  loading.value = true
  error.value = ''
  try {
    itAssets.value = await financeApi.assets.itAssets({ size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadStocktakings() {
  loading.value = true
  error.value = ''
  try {
    stocktakings.value = await financeApi.assets.stocktakings({ size: 50 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadLedgers() {
  try {
    ledgers.value = await financeApi.ledgers.list()
    if (!depreciationForm.ledger_id && ledgers.value.length) {
      depreciationForm.ledger_id = ledgers.value[0].id
    }
  } catch (err) {
    error.value = describeError(err)
  }
}

async function submitCategory() {
  if (!categoryForm.category_code.trim() || !categoryForm.category_name.trim()) {
    categoryError.value = '类别编码与名称不能为空'
    return
  }
  try {
    await financeApi.assets.createCategory({
      category_code: categoryForm.category_code.trim(),
      category_name: categoryForm.category_name.trim(),
      useful_life_months: Number(categoryForm.useful_life_months),
      residual_rate: categoryForm.residual_rate,
      depreciation_method: categoryForm.depreciation_method,
      asset_account_code: categoryForm.asset_account_code.trim() || undefined,
      depreciation_account_code: categoryForm.depreciation_account_code.trim() || undefined,
      expense_account_code: categoryForm.expense_account_code.trim() || undefined,
    })
    ElMessage.success('资产类别已创建')
    categoryVisible.value = false
    await loadCategories()
  } catch (err) {
    categoryError.value = describeError(err)
  }
}

async function submitCard() {
  if (!cardForm.asset_name.trim() || !cardForm.category_id || !cardForm.original_value) {
    cardError.value = '资产名称、类别与原值不能为空'
    return
  }
  try {
    await financeApi.assets.createCard({
      asset_name: cardForm.asset_name.trim(),
      category_id: cardForm.category_id,
      specification: cardForm.specification.trim() || undefined,
      location: cardForm.location.trim() || undefined,
      enable_date: cardForm.enable_date || undefined,
      original_value: cardForm.original_value,
      it_asset_flag: cardForm.it_asset_flag,
      device_type: cardForm.it_asset_flag ? cardForm.device_type.trim() || undefined : undefined,
      brand_model: cardForm.it_asset_flag ? cardForm.brand_model.trim() || undefined : undefined,
      serial_no: cardForm.it_asset_flag ? cardForm.serial_no.trim() || undefined : undefined,
      ip_address: cardForm.it_asset_flag ? cardForm.ip_address.trim() || undefined : undefined,
    })
    ElMessage.success('资产卡片已创建')
    cardVisible.value = false
    await loadCards()
  } catch (err) {
    cardError.value = describeError(err)
  }
}

function openEdit(row: FinanceAssetCard) {
  editTarget.value = row
  editForm.asset_name = row.asset_name
  editForm.specification = row.specification ?? ''
  editForm.location = row.location ?? ''
  editForm.dept_id = row.dept_id ?? ''
  editForm.custodian_id = row.custodian_id ?? ''
  editForm.status = row.status || 'IN_USE'
  editError.value = ''
  editVisible.value = true
}

async function submitEdit() {
  if (!editTarget.value) return
  const deptId = editForm.dept_id.trim()
  const custodianId = editForm.custodian_id.trim()
  if (!editForm.asset_name.trim()) {
    editError.value = '资产名称不能为空'
    return
  }
  if (deptId && !ID_PATTERN.test(deptId)) {
    editError.value = '部门 ID 必须为数字'
    return
  }
  if (custodianId && !ID_PATTERN.test(custodianId)) {
    editError.value = '保管人 ID 必须为数字'
    return
  }
  try {
    await financeApi.assets.updateCard(editTarget.value.id, {
      asset_name: editForm.asset_name.trim(),
      specification: editForm.specification.trim(),
      location: editForm.location.trim(),
      dept_id: deptId || null,
      custodian_id: custodianId || null,
      status: editForm.status,
    })
    ElMessage.success('资产卡片已更新')
    editVisible.value = false
    await loadCards()
  } catch (err) {
    editError.value = describeError(err)
  }
}

function openTransfer(row: FinanceAssetCard) {
  transferTarget.value = row
  transferForm.dept_id = ''
  transferForm.location = ''
  transferForm.custodian_id = ''
  transferForm.reason = ''
  transferError.value = ''
  transferVisible.value = true
}

async function submitTransfer() {
  if (!transferTarget.value) return
  const deptId = transferForm.dept_id.trim()
  const custodianId = transferForm.custodian_id.trim()
  const location = transferForm.location.trim()
  const reason = transferForm.reason.trim()
  if (deptId && !ID_PATTERN.test(deptId)) {
    transferError.value = '部门 ID 必须为数字'
    return
  }
  if (custodianId && !ID_PATTERN.test(custodianId)) {
    transferError.value = '保管人 ID 必须为数字'
    return
  }
  if (!deptId && !custodianId && !location && !reason) {
    transferError.value = '请至少填写一项调拨信息'
    return
  }
  try {
    await financeApi.assets.transferCard(transferTarget.value.id, {
      ...(deptId ? { dept_id: deptId } : {}),
      ...(custodianId ? { custodian_id: custodianId } : {}),
      ...(location ? { location } : {}),
      ...(reason ? { reason } : {}),
    })
    ElMessage.success('资产调拨已登记')
    transferVisible.value = false
    await loadCards()
  } catch (err) {
    transferError.value = describeError(err)
  }
}

async function openDepreciationSchedules() {
  depreciationDetailVisible.value = true
  depreciationDetailLoading.value = true
  try {
    depreciationSchedules.value = await financeApi.assets.depreciationSchedules({ size: 100 })
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    depreciationDetailLoading.value = false
  }
}

async function dispose(row: FinanceAssetCard) {
  try {
    await ElMessageBox.confirm(`确认处置资产 ${row.asset_no} ${row.asset_name}？`, '资产处置', { type: 'warning' })
  } catch {
    return
  }
  try {
    const result = await financeApi.assets.dispose(row.id, { disposal_date: new Date().toISOString().slice(0, 10) })
    ElMessage.success(`处置单 ${result.change_no} 已生成，损益 ${money(result.gain_loss)}`)
    await loadCards()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function runDepreciation() {
  if (!depreciationForm.ledger_id || !depreciationForm.period_code.trim()) {
    ElMessage.warning('请选择账套并填写期间')
    return
  }
  try {
    await ElMessageBox.confirm(`确认对期间 ${depreciationForm.period_code} 计提折旧？`, '计提折旧', { type: 'warning' })
  } catch {
    return
  }
  try {
    const result = await financeApi.assets.runDepreciation({
      ledger_id: depreciationForm.ledger_id,
      period_code: depreciationForm.period_code.trim(),
    })
    ElMessage.success(`批次 ${result.batch_no}：${result.asset_count} 项资产，计提 ${money(result.total_amount)}`)
    await loadCards()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitStocktaking() {
  if (!stocktakingForm.stocktaking_name.trim()) {
    stocktakingError.value = '盘点名称不能为空'
    return
  }
  try {
    await financeApi.assets.createStocktaking({
      stocktaking_name: stocktakingForm.stocktaking_name.trim(),
      scope_type: stocktakingForm.scope_type,
      plan_start_date: stocktakingForm.plan_start_date || undefined,
      plan_end_date: stocktakingForm.plan_end_date || undefined,
    })
    ElMessage.success('盘点任务已创建')
    stocktakingVisible.value = false
    await loadStocktakings()
  } catch (err) {
    stocktakingError.value = describeError(err)
  }
}

async function openStocktaking(row: FinanceStocktaking) {
  stocktakingDetailVisible.value = true
  stocktakingDetail.value = null
  try {
    stocktakingDetail.value = await financeApi.assets.stocktakingDetail(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitCount() {
  if (!stocktakingDetail.value || !countForm.asset_id) {
    ElMessage.warning('请填写资产 ID')
    return
  }
  try {
    stocktakingDetail.value = await financeApi.assets.countStocktaking(stocktakingDetail.value.stocktaking.id, {
      asset_id: countForm.asset_id,
      actual_location: countForm.actual_location.trim() || undefined,
      result: countForm.result,
      remark: countForm.remark.trim() || undefined,
    })
    ElMessage.success('盘点结果已记录')
    countForm.asset_id = ''
    countForm.remark = ''
    await loadStocktakings()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function addStocktakingItem() {
  if (!stocktakingDetail.value) return
  if (!itemForm.asset_id) {
    ElMessage.warning('请选择资产')
    return
  }
  try {
    stocktakingDetail.value = await financeApi.assets.addStocktakingItem(stocktakingDetail.value.stocktaking.id, {
      asset_id: itemForm.asset_id,
      expected_location: itemForm.expected_location.trim() || undefined,
      result: itemForm.result,
      remark: itemForm.remark.trim() || undefined,
    })
    ElMessage.success('盘点项已添加')
    itemForm.asset_id = ''
    itemForm.expected_location = ''
    itemForm.remark = ''
    await loadStocktakings()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeStocktakingItem(itemId: string) {
  if (!stocktakingDetail.value) return
  try {
    await ElMessageBox.confirm('确认删除该盘点项？', '删除盘点项', { type: 'warning' })
  } catch {
    return
  }
  try {
    stocktakingDetail.value = await financeApi.assets.deleteStocktakingItem(
      stocktakingDetail.value.stocktaking.id,
      itemId,
    )
    ElMessage.success('盘点项已删除')
    await loadStocktakings()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function completeStocktaking() {
  if (!stocktakingDetail.value) return
  try {
    await ElMessageBox.confirm('确认完成盘点？完成后不可再修改盘点结果。', '完成盘点', { type: 'warning' })
  } catch {
    return
  }
  try {
    const result = await financeApi.assets.completeStocktaking(stocktakingDetail.value.stocktaking.id)
    ElMessage.success(`盘点完成：盘盈 ${result.surplus_count}，盘亏 ${result.deficit_count}`)
    stocktakingDetailVisible.value = false
    await loadStocktakings()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openBorrow(row: FinanceItAsset) {
  borrowTarget.value = row
  borrowForm.borrower_id = ''
  borrowForm.due_date = ''
  borrowError.value = ''
  borrowVisible.value = true
}

async function submitBorrow() {
  if (!borrowTarget.value || !borrowForm.borrower_id) {
    borrowError.value = '借用人工号/ID 不能为空'
    return
  }
  try {
    await financeApi.assets.borrowIt(borrowTarget.value.asset_id, {
      borrower_id: borrowForm.borrower_id,
      due_date: borrowForm.due_date || undefined,
    })
    ElMessage.success('借用已登记')
    borrowVisible.value = false
    await loadItAssets()
  } catch (err) {
    borrowError.value = describeError(err)
  }
}

async function returnIt(row: FinanceItAsset) {
  try {
    await financeApi.assets.returnIt(row.asset_id)
    ElMessage.success('归还已登记')
    await loadItAssets()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function syncIt(row: FinanceItAsset) {
  try {
    await financeApi.assets.syncIt(row.asset_id, { success: true })
    ElMessage.success('CMDB 同步状态已回写')
    await loadItAssets()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function openBorrowRecords(row: FinanceItAsset) {
  borrowRecordsTarget.value = row
  borrowRecordsVisible.value = true
  borrowRecordsLoading.value = true
  borrowRecords.value = []
  try {
    borrowRecords.value = await financeApi.assets.itBorrowRecords(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    borrowRecordsLoading.value = false
  }
}

function changeTab(name: string | number) {
  if (name === 'cards') loadCards()
  else if (name === 'stocktaking') loadStocktakings()
  else if (name === 'it') loadItAssets()
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadCards(), loadLedgers()])
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="固定资产与 IT 资产"
      description="资产类别、卡片台账、批量计提折旧、处置、盘点与 IT 资产借用/归还/CMDB 同步。"
    >
      <template #actions>
        <el-button :loading="loading" @click="changeTab(activeTab)">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice v-if="gapItems.length" :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="changeTab">
        <el-tab-pane label="资产台账" name="cards">
          <div class="fl-toolbar">
            <el-button v-perm="'biz:finance:asset:create'" type="primary" plain @click="cardVisible = true">新增资产</el-button>
            <el-button v-perm="'tenant:finance:asset:category:manage'" plain @click="categoryVisible = true">新增类别</el-button>
            <span class="fl-muted">共 {{ cards.length }} 项资产</span>
          </div>
          <el-table :data="cards" v-loading="loading" border stripe>
            <el-table-column prop="asset_no" label="资产编号" width="160" />
            <el-table-column prop="asset_name" label="资产名称" min-width="160" />
            <el-table-column prop="specification" label="规格" width="120" />
            <el-table-column prop="location" label="位置" width="120" />
            <el-table-column label="原值" width="130" align="right"><template #default="{ row }">{{ money(row.original_value) }}</template></el-table-column>
            <el-table-column label="累计折旧" width="130" align="right"><template #default="{ row }">{{ money(row.accumulated_depreciation) }}</template></el-table-column>
            <el-table-column label="净值" width="130" align="right"><template #default="{ row }">{{ money(row.net_value) }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'biz:finance:asset:create'" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-perm="'biz:finance:asset:create'" link @click="openTransfer(row)">调拨</el-button>
                <el-button v-perm="'biz:finance:asset:dispose'" link type="danger" @click="dispose(row)">处置</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="资产类别" name="categories">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:asset:category:manage'" type="primary" plain @click="categoryVisible = true">新增类别</el-button>
            <span class="fl-muted">共 {{ categories.length }} 个类别</span>
          </div>
          <el-table :data="categories" border stripe>
            <el-table-column prop="category_code" label="类别编码" width="140" />
            <el-table-column prop="category_name" label="类别名称" min-width="160" />
            <el-table-column prop="useful_life_months" label="使用月数" width="110" />
            <el-table-column prop="residual_rate" label="残值率" width="100" />
            <el-table-column prop="depreciation_method" label="折旧方法" width="140" />
            <el-table-column prop="asset_account_code" label="资产科目" width="120" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="计提折旧" name="depreciation">
          <el-form inline>
            <el-form-item label="账套">
              <el-select v-model="depreciationForm.ledger_id" style="width: 220px">
                <el-option v-for="ledger in ledgers" :key="ledger.id" :label="ledger.ledger_name" :value="ledger.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="期间">
              <el-input v-model="depreciationForm.period_code" placeholder="202611" style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'biz:finance:asset:depreciate'" type="primary" @click="runDepreciation">计提折旧</el-button>
              <el-button plain @click="openDepreciationSchedules">折旧明细</el-button>
            </el-form-item>
          </el-form>
          <el-alert type="info" :closable="false" show-icon title="折旧按后端规则整批计提并生成凭证，可通过「折旧明细」查看最近批次的计提结果。" />
        </el-tab-pane>

        <el-tab-pane label="资产盘点" name="stocktaking">
          <div class="fl-toolbar">
            <el-button v-perm="'biz:finance:asset:stocktaking'" type="primary" plain @click="stocktakingVisible = true">新建盘点</el-button>
            <span class="fl-muted">共 {{ stocktakings.length }} 个盘点任务</span>
          </div>
          <el-table :data="stocktakings" v-loading="loading" border stripe>
            <el-table-column prop="stocktaking_no" label="盘点单号" width="170" />
            <el-table-column prop="stocktaking_name" label="盘点名称" min-width="160" />
            <el-table-column prop="scope_type" label="范围" width="100" />
            <el-table-column prop="total_count" label="总数" width="80" />
            <el-table-column prop="normal_count" label="正常" width="80" />
            <el-table-column prop="surplus_count" label="盘盈" width="80" />
            <el-table-column prop="deficit_count" label="盘亏" width="80" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link @click="openStocktaking(row)">盘点</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="IT 资产" name="it">
          <el-table :data="itAssets" v-loading="loading" border stripe>
            <el-table-column prop="asset_id" label="资产 ID" width="100" />
            <el-table-column prop="device_type" label="设备类型" width="120" />
            <el-table-column prop="brand_model" label="品牌型号" min-width="150" />
            <el-table-column prop="serial_no" label="序列号" width="140" />
            <el-table-column prop="ip_address" label="IP" width="130" />
            <el-table-column prop="borrow_status" label="借用状态" width="110" />
            <el-table-column prop="borrower_id" label="借用人" width="100" />
            <el-table-column prop="due_date" label="应还日期" width="120" />
            <el-table-column prop="sync_status" label="同步状态" width="110" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.borrow_status !== 'BORROWED'" v-perm="'biz:finance:itasset:borrow'" link type="primary" @click="openBorrow(row)">借用</el-button>
                <el-button v-else v-perm="'biz:finance:itasset:borrow'" link type="success" @click="returnIt(row)">归还</el-button>
                <el-button v-perm="'tenant:finance:itasset:manage'" link @click="syncIt(row)">同步</el-button>
                <el-button link @click="openBorrowRecords(row)">借用记录</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="categoryVisible" title="新增资产类别" width="540px">
      <el-alert v-if="categoryError" type="error" :closable="false" show-icon :title="categoryError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="类别编码" required><el-input v-model="categoryForm.category_code" maxlength="32" /></el-form-item>
        <el-form-item label="类别名称" required><el-input v-model="categoryForm.category_name" maxlength="64" /></el-form-item>
        <el-form-item label="使用月数"><el-input-number v-model="categoryForm.useful_life_months" :min="1" :max="600" style="width: 100%" /></el-form-item>
        <el-form-item label="残值率"><el-input v-model="categoryForm.residual_rate" /></el-form-item>
        <el-form-item label="折旧方法"><el-input v-model="categoryForm.depreciation_method" maxlength="32" /></el-form-item>
        <el-form-item label="资产科目"><el-input v-model="categoryForm.asset_account_code" maxlength="32" /></el-form-item>
        <el-form-item label="折旧科目"><el-input v-model="categoryForm.depreciation_account_code" maxlength="32" /></el-form-item>
        <el-form-item label="费用科目"><el-input v-model="categoryForm.expense_account_code" maxlength="32" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cardVisible" title="新增资产卡片" width="560px">
      <el-alert v-if="cardError" type="error" :closable="false" show-icon :title="cardError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="资产名称" required><el-input v-model="cardForm.asset_name" maxlength="128" /></el-form-item>
        <el-form-item label="资产类别" required>
          <el-select v-model="cardForm.category_id" style="width: 100%">
            <el-option v-for="category in categories" :key="category.id" :label="`${category.category_code} ${category.category_name}`" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格型号"><el-input v-model="cardForm.specification" maxlength="128" /></el-form-item>
        <el-form-item label="存放位置"><el-input v-model="cardForm.location" maxlength="128" /></el-form-item>
        <el-form-item label="启用日期">
          <el-date-picker v-model="cardForm.enable_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原值" required><el-input v-model="cardForm.original_value" placeholder="0.00" /></el-form-item>
        <el-form-item label="IT 资产"><el-switch v-model="cardForm.it_asset_flag" /></el-form-item>
        <template v-if="cardForm.it_asset_flag">
          <el-form-item label="设备类型"><el-input v-model="cardForm.device_type" maxlength="32" /></el-form-item>
          <el-form-item label="品牌型号"><el-input v-model="cardForm.brand_model" maxlength="64" /></el-form-item>
          <el-form-item label="序列号"><el-input v-model="cardForm.serial_no" maxlength="64" /></el-form-item>
          <el-form-item label="IP 地址"><el-input v-model="cardForm.ip_address" maxlength="64" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="cardVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCard">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stocktakingVisible" title="新建盘点任务" width="480px">
      <el-alert v-if="stocktakingError" type="error" :closable="false" show-icon :title="stocktakingError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="盘点名称" required><el-input v-model="stocktakingForm.stocktaking_name" maxlength="128" /></el-form-item>
        <el-form-item label="盘点范围">
          <el-select v-model="stocktakingForm.scope_type" style="width: 100%">
            <el-option label="全部资产" value="ALL" />
            <el-option label="按部门" value="DEPT" />
            <el-option label="按位置" value="LOCATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="stocktakingForm.plan_start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="stocktakingForm.plan_end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stocktakingVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStocktaking">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stocktakingDetailVisible" title="盘点明细" width="860px">
      <template v-if="stocktakingDetail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="盘点单">{{ stocktakingDetail.stocktaking.stocktaking_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ stocktakingDetail.stocktaking.status }}</el-descriptions-item>
          <el-descriptions-item label="盘盈/盘亏">
            {{ stocktakingDetail.stocktaking.surplus_count }} / {{ stocktakingDetail.stocktaking.deficit_count }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form inline style="margin-top: 12px">
          <el-form-item label="资产 ID"><el-input v-model="countForm.asset_id" style="width: 130px" /></el-form-item>
          <el-form-item label="实际位置"><el-input v-model="countForm.actual_location" style="width: 150px" /></el-form-item>
          <el-form-item label="结果">
            <el-select v-model="countForm.result" style="width: 110px">
              <el-option label="正常" value="NORMAL" />
              <el-option label="盘盈" value="SURPLUS" />
              <el-option label="盘亏" value="DEFICIT" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button type="primary" plain @click="submitCount">记录盘点</el-button></el-form-item>
        </el-form>
        <el-form v-if="stocktakingDetail.stocktaking.status === 'IN_PROGRESS'" inline style="margin-top: 12px">
          <el-form-item label="新增资产">
            <el-select v-model="itemForm.asset_id" filterable placeholder="选择资产" style="width: 200px">
              <el-option v-for="card in cards" :key="card.id" :label="`${card.asset_no} ${card.asset_name}`" :value="card.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="应存位置"><el-input v-model="itemForm.expected_location" style="width: 130px" /></el-form-item>
          <el-form-item label="结果">
            <el-select v-model="itemForm.result" style="width: 100px">
              <el-option label="正常" value="NORMAL" />
              <el-option label="盘盈" value="SURPLUS" />
              <el-option label="盘亏" value="DEFICIT" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="itemForm.remark" style="width: 130px" /></el-form-item>
          <el-form-item><el-button type="primary" plain @click="addStocktakingItem">新增盘点项</el-button></el-form-item>
        </el-form>
        <el-table :data="stocktakingDetail.items" border size="small" max-height="300">
          <el-table-column prop="asset_no" label="资产编号" width="150" />
          <el-table-column prop="asset_name" label="资产名称" min-width="150" />
          <el-table-column prop="expected_location" label="应存位置" width="130" />
          <el-table-column prop="actual_location" label="实际位置" width="130" />
          <el-table-column prop="result" label="结果" width="100" />
          <el-table-column prop="remark" label="备注" min-width="120" />
          <el-table-column
            v-if="stocktakingDetail.stocktaking.status === 'IN_PROGRESS'"
            label="操作"
            width="70"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button link type="danger" @click="removeStocktakingItem(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="stocktakingDetailVisible = false">关闭</el-button>
        <el-button v-perm="'biz:finance:asset:stocktaking'" type="primary" @click="completeStocktaking">完成盘点</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="borrowVisible" title="IT 资产借用" width="440px">
      <el-alert v-if="borrowError" type="error" :closable="false" show-icon :title="borrowError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="资产 ID">{{ borrowTarget?.asset_id }}</el-form-item>
        <el-form-item label="借用人 ID" required><el-input v-model="borrowForm.borrower_id" /></el-form-item>
        <el-form-item label="应还日期">
          <el-date-picker v-model="borrowForm.due_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="borrowVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBorrow">确认借用</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="editVisible" title="编辑资产卡片" width="520px">
      <el-alert v-if="editError" type="error" :closable="false" show-icon :title="editError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="资产名称" required><el-input v-model="editForm.asset_name" maxlength="128" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="editForm.specification" maxlength="128" /></el-form-item>
        <el-form-item label="存放位置"><el-input v-model="editForm.location" maxlength="128" /></el-form-item>
        <el-form-item label="部门 ID"><el-input v-model="editForm.dept_id" placeholder="数字 ID" maxlength="20" /></el-form-item>
        <el-form-item label="保管人 ID"><el-input v-model="editForm.custodian_id" placeholder="数字 ID" maxlength="20" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="在用" value="IN_USE" />
            <el-option label="闲置" value="IDLE" />
            <el-option label="借出" value="LENT" />
            <el-option label="维修中" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="资产调拨" width="520px">
      <el-alert v-if="transferError" type="error" :closable="false" show-icon :title="transferError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="部门 ID"><el-input v-model="transferForm.dept_id" placeholder="数字 ID" maxlength="20" /></el-form-item>
        <el-form-item label="存放位置"><el-input v-model="transferForm.location" maxlength="128" /></el-form-item>
        <el-form-item label="保管人 ID"><el-input v-model="transferForm.custodian_id" placeholder="数字 ID" maxlength="20" /></el-form-item>
        <el-form-item label="调拨原因"><el-input v-model="transferForm.reason" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="depreciationDetailVisible" title="折旧明细" width="900px">
      <el-table :data="depreciationSchedules" v-loading="depreciationDetailLoading" border size="small" max-height="420">
        <el-table-column prop="batch_no" label="批次" width="150" />
        <el-table-column prop="period_code" label="期间" width="100" />
        <el-table-column prop="asset_no" label="资产编号" width="150" />
        <el-table-column prop="asset_name" label="资产名称" min-width="150" />
        <el-table-column label="本期折旧" width="120" align="right">
          <template #default="{ row }">{{ money(row.depreciation_amount) }}</template>
        </el-table-column>
        <el-table-column label="累计折旧后" width="130" align="right">
          <template #default="{ row }">{{ money(row.accumulated_after) }}</template>
        </el-table-column>
        <el-table-column label="净值后" width="120" align="right">
          <template #default="{ row }">{{ money(row.net_value_after) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="depreciationDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="borrowRecordsVisible" title="借用记录" width="820px">
      <div class="fl-muted" style="margin-bottom: 8px">资产 ID：{{ borrowRecordsTarget?.asset_id }}</div>
      <el-table :data="borrowRecords" v-loading="borrowRecordsLoading" border size="small" max-height="420">
        <el-table-column prop="change_type" label="类型" width="100" />
        <el-table-column prop="occurred_at" label="发生时间" width="180" />
        <el-table-column prop="before_json" label="变更前" min-width="240" show-overflow-tooltip />
        <el-table-column prop="after_json" label="变更后" min-width="240" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="borrowRecordsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
