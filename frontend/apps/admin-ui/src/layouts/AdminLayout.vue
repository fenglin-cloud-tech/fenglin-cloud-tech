<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => String(route.meta.title ?? '中台管理台'))
const restrictedCount = computed(
  () => adminMenu.filter((item) => item.requiredPermission && !auth.can(item.requiredPermission)).length,
)

async function handleTenantChange(value: string) {
  const id = Number(value)
  tenantStore.selectTenant(id)
  const target = tenantStore.tenants.find((item) => item.id === id)
  ElMessage.warning(
    `已切换管理视角：${target?.name ?? id}。后端未提供租户令牌换发接口，接口数据仍按登录令牌租户隔离。`,
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
})
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
      <el-scrollbar class="menu-scroll">
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
  background: var(--fl-color-bg);
}
.admin-aside {
  display: flex;
  flex-direction: column;
  background: #0f1b33;
  color: #cbd5e1;
  transition: width 0.2s ease;
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}
.brand__logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--fl-color-primary);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}
.brand__text strong {
  display: block;
  color: #fff;
  font-size: 15px;
}
.brand__text small {
  color: #94a3b8;
  font-size: 11px;
}
.menu-scroll {
  flex: 1;
}
.admin-menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cbd5e1;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #ffffff;
}
:deep(.admin-menu .el-menu-item.is-active) {
  background: var(--fl-color-primary);
}
.menu-title {
  flex: 1;
}
.menu-lock {
  margin-left: 6px;
  color: #fbbf24;
  font-size: 13px;
}
.aside-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 11px;
  color: #94a3b8;
  flex-shrink: 0;
}
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid var(--fl-color-border);
  padding: 0 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.header-title strong {
  font-size: 15px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.tenant-select {
  width: 240px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--fl-color-text-primary);
  font-size: 13px;
}
.admin-main {
  padding: 20px 24px 32px;
  overflow-y: auto;
}
.permission-block {
  margin-bottom: 24px;
}
.permission-block h3 {
  margin: 0 0 12px;
  font-size: 14px;
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
