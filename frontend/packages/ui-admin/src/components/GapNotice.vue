<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    items: string[]
    type?: 'warning' | 'info' | 'error'
  }>(),
  { title: '契约缺口与实现边界', type: 'warning' },
)
</script>

<template>
  <el-alert class="fl-gap-notice" :type="type" :closable="false" show-icon>
    <template #title>
      <strong>{{ title }}</strong>
    </template>
    <ul>
      <li v-for="item in items" :key="item">{{ item }}</li>
    </ul>
  </el-alert>
</template>

<style scoped>
.fl-gap-notice {
  margin-bottom: var(--fl-space-md);
  align-items: flex-start;
}
.fl-gap-notice ul {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.7;
}
</style>
