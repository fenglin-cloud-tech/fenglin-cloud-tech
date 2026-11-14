<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElScrollbar } from 'element-plus'
import {
  Aim,
  AlarmClock,
  Bell,
  Calendar,
  ChatDotRound,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  DocumentChecked,
  Expand,
  Flag,
  Fold,
  Folder,
  Grid,
  Guide,
  Key,
  Lock,
  Money,
  Odometer,
  OfficeBuilding,
  Box,
  Promotion,
  Reading,
  Search,
  Setting,
  Share,
  Stamp,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  VideoCamera,
  Warning,
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useTenantStore } from '@/stores/tenant'
import { adminMenu } from '@/config/menu'

const iconMap = {
  Aim,
  AlarmClock,
  Bell,
  Calendar,
  ChatDotRound,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  DocumentChecked,
  Flag,
  Folder,
  Grid,
  Guide,
  Money,
  Odometer,
  OfficeBuilding,
  Box,
  Promotion,
  Reading,
  Search,
  Setting,
  Share,
  Stamp,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  VideoCamera,
  Warning,
} as const

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const auth = useAuthStore()
const tenantStore = useTenantStore()
const tenantChanging = ref(false)
const menuScrollRef = ref<InstanceType<typeof ElScrollbar> | null>(null)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => String(route.meta.title ?? '中台管理台'))
const restrictedCount = computed(
  () => adminMenu.filter((item) => item.requiredPermission && !auth.can(item.requiredPermission)).length,
)

async function handleTenantChange(value: string) {
  tenantStore.selectTenant(value)
  const target = tenantStore.tenants.find((item) => item.id === value)
  ElMessage.warning(
    `已切换管理视角：${target?.name ?? value}。后端未提供租户令牌换发接口，接口数据仍按登录令牌租户隔离。`,
  )
}

async function handleLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}

async function refreshTenantContext() {
  tenantChanging.value = true
  await Promise.all([tenantStore.loadCurrent(), tenantStore.loadList()])
  tenantChanging.value = false
}

onMounted(() => {
  void refreshTenantContext()
  void scrollActiveMenuIntoView()
})

// 86 个菜单项超过一屏时，切换页面后把当前项滚回视野中部，避免“高亮了但看不见”。
async function scrollActiveMenuIntoView() {
  await nextTick()
  const scrollbar = menuScrollRef.value
  const wrap = scrollbar?.wrapRef as HTMLElement | undefined
  const active = wrap?.querySelector('.el-menu-item.is-active') as HTMLElement | null
  if (!scrollbar || !wrap || !active) return
  const target = active.offsetTop - wrap.clientHeight / 2 + active.clientHeight / 2
  scrollbar.setScrollTop(Math.max(0, target))
}

watch(
  () => route.path,
  () => {
    void scrollActiveMenuIntoView()
  },
)
</script>

<template>
  <el-container class="admin-layout">
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '236px'" class="admin-aside">
      <div class="brand">
        <span class="brand__logo">丰</span>
        <div v-if="!appStore.sidebarCollapsed" class="brand__text">
          <strong>丰麟云枢</strong>
          <small>中台管理台</small>
        </div>
      </div>
      <el-scrollbar ref="menuScrollRef" class="menu-scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          router
          class="admin-menu"
        >
          <el-menu-item v-for="item in adminMenu" :key="item.path" :index="item.path">
            <el-icon><component :is="iconMap[item.icon as keyof typeof iconMap]" /></el-icon>
            <template #title>
              <span class="menu-title">{{ item.title }}</span>
              <el-tooltip
                v-if="item.requiredPermission && !auth.can(item.requiredPermission)"
                :content="`当前账号缺少 ${item.requiredPermission}，页面动作可能返回 403`"
                placement="right"
              >
                <el-icon class="menu-lock"><Lock /></el-icon>
              </el-tooltip>
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
      <div v-if="!appStore.sidebarCollapsed" class="aside-footer">
        <span>{{ adminMenu.length }} 个管理页面</span>
        <el-tag v-if="restrictedCount > 0" size="small" type="warning">{{ restrictedCount }} 个受限</el-tag>
      </div>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button text circle :icon="appStore.sidebarCollapsed ? Expand : Fold" @click="appStore.toggleSidebar()" />
          <div class="header-title">
            <strong>{{ currentTitle }}</strong>
            <span class="fl-muted">{{ route.path }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-tag size="small" effect="plain" type="info">
            菜单源：{{ auth.menuSource === 'api' ? '后端菜单 + 静态路由' : '静态路由（后端菜单不可用）' }}
          </el-tag>
          <el-select
            :model-value="tenantStore.selectedTenantId ?? ''"
            :loading="tenantChanging"
            class="tenant-select"
            placeholder="当前租户"
            @change="handleTenantChange"
          >
            <el-option
              v-for="tenant in tenantStore.tenants"
              :key="tenant.id"
              :label="`${tenant.name}（${tenant.code}）`"
              :value="String(tenant.id)"
            />
            <el-option
              v-if="tenantStore.tenants.length === 0 && tenantStore.current"
              :label="`${tenantStore.current.name}（${tenantStore.current.code}）`"
              :value="String(tenantStore.current.id)"
            />
          </el-select>
          <el-tooltip content="查看当前令牌权限码" placement="bottom">
            <el-button text :icon="Key" @click="appStore.permissionDrawerVisible = true">
              权限 {{ auth.permissions.length }}
            </el-button>
          </el-tooltip>
          <el-dropdown trigger="click">
            <span class="user-chip">
              <el-avatar :size="26">{{ auth.displayName.slice(0, 1) }}</el-avatar>
              <span>{{ auth.displayName }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>租户：{{ auth.user?.tenant_code ?? auth.user?.tenant_id ?? '-' }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-main">
        <div v-if="tenantStore.error" class="fl-muted" style="margin-bottom: 12px">
          租户上下文加载失败：{{ tenantStore.error }}
        </div>
        <router-view />
      </el-main>
    </el-container>

    <el-drawer v-model="appStore.permissionDrawerVisible" title="当前令牌权限与菜单" size="520px">
      <div class="permission-block">
        <h3>用户信息</h3>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用户">{{ auth.user?.username }} / {{ auth.user?.real_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="租户">{{ auth.user?.tenant_code || auth.user?.tenant_id }}</el-descriptions-item>
          <el-descriptions-item label="客户端">{{ auth.user?.client_type }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ (auth.user?.roles || []).join('，') || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <div class="permission-block">
        <h3>权限码（{{ auth.permissions.length }}）</h3>
        <div class="permission-tags">
          <el-tag v-for="code in auth.permissions" :key="code" size="small" effect="plain">{{ code }}</el-tag>
          <span v-if="auth.permissions.length === 0" class="fl-muted">令牌未返回权限码</span>
        </div>
      </div>
      <div class="permission-block">
        <h3>后端菜单接口</h3>
        <el-alert v-if="auth.menuError" type="warning" :closable="false" show-icon>
          菜单接口不可用：{{ auth.menuError }}。当前使用静态路由 + 权限码过滤。
        </el-alert>
        <el-table v-else :data="auth.menus" size="small" max-height="260">
          <el-table-column prop="name" label="菜单" />
          <el-table-column prop="path" label="路径" />
          <el-table-column prop="permission_code" label="权限码" width="170" />
        </el-table>
        <p class="fl-muted">
          说明：后端种子菜单仅包含部分模块且路径与 UIUX 26 页清单不完全一致，管理台以静态 13 页路由为准，菜单接口仅用于校验与展示。
        </p>
      </div>
    </el-drawer>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
  padding: var(--fl-space-md);
  gap: var(--fl-space-md);
  background: var(--fl-color-bg);
}
.admin-layout > .el-container {
  min-width: 0;
}
.admin-aside {
  display: flex;
  flex-direction: column;
  background: var(--fl-color-surface);
  border: 1px solid #e7ecf5;
  border-radius: var(--fl-radius-20);
  box-shadow: var(--fl-shadow-float);
  color: var(--fl-color-text-primary);
  transition: width 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: var(--fl-space-10);
  height: 56px;
  padding: 0 var(--fl-space-14);
  border-bottom: 1px solid var(--fl-color-line-soft);
  flex-shrink: 0;
}
.brand__logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--fl-radius-11);
  background: linear-gradient(135deg, #1f5eff, #3d78ff);
  box-shadow: var(--fl-shadow-brand-strong);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}
.brand__text strong {
  display: block;
  color: #17202e;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.brand__text small {
  color: #8b98ac;
  font-size: 11px;
}
.menu-scroll {
  flex: 1;
  min-height: 0;
}
.admin-menu {
  border-right: none;
  background: transparent;
  padding: 6px 0 12px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #4c5a6e;
  --el-menu-hover-bg-color: #f3f6fd;
  --el-menu-active-color: #1f5eff;
}
:deep(.admin-menu .el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin: 2px 8px;
  padding: 0 var(--fl-space-10) !important;
  border-radius: var(--fl-radius-10);
  font-size: 13px;
  color: #4c5a6e;
  transition:
    background 0.22s ease,
    color 0.22s ease;
}
:deep(.admin-menu .el-menu-item .el-icon) {
  color: #95a3b8;
}
:deep(.admin-menu .el-menu-item:hover) {
  background: #f3f6fd;
  color: #17202e;
}
:deep(.admin-menu .el-menu-item.is-active) {
  background: #e9efff;
  color: #1f5eff;
  font-weight: 600;
  position: relative;
}
:deep(.admin-menu .el-menu-item.is-active .el-icon) {
  color: #1f5eff;
}
:deep(.admin-menu .el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 16px;
  border-radius: 2px;
  transform: translateY(-50%);
  background: #1f5eff;
  box-shadow: var(--fl-shadow-focus);
}
:deep(.admin-menu.el-menu--collapse .el-menu-item) {
  margin: 2px 6px;
  padding: 0 !important;
  justify-content: center;
}
:deep(.admin-menu.el-menu--collapse .el-menu-item.is-active::before) {
  display: none;
}
.menu-title {
  flex: 1;
}
.menu-lock {
  margin-left: 6px;
  color: #b57612;
  font-size: 13px;
}
.aside-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: var(--fl-space-10) var(--fl-space-14);
  border-top: 1px solid var(--fl-color-line-soft);
  font-size: 11px;
  color: #8b98ac;
  flex-shrink: 0;
}
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  margin-bottom: var(--fl-space-md);
  padding: 0 var(--fl-space-14);
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #e7ecf5;
  border-radius: var(--fl-radius-lg);
  box-shadow: var(--fl-shadow-header);
  backdrop-filter: blur(12px);
}
.header-left {
  display: flex;
  align-items: center;
  gap: var(--fl-space-10);
}
.header-title {
  display: flex;
  align-items: baseline;
  gap: var(--fl-space-10);
}
.header-title strong {
  font-size: 15px;
  font-weight: 600;
  color: #17202e;
}
.header-title .fl-muted {
  color: #8b98ac;
  font-size: 11px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: var(--fl-space-md);
  flex-wrap: wrap;
  justify-content: flex-end;
}
.tenant-select {
  width: 220px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #17202e;
  font-size: 12px;
}
.user-chip :deep(.el-avatar) {
  background: #e9efff;
  color: #1f5eff;
  font-weight: 600;
}
.admin-main {
  padding: 0 4px 28px;
  overflow-y: auto;
}
.permission-block {
  margin-bottom: var(--fl-space-20);
}
.permission-block h3 {
  margin: 0 0 var(--fl-space-10);
  font-size: 13px;
  color: #17202e;
}
.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
@media (max-width: 1100px) {
  .tenant-select {
    width: 170px;
  }
  .header-right > .el-tag {
    display: none;
  }
}
</style>
