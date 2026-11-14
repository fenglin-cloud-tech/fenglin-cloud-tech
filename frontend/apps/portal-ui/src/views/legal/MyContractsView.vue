<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { contractStatus, fmtAmount, fmtTime, label, sealStatus, signStatusMap } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const status = ref('')
const keyword = ref('')

const drawer = ref(false)
const detail = ref<LegalRecord | null>(null)
const detailLoading = ref(false)

const gapItems = [
  '本页调用 /contracts；无 legal:contract:view 时后端强制 owner_id=当前用户，只有 legal:contract:view:self 也能看到本人合同，不会下发租户全量数据。',
  '附件、正文预览依赖文件中台，本页展示后端返回的版本、审批与签署方信息。',
  '合同的提交、审批、归档属于管理动作，员工端不提供入口；需要办理请联系法务。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.contracts.list({
      status: status.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: 20,
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

async function openDetail(row: LegalRecord) {
  drawer.value = true
  detailLoading.value = true
  try {
    detail.value = await legalApi.contracts.get(row.id)
  } catch (err) {
    detail.value = null
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="我的合同"
      description="仅展示本人负责的合同；数据来自 /api/legal/v1/contracts。"
    >
      <template #actions>
        <el-input v-model="keyword" placeholder="名称/编号" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="load">
          <el-option v-for="(text, code) in contractStatus" :key="code" :label="text" :value="code" />
        </el-select>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe @row-click="openDetail">
        <el-table-column prop="contract_no" label="合同编号" width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ label(contractStatus, row.status) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount, row.currency) }}</template>
        </el-table-column>
        <el-table-column label="用印" width="100">
          <template #default="{ row }">{{ label(sealStatus, row.seal_status) }}</template>
        </el-table-column>
        <el-table-column label="签署" width="100">
          <template #default="{ row }">{{ label(signStatusMap, row.sign_status) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="20"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>

    <el-drawer v-model="drawer" title="合同详情" size="680px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同编号">{{ detail.contract_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ label(contractStatus, detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="名称" :span="2">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ fmtAmount(detail.amount, detail.currency) }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.owner_id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ detail.start_date ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="结束日期">{{ detail.end_date ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmtTime(detail.created_at) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmtTime(detail.updated_at) }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">版本（{{ (detail.versions ?? []).length }}）</el-divider>
          <el-table :data="detail.versions ?? []" size="small" border max-height="180">
            <el-table-column prop="version_no" label="版本" width="70" />
            <el-table-column prop="created_stage" label="阶段" width="100" />
            <el-table-column prop="change_summary" label="变更说明" min-width="160" />
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>

          <el-divider content-position="left">签署方（{{ (detail.signatories ?? []).length }}）</el-divider>
          <el-table :data="detail.signatories ?? []" size="small" border max-height="180">
            <el-table-column prop="signer_name" label="签署人" width="120" />
            <el-table-column prop="party_type" label="类型" width="100" />
            <el-table-column prop="signer_mobile_tail" label="手机尾号" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
