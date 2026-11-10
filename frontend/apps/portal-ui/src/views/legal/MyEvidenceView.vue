<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const filters = reactive({ contract_id: '', evidence_type: '' })
const chainResult = ref<LegalRecord | null>(null)

const gapItems = [
  '存证查询接口 /evidences 目前只校验 legal:evidence:view，没有本人范围过滤；因此菜单仅在具备该权限时展示，避免无权限用户进入。',
  '如需做到“只看本人合同存证”，需后端补 owner 过滤；当前页面会明确标注这一缺口。',
  '核验（单条 / 链式）调用后端真实哈希校验，前端不构造哈希。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.evidences.list({
      contract_id: filters.contract_id ? Number(filters.contract_id) : undefined,
      evidence_type: filters.evidence_type || undefined,
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

async function verify(row: LegalRecord) {
  try {
    const result = await legalApi.evidences.verify(Number(row.id))
    ElMessage.success(`核验完成：${result.verified === false ? '不一致' : '通过'}`)
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function chainVerify() {
  if (!filters.contract_id) {
    ElMessage.warning('请填写合同ID')
    return
  }
  try {
    chainResult.value = await legalApi.evidences.chainVerify(Number(filters.contract_id))
  } catch (err) {
    chainResult.value = null
    ElMessage.error(describeError(err))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="我的存证"
      description="合同签署与用印存证查询、核验；数据来自 /api/legal/v1/evidences。"
    >
      <template #actions>
        <el-input v-model="filters.contract_id" placeholder="合同ID" clearable style="width: 150px" />
        <el-select v-model="filters.evidence_type" placeholder="全部类型" clearable style="width: 130px">
          <el-option label="签署" value="SIGN" />
          <el-option label="用印" value="SEAL" />
          <el-option label="归档" value="ARCHIVE" />
        </el-select>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="evidence_no" label="存证编号" width="180" />
        <el-table-column prop="contract_id" label="合同ID" width="170" />
        <el-table-column prop="evidence_type" label="类型" width="100" />
        <el-table-column prop="content_hash" label="内容哈希" min-width="180" show-overflow-tooltip />
        <el-table-column prop="worm_ref" label="WORM 引用" min-width="160" show-overflow-tooltip />
        <el-table-column label="已核验" width="90">
          <template #default="{ row }">{{ row.verified ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="核验时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.verified_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-perm="'legal:evidence:verify'" link type="primary" @click="verify(row)">核验</el-button>
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
      <div style="margin-top: 12px">
        <el-button v-perm="'legal:evidence:verify'" plain @click="chainVerify">按当前合同链式核验</el-button>
      </div>
      <el-descriptions v-if="chainResult" :column="2" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="链式校验">{{ chainResult.valid === false ? '不一致' : '通过' }}</el-descriptions-item>
        <el-descriptions-item label="存证数量">{{ chainResult.count ?? chainResult.evidence_count ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
