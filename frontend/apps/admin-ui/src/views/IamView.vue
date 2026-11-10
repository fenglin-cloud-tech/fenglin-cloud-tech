<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import {
  iamApi,
  type CreateRoleRequest,
  type CreateUserRequest,
  type MenuNode,
  type OrgNode,
  type PermissionNode,
  type RoleView,
  type UpdateUserRequest,
  type UserView,
} from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const activeTab = ref<'users' | 'roles' | 'permissions' | 'menus'>('users')

const users = ref<UserView[]>([])
const userTotal = ref(0)
const userLoading = ref(false)
const userError = ref('')
const userQuery = reactive({ keyword: '', status: '', page: 1, size: 10 })

const roles = ref<RoleView[]>([])
const roleLoading = ref(false)
const roleError = ref('')

const permissionGroups = ref<Record<string, PermissionNode[]>>({})
const permissionLoading = ref(false)
const permissionError = ref('')

const menus = ref<MenuNode[]>([])
const menuLoading = ref(false)
const menuError = ref('')

const userDialog = ref(false)
const userSaving = ref(false)
const userFormError = ref('')
const createdUser = ref<{ user_id: number; employee_no?: string; status: string; initial_password?: string } | null>(null)
const userForm = reactive<CreateUserRequest>({
  real_name: '',
  username: '',
  mobile: '',
  email: '',
  employee_no: '',
  primary_org_id: 0,
  role_ids: [],
  password: '',
})

const assignVisible = ref(false)
const assignSaving = ref(false)
const assignTarget = ref<UserView | null>(null)
const assignRoleIds = ref<number[]>([])

const orgs = ref<OrgNode[]>([])
const orgLoading = ref(false)
const orgError = ref('')
const orgFlat = computed(() => {
  const list: Array<{ id: string | number; label: string }> = []
  const walk = (nodes: OrgNode[], depth: number) => {
    nodes.forEach((node) => {
      list.push({ id: node.id, label: `${'　'.repeat(depth)}${node.name}` })
      if (node.children?.length) walk(node.children, depth + 1)
    })
  }
  walk(orgs.value, 0)
  return list
})

function orgName(id?: number) {
  if (!id) return '-'
  return orgFlat.value.find((item) => item.id === id)?.label.trim() ?? String(id)
}

const orgDialogVisible = ref(false)
const orgDialogMode = ref<'create' | 'rename' | 'move'>('create')
const orgDialogTitle = computed(() => {
  if (orgDialogMode.value === 'rename') return '重命名组织'
  if (orgDialogMode.value === 'move') return '移动组织'
  return '新增组织'
})
const orgSaving = ref(false)
const orgFormError = ref('')
const orgTarget = ref<OrgNode | null>(null)
const orgForm = reactive({ parent_id: 0, name: '', org_type: 'DEPT', sort: 0, leader_id: 0 })
const orgTypeOptions = [
  { value: 'COMPANY', label: '公司' },
  { value: 'DEPT', label: '部门' },
  { value: 'TEAM', label: '小组' },
]

const userEditVisible = ref(false)
const userEditSaving = ref(false)
const userEditError = ref('')
const userEditTarget = ref<UserView | null>(null)
const userEditForm = reactive({
  nickname: '',
  mobile: '',
  email: '',
  employee_no: '',
  status: 'ACTIVE',
  primary_org_id: 0,
})

const roleDialog = ref(false)
const roleSaving = ref(false)
const roleFormError = ref('')
const roleForm = reactive<CreateRoleRequest>({ code: '', name: '', role_type: 'TENANT', data_scope: 'SELF' })

const grantVisible = ref(false)
const grantSaving = ref(false)
const grantTarget = ref<RoleView | null>(null)
const selectedCodes = ref<string[]>([])

const canCreateUser = computed(() => auth.can('tenant:iam:user:create'))
const canManageRole = computed(() => auth.can('tenant:iam:role:manage'))
const canAssignRole = computed(() => auth.can('tenant:iam:role:assign'))
const canManageOrg = computed(() => auth.can('tenant:iam:org:manage'))
const canUpdateUser = computed(() => auth.can('tenant:iam:user:update'))

const allPermissions = computed(() =>
  Object.entries(permissionGroups.value).flatMap(([domain, nodes]) => {
    const flat: Array<{ domain: string; code: string; name: string; type: string }> = []
    const walk = (items: PermissionNode[]) => {
      items.forEach((node) => {
        flat.push({ domain, code: node.code, name: node.name, type: node.type })
        if (node.children?.length) walk(node.children)
      })
    }
    walk(nodes)
    return flat
  }),
)

async function loadUsers() {
  userLoading.value = true
  userError.value = ''
  try {
    const result = await iamApi.listUsers(userQuery)
    users.value = result.records
    userTotal.value = result.total
  } catch (err) {
    userError.value = describeError(err)
  } finally {
    userLoading.value = false
  }
}

async function loadOrgs() {
  orgLoading.value = true
  orgError.value = ''
  try {
    orgs.value = await iamApi.orgTree()
  } catch (err) {
    orgError.value = describeError(err)
  } finally {
    orgLoading.value = false
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

async function loadPermissions() {
  permissionLoading.value = true
  permissionError.value = ''
  try {
    permissionGroups.value = await iamApi.permissionTree()
  } catch (err) {
    permissionError.value = describeError(err)
  } finally {
    permissionLoading.value = false
  }
}

async function loadMenus() {
  menuLoading.value = true
  menuError.value = ''
  try {
    menus.value = await iamApi.menus('ADMIN')
  } catch (err) {
    menuError.value = describeError(err)
  } finally {
    menuLoading.value = false
  }
}

function openCreateUser() {
  Object.assign(userForm, {
    real_name: '',
    username: '',
    mobile: '',
    email: '',
    employee_no: '',
    primary_org_id: 0,
    role_ids: [],
    password: '',
  })
  createdUser.value = null
  userFormError.value = ''
  userDialog.value = true
}

async function submitUser() {
  if (!userForm.real_name || !userForm.username || !userForm.mobile || !userForm.primary_org_id) {
    userFormError.value = '姓名、用户名、手机号、主组织为必填项'
    return
  }
  userSaving.value = true
  userFormError.value = ''
  try {
    createdUser.value = await iamApi.createUser({
      ...userForm,
      employee_no: userForm.employee_no || undefined,
      email: userForm.email || undefined,
      password: userForm.password || undefined,
      primary_position_id: userForm.primary_position_id || undefined,
    })
    ElMessage.success('用户创建成功')
    await loadUsers()
  } catch (err) {
    userFormError.value = describeError(err)
  } finally {
    userSaving.value = false
  }
}

function openCreateOrg(parent: OrgNode | null) {
  if (!canManageOrg.value) return
  orgDialogMode.value = 'create'
  orgTarget.value = parent
  Object.assign(orgForm, {
    parent_id: parent?.id ?? 0,
    name: '',
    org_type: 'DEPT',
    sort: 0,
    leader_id: 0,
  })
  orgFormError.value = ''
  orgDialogVisible.value = true
}

function openRenameOrg(node: OrgNode) {
  if (!canManageOrg.value) return
  orgDialogMode.value = 'rename'
  orgTarget.value = node
  Object.assign(orgForm, { parent_id: node.parent_id ?? 0, name: node.name, org_type: node.org_type, sort: node.sort, leader_id: node.leader_id ?? 0 })
  orgFormError.value = ''
  orgDialogVisible.value = true
}

function openMoveOrg(node: OrgNode) {
  if (!canManageOrg.value) return
  orgDialogMode.value = 'move'
  orgTarget.value = node
  Object.assign(orgForm, { parent_id: node.parent_id ?? 0, name: node.name, org_type: node.org_type, sort: node.sort, leader_id: node.leader_id ?? 0 })
  orgFormError.value = ''
  orgDialogVisible.value = true
}

async function submitOrg() {
  if (!canManageOrg.value) return
  if (orgDialogMode.value !== 'move' && !orgForm.name.trim()) {
    orgFormError.value = '组织名称为必填项'
    return
  }
  orgSaving.value = true
  orgFormError.value = ''
  try {
    if (orgDialogMode.value === 'create') {
      await iamApi.createOrg({
        parent_id: orgForm.parent_id || null,
        name: orgForm.name.trim(),
        org_type: orgForm.org_type,
        sort: orgForm.sort,
        leader_id: orgForm.leader_id || undefined,
      })
      ElMessage.success('组织已创建')
    } else if (orgDialogMode.value === 'rename' && orgTarget.value) {
      await iamApi.updateOrg(orgTarget.value.id, { name: orgForm.name.trim() })
      ElMessage.success('组织已重命名')
    } else if (orgDialogMode.value === 'move' && orgTarget.value) {
      await iamApi.moveOrg(orgTarget.value.id, { parent_id: orgForm.parent_id || null })
      ElMessage.success('组织已移动')
    }
    orgDialogVisible.value = false
    await loadOrgs()
  } catch (err) {
    orgFormError.value = describeError(err)
  } finally {
    orgSaving.value = false
  }
}

async function disableOrg(node: OrgNode) {
  if (!canManageOrg.value) return
  try {
    await ElMessageBox.confirm(`确认停用组织「${node.name}」？`, '停用组织', { type: 'warning' })
  } catch {
    return
  }
  try {
    await iamApi.updateOrg(node.id, { status: 'DISABLED' })
    ElMessage.success('组织已停用')
    await loadOrgs()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function deleteOrg(node: OrgNode) {
  if (!canManageOrg.value) return
  try {
    await ElMessageBox.confirm(`确认删除组织「${node.name}」？删除后不可恢复。`, '删除组织', { type: 'warning' })
  } catch {
    return
  }
  try {
    await iamApi.deleteOrg(node.id)
    ElMessage.success('组织已删除')
    await loadOrgs()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function handleOrgCommand(command: unknown, node: OrgNode) {
  switch (command) {
    case 'create':
      openCreateOrg(node)
      break
    case 'rename':
      openRenameOrg(node)
      break
    case 'move':
      openMoveOrg(node)
      break
    case 'disable':
      void disableOrg(node)
      break
    case 'delete':
      void deleteOrg(node)
      break
  }
}

function openEditUser(row: UserView) {
  if (!canUpdateUser.value) return
  userEditTarget.value = row
  Object.assign(userEditForm, {
    nickname: row.nickname ?? row.real_name ?? '',
    mobile: '',
    email: '',
    employee_no: row.employee_no ?? '',
    status: row.status || 'ACTIVE',
    primary_org_id: row.primary_org_id ?? 0,
  })
  userEditError.value = ''
  userEditVisible.value = true
}

async function submitEditUser() {
  if (!canUpdateUser.value || !userEditTarget.value) return
  const body: UpdateUserRequest = {}
  if (userEditForm.nickname.trim()) body.nickname = userEditForm.nickname.trim()
  if (userEditForm.mobile.trim()) body.mobile = userEditForm.mobile.trim()
  if (userEditForm.email.trim()) body.email = userEditForm.email.trim()
  if (userEditForm.employee_no.trim()) body.employee_no = userEditForm.employee_no.trim()
  if (userEditForm.status) body.status = userEditForm.status
  if (userEditForm.primary_org_id) body.primary_org_id = userEditForm.primary_org_id
  if (userEditTarget.value.version !== undefined) body.version = userEditTarget.value.version
  userEditSaving.value = true
  userEditError.value = ''
  try {
    await iamApi.updateUser(userEditTarget.value.user_id, body)
    ElMessage.success('用户资料已更新')
    userEditVisible.value = false
    await loadUsers()
  } catch (err) {
    userEditError.value = describeError(err)
  } finally {
    userEditSaving.value = false
  }
}

function openAssign(row: UserView) {
  assignTarget.value = row
  const matched = roles.value.filter((role) => row.roles?.includes(role.code))
  assignRoleIds.value = matched.map((role) => role.role_id)
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignTarget.value) return
  assignSaving.value = true
  try {
    await iamApi.assignRoles(assignTarget.value.user_id, assignRoleIds.value)
    ElMessage.success('角色分配成功')
    assignVisible.value = false
    await loadUsers()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    assignSaving.value = false
  }
}

function openCreateRole() {
  Object.assign(roleForm, { code: '', name: '', role_type: 'TENANT', data_scope: 'SELF' })
  roleFormError.value = ''
  roleDialog.value = true
}

async function submitRole() {
  if (!roleForm.code || !roleForm.name) {
    roleFormError.value = '角色编码与名称为必填项'
    return
  }
  roleSaving.value = true
  roleFormError.value = ''
  try {
    await iamApi.createRole(roleForm)
    ElMessage.success('角色创建成功')
    roleDialog.value = false
    await loadRoles()
  } catch (err) {
    roleFormError.value = describeError(err)
  } finally {
    roleSaving.value = false
  }
}

async function openGrant(role: RoleView) {
  grantTarget.value = role
  if (allPermissions.value.length === 0) await loadPermissions()
  selectedCodes.value = [...(role.permission_codes ?? [])]
  grantVisible.value = true
}

async function submitGrant() {
  if (!grantTarget.value) return
  grantSaving.value = true
  try {
    await iamApi.grantPermissions(grantTarget.value.role_id, selectedCodes.value)
    ElMessage.success('权限授予成功')
    grantVisible.value = false
    await loadRoles()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    grantSaving.value = false
  }
}

async function refreshAll() {
  await Promise.allSettled([loadOrgs(), loadUsers()])
}

onMounted(async () => {
  await Promise.allSettled([loadOrgs(), loadUsers(), loadRoles(), loadPermissions(), loadMenus()])
})
</script>

<template>
  <div>
    <PageHeader module="IAM · A-09~A-15" title="身份与权限" description="用户、角色、权限码与菜单；支持组织树维护与用户资料编辑。">
      <template #actions>
        <el-button :loading="orgLoading || userLoading" @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        'A-11 岗位、A-13 数据权限、A-14 字段权限：现有后端未提供对应 REST 接口，本页不伪造。',
        '组织成员挂载接口已接入 API 客户端，但缺少成员列表接口，本页暂不提供成员管理界面。',
        '后端菜单种子仅 5 条且 parent_code 指向未创建节点，路径与 UIUX 26 页清单不一致；管理台以静态 13 页路由为准。',
        '当前权限：创建用户=' + (canCreateUser ? '允许' : '拒绝') + '，用户资料编辑=' + (canUpdateUser ? '允许' : '拒绝') + '，组织管理=' + (canManageOrg ? '允许' : '拒绝') + '，角色管理=' + (canManageRole ? '允许' : '拒绝') + '，分配角色=' + (canAssignRole ? '允许' : '拒绝') + '。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="用户" name="users">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-card shadow="never" v-loading="orgLoading">
                <template #header>
                  <div class="org-card-header">
                    <span>组织架构</span>
                    <div>
                      <el-button v-perm="'tenant:iam:org:manage'" type="primary" plain size="small" @click="openCreateOrg(null)">新增根组织</el-button>
                      <el-button size="small" @click="loadOrgs">刷新</el-button>
                    </div>
                  </div>
                </template>
                <el-alert v-if="orgError" type="error" :closable="false" show-icon :title="orgError" style="margin-bottom: 12px" />
                <el-tree
                  :data="orgs"
                  node-key="id"
                  :props="{ label: 'name', children: 'children' }"
                  default-expand-all
                  highlight-current
                  :expand-on-click-node="false"
                >
                  <template #default="{ data }">
                    <div class="org-node">
                      <span class="org-node-name">{{ data.name }}</span>
                      <el-tag size="small" effect="plain" :type="data.status === 'DISABLED' ? 'info' : 'success'">{{ data.member_count ?? 0 }} 人</el-tag>
                      <el-dropdown v-if="canManageOrg" trigger="click" @command="handleOrgCommand($event, data)">
                        <el-button link type="primary" size="small">操作</el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="create">新增子组织</el-dropdown-item>
                            <el-dropdown-item command="rename">重命名</el-dropdown-item>
                            <el-dropdown-item command="move">移动</el-dropdown-item>
                            <el-dropdown-item command="disable" :disabled="data.status === 'DISABLED'">停用</el-dropdown-item>
                            <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </template>
                </el-tree>
                <el-empty v-if="!orgLoading && orgs.length === 0" description="暂无组织节点" />
              </el-card>
            </el-col>
            <el-col :span="16">
              <div class="fl-toolbar">
                <el-input v-model="userQuery.keyword" placeholder="用户名/姓名关键词" clearable style="width: 190px" @keyup.enter="loadUsers" />
                <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 130px">
                  <el-option label="ACTIVE" value="ACTIVE" />
                  <el-option label="DISABLED" value="DISABLED" />
                </el-select>
                <el-button type="primary" plain @click="userQuery.page = 1; loadUsers()">查询</el-button>
                <el-button v-perm="'tenant:iam:user:create'" type="primary" @click="openCreateUser">新增用户</el-button>
              </div>
              <el-alert v-if="userError" type="error" :closable="false" show-icon :title="userError" style="margin-bottom: 12px" />
              <el-table :data="users" v-loading="userLoading" border stripe>
                <el-table-column prop="user_id" label="用户 ID" width="90" />
                <el-table-column prop="username" label="用户名" width="130" />
                <el-table-column prop="real_name" label="姓名" width="110" />
                <el-table-column prop="employee_no" label="工号" width="110" />
                <el-table-column label="主组织" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ orgName(row.primary_org_id) }}</template>
                </el-table-column>
                <el-table-column label="角色" min-width="150">
                  <template #default="{ row }">
                    <el-tag v-for="role in row.roles || []" :key="role" size="small" effect="plain" style="margin-right: 4px">{{ role }}</el-tag>
                    <span v-if="!row.roles?.length" class="fl-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" />
                <el-table-column label="操作" width="170" fixed="right">
                  <template #default="{ row }">
                    <el-button v-perm="'tenant:iam:user:update'" link type="primary" @click="openEditUser(row)">编辑资料</el-button>
                    <el-button v-perm="'tenant:iam:role:assign'" link type="primary" @click="openAssign(row)">分配角色</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                v-model:current-page="userQuery.page"
                v-model:page-size="userQuery.size"
                :total="userTotal"
                layout="total, prev, pager, next"
                style="margin-top: 16px; justify-content: flex-end"
                @change="loadUsers"
              />
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="角色" name="roles">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:iam:role:manage'" type="primary" @click="openCreateRole">新增角色</el-button>
            <el-button :loading="roleLoading" @click="loadRoles">刷新</el-button>
          </div>
          <el-alert v-if="roleError" type="error" :closable="false" show-icon :title="roleError" style="margin-bottom: 12px" />
          <el-table :data="roles" v-loading="roleLoading" border stripe>
            <el-table-column prop="role_id" label="角色 ID" width="90" />
            <el-table-column prop="code" label="编码" width="180" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="role_type" label="类型" width="110" />
            <el-table-column prop="data_scope" label="数据范围" width="110" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="权限数" width="90">
              <template #default="{ row }">{{ row.permission_codes?.length ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'tenant:iam:role:manage'" link type="primary" @click="openGrant(row)">授予权限</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="权限树" name="permissions">
          <el-alert v-if="permissionError" type="error" :closable="false" show-icon :title="permissionError" style="margin-bottom: 12px" />
          <div v-loading="permissionLoading">
            <el-collapse v-if="Object.keys(permissionGroups).length">
              <el-collapse-item v-for="(nodes, domain) in permissionGroups" :key="domain" :title="`${domain}（${nodes.length} 个根节点）`" :name="domain">
                <el-tree :data="nodes" node-key="code" :props="{ label: 'name', children: 'children' }" default-expand-all>
                  <template #default="{ data }">
                    <span>{{ data.name }}</span>
                    <el-tag size="small" effect="plain" style="margin-left: 8px">{{ data.code }}</el-tag>
                    <el-tag size="small" type="info" style="margin-left: 4px">{{ data.type }}</el-tag>
                  </template>
                </el-tree>
              </el-collapse-item>
            </el-collapse>
            <el-empty v-else description="无权限数据" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="菜单" name="menus">
          <el-alert v-if="menuError" type="error" :closable="false" show-icon :title="menuError" style="margin-bottom: 12px" />
          <el-table :data="menus" v-loading="menuLoading" border stripe>
            <el-table-column prop="code" label="编码" width="180" />
            <el-table-column prop="name" label="名称" width="150" />
            <el-table-column prop="path" label="路径" min-width="150" />
            <el-table-column prop="parent_code" label="父级" width="160" />
            <el-table-column prop="permission_code" label="权限码" min-width="200" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="orgDialogVisible" :title="orgDialogTitle" width="480px">
      <el-alert v-if="orgFormError" type="error" :closable="false" show-icon :title="orgFormError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item v-if="orgDialogMode === 'create'" label="上级组织">
          <el-select v-model="orgForm.parent_id" filterable style="width: 100%">
            <el-option label="（根组织）" :value="0" />
            <el-option v-for="item in orgFlat" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="orgDialogMode === 'move'" label="新上级组织">
          <el-select v-model="orgForm.parent_id" filterable style="width: 100%">
            <el-option label="（根组织）" :value="0" />
            <el-option v-for="item in orgFlat" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="orgDialogMode !== 'move'" label="组织名称" required>
          <el-input v-model="orgForm.name" maxlength="64" />
        </el-form-item>
        <template v-if="orgDialogMode === 'create'">
          <el-form-item label="组织类型">
            <el-select v-model="orgForm.org_type" style="width: 100%">
              <el-option v-for="opt in orgTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="orgForm.sort" :min="0" />
          </el-form-item>
          <el-form-item label="负责人 ID">
            <el-input-number v-model="orgForm.leader_id" :min="0" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orgSaving" @click="submitOrg">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialog" title="新增用户" width="560px">
      <el-alert v-if="userFormError" type="error" :closable="false" show-icon :title="userFormError" style="margin-bottom: 12px" />
      <el-alert v-if="createdUser" type="success" :closable="false" show-icon style="margin-bottom: 12px" title="用户已创建">
        <div>用户 ID：{{ createdUser.user_id }}，工号：{{ createdUser.employee_no || '-' }}，状态：{{ createdUser.status }}</div>
        <div v-if="createdUser.initial_password">初始密码（仅本次显示）：<strong>{{ createdUser.initial_password }}</strong></div>
      </el-alert>
      <el-form label-width="110px" :disabled="!!createdUser">
        <el-form-item label="姓名" required><el-input v-model="userForm.real_name" /></el-form-item>
        <el-form-item label="用户名" required><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="手机号" required><el-input v-model="userForm.mobile" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="工号"><el-input v-model="userForm.employee_no" placeholder="可缺省，由后端生成" /></el-form-item>
        <el-form-item label="主组织" required>
          <el-select v-model="userForm.primary_org_id" filterable style="width: 100%" placeholder="从组织树选择">
            <el-option v-for="item in orgFlat" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="userForm.password" type="password" show-password placeholder="可缺省，由平台生成并仅返回一次" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role_ids" multiple style="width: 100%" placeholder="可选">
            <el-option v-for="role in roles" :key="role.role_id" :label="`${role.name}（${role.code}）`" :value="role.role_id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">{{ createdUser ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!createdUser" type="primary" :loading="userSaving" @click="submitUser">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userEditVisible" title="编辑用户资料" width="520px">
      <el-alert v-if="userEditError" type="error" :closable="false" show-icon :title="userEditError" style="margin-bottom: 12px" />
      <div v-if="userEditTarget" class="fl-muted" style="margin-bottom: 12px">
        用户：{{ userEditTarget.username }}（{{ userEditTarget.real_name || '-' }}）
      </div>
      <el-form label-width="100px">
        <el-form-item label="昵称">
          <el-input v-model="userEditForm.nickname" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userEditForm.mobile" placeholder="留空表示不修改" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userEditForm.email" placeholder="留空表示不修改" />
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="userEditForm.employee_no" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="userEditForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="主组织">
          <el-select v-model="userEditForm.primary_org_id" filterable clearable style="width: 100%" placeholder="选择主组织">
            <el-option v-for="item in orgFlat" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="userEditSaving" @click="submitEditUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="分配角色" width="480px">
      <div v-if="assignTarget" class="fl-muted" style="margin-bottom: 12px">
        用户：{{ assignTarget.username }}（{{ assignTarget.real_name || '-' }}）
      </div>
      <el-select v-model="assignRoleIds" multiple style="width: 100%" placeholder="选择角色">
        <el-option v-for="role in roles" :key="role.role_id" :label="`${role.name}（${role.code}）`" :value="role.role_id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSaving" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="新增角色" width="480px">
      <el-alert v-if="roleFormError" type="error" :closable="false" show-icon :title="roleFormError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="角色编码" required><el-input v-model="roleForm.code" /></el-form-item>
        <el-form-item label="角色名称" required><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="角色类型">
          <el-select v-model="roleForm.role_type" style="width: 100%">
            <el-option label="TENANT" value="TENANT" />
            <el-option label="PLATFORM" value="PLATFORM" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="roleForm.data_scope" style="width: 100%">
            <el-option label="SELF" value="SELF" />
            <el-option label="ORG" value="ORG" />
            <el-option label="TENANT" value="TENANT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="submitRole">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantVisible" title="授予权限" width="640px">
      <div v-if="grantTarget" class="fl-muted" style="margin-bottom: 12px">
        角色：{{ grantTarget.name }}（{{ grantTarget.code }}）
      </div>
      <div class="grant-groups">
        <div v-for="(nodes, domain) in permissionGroups" :key="domain">
          <h4>{{ domain }}</h4>
          <el-checkbox-group v-model="selectedCodes">
            <el-checkbox v-for="item in allPermissions.filter((p) => p.domain === domain)" :key="item.code" :value="item.code">
              {{ item.name }} <span class="fl-mono">{{ item.code }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <template #footer>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button type="primary" :loading="grantSaving" @click="submitGrant">保存（{{ selectedCodes.length }} 项）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.org-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.org-node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  padding-right: 8px;
}
.org-node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.grant-groups {
  max-height: 55vh;
  overflow: auto;
}
.grant-groups h4 {
  margin: 16px 0 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--fl-color-border);
  font-size: 13px;
}
.grant-groups :deep(.el-checkbox) {
  display: flex;
  margin-right: 0;
  height: 28px;
}
</style>
