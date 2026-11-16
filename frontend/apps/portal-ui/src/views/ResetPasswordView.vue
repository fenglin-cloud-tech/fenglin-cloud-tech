<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))
const form = reactive({ password: '', confirm: '' })
const loading = ref(false)
const done = ref(false)
const error = ref('')

async function submit() {
  if (!token.value) {
    error.value = '链接缺少一次性令牌，请重新申请'
    return
  }
  if (form.password.length < 12) {
    error.value = '新口令至少 12 位，且需包含大写、小写、数字和符号'
    return
  }
  if (form.password !== form.confirm) {
    error.value = '两次输入的新口令不一致'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await authApi.resetPassword({ token: token.value, new_password: form.password })
    done.value = true
    ElMessage.success('口令已重置，请使用新口令登录')
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="reset-page">
    <div class="reset-card">
      <h1>重置登录口令</h1>
      <p class="reset-tip">
        链接一次性使用，30 分钟内有效；提交后该账号在其他设备上的登录会立即失效。
      </p>
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 16px" />
      <template v-if="!done">
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="新口令">
            <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新口令">
            <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" @keyup.enter="submit" />
          </el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">提交</el-button>
        </el-form>
      </template>
      <el-result v-else icon="success" title="口令已重置" sub-title="请使用新口令重新登录">
        <template #extra>
          <el-button type="primary" @click="router.replace('/login')">返回登录</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<style scoped>
.reset-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 60%, #e0f2fe 100%);
}
.reset-card {
  width: 440px;
  padding: 32px;
  background: #fff;
  border-radius: var(--fl-radius-lg, 12px);
  box-shadow: var(--fl-shadow-login);
}
h1 {
  margin: 0 0 8px;
  font-size: 18px;
}
.reset-tip {
  margin: 0 0 20px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--fl-color-text-secondary);
}
</style>
