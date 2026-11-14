<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { CrmActivityDialog } from '@fenglin/ui-admin'
import { crmApi, type CrmId, type LeadView } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const rows = ref<LeadView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ status: '', keyword: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<LeadView | null>(null)
const activities = ref<Array<{ activity_id: CrmId; activity_type: string; subject?: string; occurred_at?: string; result?: string }>>([])

const activityVisible = ref(false)
const activityBizId = ref<CrmId>('')

const statusOptions = [
  { value: 'NEW', label: '待分配' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'FOLLOWING', label: '跟进中' },
  { value: 'CONVERTED', label: '已转客户' },
  { value: 'INVALID', label: '无效' },
]
const sourceLabels: Record<string, string> = {
  MANUAL: '手工录入',
  IMPORT: '批量导入',
  FORM: '表单',
  API: '接口',
  PARTNER: '合作伙伴',
  OPINION: '舆情线索',
}

function statusLabel(value?: string) {
  return statusOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function statusTag(value?: string) {
  if (value === 'CONVERTED') return 'success'
  if (value === 'INVALID') return 'info'
  if (value === 'FOLLOWING') return 'warning'
  return 'info'
}

async function load() {
  if (!auth.userId) {
    error.value = '登录上下文缺少用户 ID，无法过滤“我的线索”'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.leads.my({
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: LeadView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  activities.value = []
  try {
    const result = await crmApi.leads.myDetail(row.lead_id)
    detail.value = result.lead
    activities.value = result.activities
  } catch (err) {
    error.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

function openActivity(row: LeadView) {
  activityBizId.value = row.lead_id
  activityVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select v-model="query.status" placeholder="线索状态" clearable style="width: 140px" @change="query.page = 1; load()">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="线索名称/编号" clearable style="width: 220px" @keyup.enter="query.page = 1; load()" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <span class="fl-muted">负责人：{{ auth.displayName }} · 共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="lead_no" label="线索编号" width="170" />
        <el-table-column prop="name" label="线索名称" min-width="160" />
        <el-table-column prop="contact_name" label="联系人" width="110" />
        <el-table-column prop="mobile_masked" label="手机号" width="130" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ sourceLabels[row.source] ?? row.source ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_activity_at" label="最近跟进" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'crm:activity:create'" link type="success" @click="openActivity(row)">跟进</el-button>
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
    </el-card>

    <el-drawer v-model="detailVisible" title="线索详情" size="520px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border size="small">
          <el-descriptions-item label="线索编号">{{ detail.lead_no }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contact_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.mobile_masked || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ detail.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ detail.score }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.created_at || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 16px 0 8px">跟进记录</h4>
        <el-empty v-if="!activities.length" description="暂无跟进记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item v-for="item in activities" :key="String(item.activity_id)" :timestamp="item.occurred_at">
            <strong>{{ item.subject || item.activity_type }}</strong>
            <div class="fl-muted">{{ item.result || '-' }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <CrmActivityDialog v-model="activityVisible" :biz-type="'LEAD'" :biz-id="activityBizId" @saved="load" />
  </div>
</template>
