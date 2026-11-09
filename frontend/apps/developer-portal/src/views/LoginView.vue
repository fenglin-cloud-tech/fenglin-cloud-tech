<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ tenant_code: 'fenglin', username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!form.tenant_code || !form.username || !form.password) {
    error.value = '租户编码、账号、密码均为必填'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/apps'
    await router.replace(redirect)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="logo">丰</span>
        <div>
          <h1>开放平台 · 开发者门户</h1>
          <p>查看已接入应用、调用配额与接入方式</p>
        </div>
      </div>
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 16px" />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="租户编码">
          <el-input v-model="form.tenant_code" placeholder="如 fenglin" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">登录</el-button>
      </el-form>
      <p class="login-tip">仅展示真实接入数据；无 biz:openapi:portal:view 权限时将返回后端 403。</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #ecfeff 0%, #f8fafc 60%, #eef2ff 100%);
}
.login-card {
  width: 420px;
  padding: 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 18px 48px rgba(15, 27, 51, 0.12);
}
.login-brand {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}
.logo {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--fl-color-primary);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}
h1 {
  margin: 0;
  font-size: 18px;
}
.login-brand p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--fl-color-text-secondary);
}
.login-tip {
  margin-top: 20px;
  font-size: 12px;
  color: var(--fl-color-text-secondary);
  line-height: 1.6;
}
</style>
