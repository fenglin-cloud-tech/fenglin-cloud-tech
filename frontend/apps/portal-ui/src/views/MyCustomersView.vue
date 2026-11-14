<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { CrmActivityDialog } from '@fenglin/ui-admin'
import { crmApi, type CrmId, type CustomerDetailView, type CustomerView } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const rows = ref<CustomerView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ keyword: '', segment: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CustomerDetailView | null>(null)
const activityVisible = ref(false)
const activityBizId = ref<CrmId>('')

const segmentOptions = [
  { value: 'SMB', label: '中小企业' },
  { value: 'KA', label: '重点客户' },
  { value: 'GOV', label: '政企' },
]

async function load() {
  if (!auth.userId) {
    error.value = '登录上下文缺少用户 ID，无法过滤“我的客户”'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.customers.my({
      keyword: query.keyword || undefined,
      segment: query.segment || undefined,
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

async function openDetail(row: CustomerView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await crmApi.customers.myDetail(row.customer_id)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

function openActivity(row: CustomerView) {
  activityBizId.value = row.customer_id
  activityVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="客户名称/编号" clearable style="width: 220px" @keyup.enter="query.page = 1; load()" />
        <el-select v-model="query.segment" placeholder="客户分层" clearable style="width: 140px" @change="query.page = 1; load()">
          <el-option v-for="item in segmentOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <span class="fl-muted">负责人：{{ auth.displayName }} · 共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="customer_no" label="客户编号" width="170" />
        <el-table-column prop="name" label="客户名称" min-width="180" />
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="segment" label="分层" width="100" />
        <el-table-column prop="grade" label="评级" width="80" />
        <el-table-column prop="arr" label="ARR" width="120" />
        <el-table-column prop="mobile_masked" label="联系电话" width="140" />
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

    <el-drawer v-model="detailVisible" title="客户详情" size="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="客户编号">{{ detail.customer.customer_no }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ detail.customer.name }}</el-descriptions-item>
            <el-descriptions-item label="行业">{{ detail.customer.industry || '-' }}</el-descriptions-item>
            <el-descriptions-item label="统一社会信用代码">{{ detail.customer.uscc || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分层">{{ detail.customer.segment || '-' }}</el-descriptions-item>
            <el-descriptions-item label="评级">{{ detail.customer.grade || '-' }}</el-descriptions-item>
            <el-descriptions-item label="ARR">{{ detail.customer.arr ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.customer.status }}</el-descriptions-item>
          </el-descriptions>

          <h4 style="margin: 16px 0 8px">联系人</h4>
          <el-table :data="detail.contacts" size="small" border>
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column prop="position" label="职位" width="140" />
            <el-table-column prop="mobile_masked" label="手机号" width="140" />
            <el-table-column prop="email" label="邮箱" min-width="160" />
          </el-table>

          <h4 style="margin: 16px 0 8px">商机</h4>
          <el-table :data="detail.opportunities" size="small" border>
            <el-table-column prop="opportunity_no" label="商机编号" width="160" />
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="stage" label="阶段" width="90" />
            <el-table-column prop="amount" label="金额" width="120" />
          </el-table>

          <h4 style="margin: 16px 0 8px">跟进记录</h4>
          <el-empty v-if="!detail.activities.length" description="暂无跟进记录" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item v-for="item in detail.activities" :key="String(item.activity_id)" :timestamp="item.occurred_at">
              <strong>{{ item.subject || item.activity_type }}</strong>
              <div class="fl-muted">{{ item.result || '-' }}</div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
    </el-drawer>

    <CrmActivityDialog v-model="activityVisible" :biz-type="'CUSTOMER'" :biz-id="activityBizId" @saved="load" />
  </div>
</template>
