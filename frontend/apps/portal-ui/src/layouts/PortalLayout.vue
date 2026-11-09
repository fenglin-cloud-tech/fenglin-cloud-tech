<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Aim, AlarmClock, Grid, Tickets, TrendCharts, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const permissionVisible = ref(false)

const navItems = [
  { path: '/overview', title: '我的工作台', icon: Grid, perm: '' },
  { path: '/my-leads', title: '我的线索', icon: Aim, perm: 'crm:lead:view' },
  { path: '/my-customers', title: '我的客户', icon: UserFilled, perm: 'crm:customer:view' },
  { path: '/my-opportunities', title: '我的商机', icon: TrendCharts, perm: 'crm:opportunity:view' },
  { path: '/my-quotations', title: '我的报价/合同', icon: Tickets, perm: 'crm:quotation:view' },
  { path: '/payment-reminders', title: '回款提醒', icon: AlarmClock, perm: 'crm:contract:view' },
]

// 菜单按后端权限点收敛：未授权项不展示，避免前端伪造可访问入口。
const visibleNavItems = computed(() => navItems.filter((item) => !item.perm || auth.can(item.perm)))

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => String(route.meta.title ?? '员工门户'))

async function handleLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}
</script>

<template>
  <el-container class="portal-layout">
    <el-header class="portal-header">
      <div class="brand">
        <span class="brand__logo">丰</span>
        <div class="brand__text">
          <strong>丰麟云枢</strong>
          <small>员工门户</small>
        </div>
      </div>
      <el-menu :default-active="activeMenu" mode="horizontal" router class="portal-menu">
        <el-menu-item v-for="item in visibleNavItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
      <div class="header-right">
        <el-button text @click="permissionVisible = true">权限 {{ auth.permissions.length }}</el-button>
        <el-dropdown trigger="click">
          <span class="user-chip">
            <el-avatar :size="26">{{ auth.displayName.slice(0, 1) }}</el-avatar>
            <span>{{ auth.displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>租户：{{ auth.user?.tenant_code || auth.user?.tenant_id || '-' }}</el-dropdown-item>
              <el-dropdown-item disabled>账号：{{ auth.user?.username || '-' }}</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="portal-main">
      <div class="crumb">
        <strong>{{ currentTitle }}</strong>
        <span>{{ route.path }}</span>
      </div>
      <router-view />
    </el-main>

    <el-drawer v-model="permissionVisible" title="当前账号权限" size="440px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="用户">{{ auth.user?.username }} / {{ auth.user?.real_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ auth.user?.tenant_code || auth.user?.tenant_id }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ (auth.user?.roles || []).join('，') || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="perm-tags">
        <el-tag v-for="code in auth.permissions" :key="code" size="small" effect="plain">{{ code }}</el-tag>
        <span v-if="auth.permissions.length === 0" class="fl-muted">令牌未返回权限码</span>
      </div>
      <p class="fl-muted">菜单与按钮均按后端权限码收敛；无权限时操作会返回后端真实 403 错误，前端不伪造数据。</p>
    </el-drawer>
  </el-container>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
  background: var(--fl-color-bg);
}
.portal-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid var(--fl-color-border);
  padding: 0 24px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
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
}
.brand__text strong {
  display: block;
  font-size: 15px;
}
.brand__text small {
  color: var(--fl-color-text-secondary);
  font-size: 11px;
}
.portal-menu {
  flex: 1;
  border-bottom: none;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
}
.portal-main {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 20px 24px 40px;
}
.crumb {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
}
.crumb strong {
  font-size: 18px;
}
.crumb span {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 16px 0;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
