<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ tenant_code: 'fenglin', username: '', password: '' })
const loading = ref(false)
const error = ref('')

// PLAT-22 忘记密码：SMTP 未配置时后端返回 UNAVAILABLE，提示联系管理员生成一次性链接
const forgotVisible = ref(false)
const forgotLoading = ref(false)
const forgotForm = reactive({ tenant_code: 'fenglin', username: '' })
const forgotResult = ref<{ channel: string; message: string } | null>(null)

function openForgot() {
  forgotForm.tenant_code = form.tenant_code || 'fenglin'
  forgotForm.username = form.username
  forgotResult.value = null
  forgotVisible.value = true
}

async function submitForgot() {
  if (!forgotForm.tenant_code || !forgotForm.username) {
    ElMessage.warning('请填写租户编码与账号')
    return
  }
  forgotLoading.value = true
  try {
    forgotResult.value = await authApi.forgotPassword({
      tenant_code: forgotForm.tenant_code.trim(),
      username: forgotForm.username.trim(),
    })
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    forgotLoading.value = false
  }
}

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
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
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
          <h1>丰麟云枢 · 员工门户</h1>
          <p>客户经营、商机推进与回款跟进的日常入口</p>
        </div>
      </div>
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 16px" />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="租户编码">
          <el-input v-model="form.tenant_code" placeholder="如 fenglin" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">登录</el-button>
        <div style="margin-top: 10px; text-align: right">
          <el-link type="primary" :underline="false" @click="openForgot">忘记密码？</el-link>
        </div>
      </el-form>
      <p class="login-tip">使用企业分配的租户账号登录；权限由后端返回，无权限的操作会提示真实错误。</p>
    </div>

    <el-dialog v-model="forgotVisible" title="找回登录口令" width="420px">
      <el-alert
        v-if="forgotResult"
        :type="forgotResult.channel === 'UNAVAILABLE' ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="forgotResult.message"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="提交后如账号存在且绑定邮箱，重置链接会发送至邮箱；未配置邮件通道时请联系管理员生成一次性链接。"
        style="margin-bottom: 12px"
      />
      <el-form label-position="top" @submit.prevent="submitForgot">
        <el-form-item label="租户编码">
          <el-input v-model="forgotForm.tenant_code" placeholder="如 fenglin" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="forgotForm.username" placeholder="用户名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="forgotVisible = false">关闭</el-button>
        <el-button type="primary" :loading="forgotLoading" @click="submitForgot">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 60%, #e0f2fe 100%);
}
.login-card {
  width: 420px;
  padding: 32px;
  background: #fff;
  border-radius: var(--fl-radius-lg, 12px);
  box-shadow: var(--fl-shadow-login);
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
  border-radius: var(--fl-radius-10);
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
  margin-top: var(--fl-space-20);
  font-size: 12px;
  color: var(--fl-color-text-secondary);
  line-height: 1.6;
}
</style>
