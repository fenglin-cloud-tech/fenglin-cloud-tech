<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  module?: string
}>()
</script>

<template>
  <div class="fl-page-header">
    <div class="fl-page-header__main">
      <div class="fl-page-header__tags">
        <span v-if="module" class="fl-page-header__module">{{ module }}</span>
        <slot name="tags" />
      </div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="fl-page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.fl-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--fl-space-lg);
  margin-bottom: var(--fl-space-lg);
}
.fl-page-header__tags {
  display: flex;
  align-items: center;
  gap: var(--fl-space-sm);
  margin-bottom: var(--fl-space-xs);
}
.fl-page-header__module {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(31, 94, 255, 0.1);
  color: var(--fl-color-primary);
  font-size: var(--fl-font-size-xs);
  font-weight: var(--fl-font-weight-medium);
}
h1 {
  margin: 0;
  font-size: var(--fl-font-size-xl);
  font-weight: var(--fl-font-weight-bold);
  color: var(--fl-color-text-primary);
}
p {
  margin: var(--fl-space-xs) 0 0;
  color: var(--fl-color-text-secondary);
  font-size: var(--fl-font-size-base);
}
.fl-page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--fl-space-sm);
  flex-wrap: wrap;
}
</style>
