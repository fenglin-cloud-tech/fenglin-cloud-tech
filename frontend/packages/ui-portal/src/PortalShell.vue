<script setup lang="ts">
defineProps<{ title?: string; subtitle?: string }>()
</script>

<template>
  <div class="fl-portal-shell">
    <header class="fl-portal-shell__header">
      <div class="fl-portal-shell__brand">
        <span class="fl-portal-shell__logo">丰</span>
        <div>
          <strong>{{ title ?? '丰麟云枢' }}</strong>
          <small>{{ subtitle ?? '统一用户门户' }}</small>
        </div>
      </div>
      <slot name="nav" />
    </header>
    <main class="fl-portal-shell__main">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.fl-portal-shell {
  min-height: 100vh;
  background: var(--fl-color-bg, #f5f7fa);
}
.fl-portal-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 var(--fl-space-lg, 24px);
  background: #fff;
  border-bottom: 1px solid var(--fl-color-border, #e2e8f0);
}
.fl-portal-shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.fl-portal-shell__logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--fl-color-primary, #1f5eff);
  color: #fff;
  font-weight: 600;
}
.fl-portal-shell__brand strong {
  display: block;
  color: var(--fl-color-text-primary, #1e293b);
}
.fl-portal-shell__brand small {
  color: var(--fl-color-text-secondary, #64748b);
  font-size: 12px;
}
.fl-portal-shell__main {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--fl-space-lg, 24px);
}
</style>
