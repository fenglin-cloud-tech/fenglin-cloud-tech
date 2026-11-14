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
  padding: 3px 10px;
  border-radius: 999px;
  background: #e9efff;
  color: var(--fl-color-primary);
  font-size: var(--fl-font-size-xs);
  font-weight: var(--fl-font-weight-medium);
}
h1 {
  margin: 0;
  font-family: var(--fl-font-family);
  font-size: var(--fl-page-title-size);
  font-weight: var(--fl-page-title-weight);
  line-height: 1.2;
  letter-spacing: 0.01em;
  color: var(--fl-color-text-primary);
}
p {
  max-width: 760px;
  margin: var(--fl-space-xs) 0 0;
  color: var(--fl-color-text-secondary);
  font-size: var(--fl-font-size-sm);
  line-height: 1.65;
}
.fl-page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--fl-space-sm);
  flex-wrap: wrap;
}
</style>
