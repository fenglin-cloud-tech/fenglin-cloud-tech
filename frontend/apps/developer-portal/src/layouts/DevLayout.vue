<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)

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
      <el-menu :default-active="activeMenu" mode="horizontal" router class="dev-menu">
        <el-menu-item index="/apps">我的应用</el-menu-item>
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
.brand__sub {
  font-size: 11px;
  color: var(--fl-color-text-secondary);
}
.dev-menu {
  flex: 1;
  border-bottom: none;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.user {
  color: var(--fl-color-text-secondary);
}
</style>
