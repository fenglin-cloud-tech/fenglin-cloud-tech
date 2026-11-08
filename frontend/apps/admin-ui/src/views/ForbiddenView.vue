<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="forbidden">
    <el-result icon="warning" title="403 · 无访问权限" sub-title="当前账号缺少访问该页面所需的权限码，请联系平台管理员授权。">
      <template #extra>
        <el-button type="primary" @click="router.replace('/overview')">返回运营概览</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden {
  display: grid;
  place-items: center;
  min-height: 60vh;
  background: #fff;
  border-radius: var(--fl-radius-md);
}
</style>
