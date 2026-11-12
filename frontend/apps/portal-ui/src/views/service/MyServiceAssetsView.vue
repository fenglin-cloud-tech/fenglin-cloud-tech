<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const assets = ref<ServiceRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const loading = ref(false)
const error = ref('')
const reminder = ref<ServiceRecord | null>(null)
const returning = ref(false)

const gapItems = [
  '资产提醒接口 /assets/reminders 返回租户级汇总（总量/借出/逾期），不支持按本人过滤。',
  '自助归还直接置为在库（IDLE），不经过资产管理员确认；若产品需要确认环节，可在此处加一步，不改本接口契约。',
  '资产台账按 ownerId 过滤，仅显示负责人为当前登录用户的资产。',
]

async function load() {
  if (!auth.userId) {
    error.value = '当前登录用户缺少用户 ID，无法查询“我的资产”'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await serviceApi.assets.list({
      ownerId: auth.userId,
      status: status.value || undefined,
      page: page.value,
      size: size.value,
    })
    assets.value = result.records
    total.value = result.total
    reminder.value = await serviceApi.assets.reminders().catch(() => null)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function returnSelf(row: ServiceRecord) {
  if (returning.value) return
  returning.value = true
  error.value = ''
  try {
    await serviceApi.assets.returnSelf(row.id)
    ElMessage.success('已归还，资产状态已置为在库')
    await load()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    returning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服服务台"
      title="我的资产"
      description="本人名下 IT 资产与逾期提醒；数据来自 /api/service/v1/assets。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="8">
        <el-card shadow="never">
          <el-statistic title="我的资产" :value="total" />
          <p class="fl-muted">该数量为按 ownerId 过滤后的台账总数。</p>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header><span>租户资产提醒</span></template>
          <el-descriptions v-if="reminder" :column="3" border size="small">
            <el-descriptions-item label="资产总量">{{ reminder.total ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="借出中">{{ reminder.borrowed ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="已逾期">{{ reminder.overdue ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无提醒数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <div class="fl-toolbar" style="margin-bottom: 8px">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 170px" @change="page = 1; load()">
          <el-option v-for="item in ['IN_USE', 'BORROWED', 'IDLE', 'SCRAPPED']" :key="item" :label="item" :value="item" />
        </el-select>
        <span class="fl-muted">负责人：{{ auth.displayName }}（ID {{ auth.userId ?? '-' }}）</span>
      </div>
      <el-table :data="assets" v-loading="loading" border stripe size="small">
        <el-table-column prop="asset_no" label="资产编号" width="170" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="asset_type" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="borrow_due_at" label="借用到期" width="170" />
        <el-table-column prop="ext_ref" label="外部引用" min-width="140" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'BORROWED'"
              size="small"
              plain
              :loading="returning"
              @click="returnSelf(row)"
            >归还</el-button>
            <span v-else class="fl-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>
  </div>
</template>

<style scoped>
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
