<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    hint?: string
    tone?: 'default' | 'success' | 'warning' | 'danger'
    loading?: boolean
  }>(),
  { tone: 'default', loading: false },
)
</script>

<template>
  <div class="fl-stat-card" :data-tone="tone">
    <span class="fl-stat-card__label">{{ label }}</span>
    <strong v-if="!loading" class="fl-stat-card__value">{{ value }}</strong>
    <span v-else class="fl-stat-card__value fl-stat-card__value--loading">加载中…</span>
    <span v-if="hint" class="fl-stat-card__hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.fl-stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--fl-card-padding);
  background: var(--fl-color-surface);
  border: 1px solid #e7ecf5;
  border-radius: var(--fl-card-radius);
  box-shadow: var(--fl-card-shadow);
  min-height: 92px;
}
.fl-stat-card__label {
  color: var(--fl-color-text-secondary);
  font-size: var(--fl-font-size-sm);
}
.fl-stat-card__value {
  font-size: var(--fl-stat-value-size);
  font-weight: var(--fl-font-weight-bold);
  color: var(--fl-color-text-primary);
  line-height: 1.15;
  letter-spacing: -0.01em;
}
.fl-stat-card__value--loading {
  font-size: var(--fl-font-size-base);
  color: var(--fl-color-text-secondary);
  font-weight: normal;
}
.fl-stat-card__hint {
  font-size: var(--fl-font-size-xs);
  color: var(--fl-color-text-secondary);
}
.fl-stat-card[data-tone='success'] .fl-stat-card__value { color: var(--fl-color-success); }
.fl-stat-card[data-tone='warning'] .fl-stat-card__value { color: var(--fl-color-warning); }
.fl-stat-card[data-tone='danger'] .fl-stat-card__value { color: var(--fl-color-danger); }
</style>
