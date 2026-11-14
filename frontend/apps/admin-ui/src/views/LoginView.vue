<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, OfficeBuilding, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({
  tenant_code: 'fenglin',
  username: 'admin',
  password: '',
})
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!form.tenant_code || !form.username || !form.password) {
    error.value = '请输入租户编码、用户名与密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.login({ ...form, client_type: 'ADMIN' })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
    await router.replace(redirect)
  } catch (err) {
    error.value = (err as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="login-hero__brand">
        <span class="login-logo">丰</span>
        <span>丰麟云枢 · 中台管理台</span>
      </div>
      <div class="login-hero__content">
        <h1>一个入口，管理 13 个中台能力</h1>
        <p>租户、身份权限、流程、消息、文件、审计、搜索、开放平台、报表、低代码、AI 与网关治理。</p>
        <ul>
          <li>统一登录与令牌刷新（对接 auth 服务真实接口）</li>
          <li>租户上下文、菜单与按钮级权限码收敛</li>
          <li>管理端接口经网关 18080 转发，契约缺口页面内显式标注</li>
        </ul>
      </div>
      <div class="login-hero__footer">丰麟云枢 · 0 到 1 建设方案 P3 前端</div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <h2>管理端登录</h2>
        <p class="fl-muted">使用平台/租户管理员账号登录，客户端类型固定为 ADMIN。</p>

        <el-alert v-if="error" type="error" :closable="false" show-icon class="login-error" :title="error" />

        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="租户编码">
            <el-input v-model="form.tenant_code" :prefix-icon="OfficeBuilding" placeholder="例如 fenglin" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="form.username" :prefix-icon="User" placeholder="管理员用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :prefix-icon="Lock"
              placeholder="登录密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" class="login-submit" :loading="loading" @click="submit">登录</el-button>
        </el-form>

        <div class="login-note">
          <strong>接口说明</strong>
          <span>POST /api/hub/v1/sso/login → access_token / refresh_token</span>
          <span>GET /api/hub/v1/sso/userinfo → permissions / tenant_id</span>
          <span>GET /api/hub/v1/admin/menus?clientType=ADMIN → 菜单（可能不可用）</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  min-height: 100vh;
  background: var(--fl-color-bg);
}
.login-hero {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  color: var(--fl-color-text-primary);
  background:
    radial-gradient(circle at 16% 14%, rgba(31, 94, 255, 0.18), transparent 42%),
    radial-gradient(circle at 84% 80%, rgba(14, 165, 233, 0.14), transparent 46%),
    linear-gradient(152deg, #f8fbff 0%, #edf3fe 100%);
}
.login-hero__brand {
  display: flex;
  align-items: center;
  gap: var(--fl-space-md);
  font-weight: 600;
  letter-spacing: 0.5px;
}
.login-logo {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--fl-radius-10);
  background: var(--fl-color-primary);
  color: #fff;
  font-weight: 700;
}
.login-hero__content {
  max-width: 520px;
}
.login-hero__content h1 {
  margin: 0 0 16px;
  font-size: 34px;
  line-height: 1.3;
  color: var(--fl-color-text-primary);
}
.login-hero__content p {
  margin: 0 0 24px;
  color: var(--fl-color-text-secondary);
  line-height: 1.8;
}
.login-hero__content ul {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  color: #3b4759;
}
.login-hero__content li::marker {
  color: var(--fl-color-primary);
}
.login-hero__footer {
  font-size: 12px;
  color: var(--fl-color-text-tertiary);
}
.login-panel {
  display: grid;
  place-items: center;
  padding: 32px;
  background: transparent;
}
.login-card {
  width: 100%;
  max-width: 420px;
  padding: 32px;
  background: var(--fl-color-surface);
  border: 1px solid var(--fl-color-line-soft);
  border-radius: var(--fl-radius-lg);
  box-shadow: var(--fl-shadow-md);
}
.login-card h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.login-error {
  margin: 16px 0 8px;
}
.login-submit {
  width: 100%;
  margin-top: 8px;
}
.login-note {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px dashed var(--fl-color-border);
  font-size: 11px;
  color: var(--fl-color-text-secondary);
  word-break: break-all;
}
.login-note strong {
  color: var(--fl-color-text-primary);
  margin-bottom: 2px;
}
@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .login-hero {
    display: none;
  }
}
</style>
