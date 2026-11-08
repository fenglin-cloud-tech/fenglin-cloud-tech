<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import {
  iamApi,
  type CreateRoleRequest,
  type CreateUserRequest,
  type MenuNode,
  type PermissionNode,
  type RoleView,
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
    userFormError.value = '姓名、用户名、手机号、主组织 ID 为必填项'
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

onMounted(async () => {
  await Promise.allSettled([loadUsers(), loadRoles(), loadPermissions(), loadMenus()])
})
</script>

<template>
  <div>
    <PageHeader module="IAM · A-09~A-15" title="身份与权限" description="用户、角色、权限码与菜单；组织、岗位、数据权限、字段权限接口缺失。">
      <template #actions>
        <el-button @click="loadUsers">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        'A-09 组织架构、A-11 岗位、A-13 数据权限、A-14 字段权限：现有后端未提供对应 REST 接口，本页不伪造。',
        '创建用户要求 primary_org_id，但组织列表接口缺失，当前仅能手填组织 ID（真实后端校验）。',
        '后端菜单种子仅 5 条且 parent_code 指向未创建节点，路径与 UIUX 26 页清单不一致；管理台以静态 13 页路由为准。',
        '当前权限：创建用户=' + (canCreateUser ? '允许' : '拒绝') + '，角色管理=' + (canManageRole ? '允许' : '拒绝') + '，分配角色=' + (canAssignRole ? '允许' : '拒绝') + '。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="用户" name="users">
          <div class="fl-toolbar">
            <el-input v-model="userQuery.keyword" placeholder="用户名/姓名关键词" clearable style="width: 220px" @keyup.enter="loadUsers" />
            <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 150px">
              <el-option label="ACTIVE" value="ACTIVE" />
              <el-option label="DISABLED" value="DISABLED" />
            </el-select>
            <el-button type="primary" plain @click="userQuery.page = 1; loadUsers()">查询</el-button>
            <el-button v-perm="'tenant:iam:user:create'" type="primary" @click="openCreateUser">新增用户</el-button>
          </div>
          <el-alert v-if="userError" type="error" :closable="false" show-icon :title="userError" style="margin-bottom: 12px" />
          <el-table :data="users" v-loading="userLoading" border stripe>
            <el-table-column prop="user_id" label="用户 ID" width="90" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="real_name" label="姓名" width="120" />
            <el-table-column prop="employee_no" label="工号" width="120" />
            <el-table-column label="主组织 ID" width="110">
              <template #default="{ row }">{{ row.primary_org_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="角色" min-width="160">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles || []" :key="role" size="small" effect="plain" style="margin-right: 4px">{{ role }}</el-tag>
                <span v-if="!row.roles?.length" class="fl-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
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
        <el-form-item label="主组织 ID" required>
          <el-input-number v-model="userForm.primary_org_id" :min="1" />
          <div class="fl-muted">组织列表接口缺失，请填写已存在的组织 ID</div>
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
