<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => (route.path.startsWith('/apps') ? '/apps' : route.path))

async function handleLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}
</script>

<template>
  <el-container class="dev-layout">
    <el-header class="fl-dev-header">
      <div class="brand">
        <span class="brand__logo">丰</span>
        <div>
          <strong class="fl-dev-brand">丰麟云枢 · 开放平台</strong>
          <div class="brand__sub">开发者门户</div>
        </div>
      </div>
      <el-menu :default-active="activeMenu" mode="horizontal" router class="dev-menu" :ellipsis="false">
        <el-menu-item index="/apps">我的应用</el-menu-item>
        <el-menu-item index="/logs">调用日志</el-menu-item>
        <el-menu-item index="/quotas">配额与用量</el-menu-item>
        <el-menu-item index="/docs">接口文档</el-menu-item>
        <el-menu-item index="/guide">接入指引</el-menu-item>
      </el-menu>
      <div class="header-right">
        <span class="user">{{ auth.displayName }}</span>
        <el-button text @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>
    <el-main class="fl-dev-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.dev-layout {
  min-height: 100vh;
  background: var(--fl-color-bg);
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
  border-radius: 11px;
  background: linear-gradient(135deg, #1f5eff, #3d78ff);
  box-shadow: 0 8px 18px -10px rgba(31, 94, 255, 0.7);
  color: #fff;
  font-weight: 700;
}
.brand__sub {
  font-size: 11px;
  color: var(--fl-color-text-tertiary);
}
.dev-menu {
  flex: 1;
  min-width: 0;
  border-bottom: none;
  background: transparent;
}
:deep(.dev-menu.el-menu--horizontal) {
  border-bottom: none;
}
:deep(.dev-menu .el-menu-item) {
  height: 36px;
  line-height: 36px;
  margin: 0 2px;
  padding: 0 12px;
  border-radius: 10px;
  border-bottom: none !important;
  color: #55637a;
  font-size: 13px;
  transition:
    background 0.22s ease,
    color 0.22s ease;
}
:deep(.dev-menu .el-menu-item:hover) {
  background: #f2f6fd;
  color: #17202e;
}
:deep(.dev-menu .el-menu-item.is-active) {
  background: #e9efff;
  color: #1f5eff;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  flex-shrink: 0;
}
.user {
  color: var(--fl-color-text-secondary);
}
</style>
