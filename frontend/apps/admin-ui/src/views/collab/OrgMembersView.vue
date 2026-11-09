<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { iamApi, type RoleView, type UserView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

/** 用户列表接口可能额外返回手机号/邮箱字段，类型上保持可选，运行时原样展示。 */
interface OrgUserRow extends UserView {
  mobile?: string
  mobile_masked?: string
  email?: string
}

const users = ref<OrgUserRow[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ keyword: '', status: '', page: 1, size: 10 })

const roles = ref<RoleView[]>([])
const roleLoading = ref(false)
const roleError = ref('')

const detailVisible = ref(false)
const detailTarget = ref<OrgUserRow | null>(null)

const roleNameMap = computed(() => {
  const map = new Map<string, string>()
  roles.value.forEach((role) => map.set(role.code, role.name))
  return map
})

const statusOptions = [
  { value: 'ACTIVE', label: 'ACTIVE' },
  { value: 'DISABLED', label: 'DISABLED' },
]

function roleLabel(code: string) {
  return roleNameMap.value.get(code) || code
}

function mobileOf(row: OrgUserRow) {
  return row.mobile_masked || row.mobile || '-'
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const result = await iamApi.listUsers(query)
    users.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roleLoading.value = true
  roleError.value = ''
  try {
    const result = await iamApi.listRoles({ page: 1, size: 200 })
    roles.value = result.records
  } catch (err) {
    roleError.value = describeError(err)
  } finally {
    roleLoading.value = false
  }
}

function openDetail(row: OrgUserRow) {
  detailTarget.value = row
  detailVisible.value = true
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.page = 1
  void loadUsers()
}

onMounted(() => {
  void Promise.allSettled([loadUsers(), loadRoles()])
})
</script>

<template>
  <div>
    <PageHeader
      module="云协 Collab"
      title="组织与成员"
      description="成员目录来自 IAM 用户与角色服务；Collab 不单独维护组织架构与成员状态。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadUsers">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '组织架构树：talent 服务有 GET /api/talent/v1/orgs/tree，但网关未配置 /api/talent/** 路由，管理台无法调用，本页不展示虚构组织树。',
        '成员部门归属：IAM 用户列表未返回部门字段，只能展示账号与角色。',
        '成员邀请/停用/离职等状态变更没有 Collab 接口，需在 IAM 身份与权限页办理。',
      ]"
    />

    <div class="fl-card role-card" v-loading="roleLoading">
      <div class="role-card__head">
        <h3>角色概览</h3>
        <span class="fl-muted">共 {{ roles.length }} 个角色（来自 IAM listRoles）</span>
      </div>
      <el-alert v-if="roleError" type="error" :closable="false" show-icon :title="roleError" style="margin-bottom: 12px" />
      <div v-if="roles.length" class="role-tags">
        <el-tag v-for="role in roles" :key="role.role_id" effect="plain" size="small">
          {{ role.name }}（{{ role.code }}）
        </el-tag>
      </div>
      <div v-else class="fl-muted">暂无角色数据</div>
    </div>

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="用户名/姓名关键词" clearable style="width: 240px" @keyup.enter="query.page = 1; loadUsers()" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" plain @click="query.page = 1; loadUsers()">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="users" v-loading="loading" border stripe row-key="user_id">
        <el-table-column prop="user_id" label="用户 ID" width="100" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="real_name" label="姓名" width="120">
          <template #default="{ row }">{{ row.real_name || '-' }}</template>
        </el-table-column>
        <el-table-column label="手机号" width="140">
          <template #default="{ row }">{{ mobileOf(row) }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="code in row.roles || []" :key="code" size="small" effect="plain" style="margin-right: 4px">
              {{ roleLabel(code) }}
            </el-tag>
            <span v-if="!row.roles?.length" class="fl-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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
        @change="loadUsers"
      />
    </div>

    <el-dialog v-model="detailVisible" title="成员详情" width="620px">
      <el-descriptions v-if="detailTarget" :column="2" border size="small">
        <el-descriptions-item label="用户 ID">{{ detailTarget.user_id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detailTarget.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detailTarget.real_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工号">{{ detailTarget.employee_no || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ mobileOf(detailTarget) }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailTarget.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主组织 ID">{{ detailTarget.primary_org_id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="主岗位 ID">{{ detailTarget.primary_position_id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailTarget.status }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="code in detailTarget.roles || []" :key="code" size="small" effect="plain" style="margin-right: 4px">
            {{ roleLabel(code) }}
          </el-tag>
          <span v-if="!detailTarget.roles?.length" class="fl-muted">-</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role-card {
  margin-bottom: var(--fl-space-md);
}
.role-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--fl-space-sm);
  margin-bottom: var(--fl-space-sm);
}
.role-card__head h3 {
  margin: 0;
  font-size: 14px;
}
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
