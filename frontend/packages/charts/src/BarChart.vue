<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    data: Array<{ label: string; value: number }>
    height?: number
    color?: string
    emptyText?: string
  }>(),
  { height: 180, color: '#1F5EFF', emptyText: '暂无数据' },
)

const maxValue = computed(() => Math.max(1, ...props.data.map((item) => item.value)))
const barWidth = computed(() => {
  if (props.data.length === 0) return 0
  return Math.min(48, 640 / props.data.length - 12)
})
</script>

<template>
  <div class="fl-bar-chart" :style="{ height: `${height}px` }">
    <div v-if="data.length === 0" class="fl-bar-chart__empty">
      <span class="fl-bar-chart__empty-ring" aria-hidden="true"></span>
      <span>{{ emptyText }}</span>
    </div>
    <div v-else class="fl-bar-chart__plot">
      <div v-for="item in data" :key="item.label" class="fl-bar-chart__item">
        <span class="fl-bar-chart__value">{{ item.value }}</span>
        <div
          class="fl-bar-chart__bar"
          :style="{
            height: `${Math.max(4, (item.value / maxValue) * (height - 56))}px`,
            width: `${barWidth}px`,
            background: color,
          }"
        />
        <span class="fl-bar-chart__label" :title="item.label">{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fl-bar-chart {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.fl-bar-chart__plot {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 8px;
  width: 100%;
  height: 100%;
  padding: 0 8px;
}
.fl-bar-chart__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex: 1;
}
.fl-bar-chart__bar {
  border-radius: 4px 4px 0 0;
  transition: height 0.3s ease;
}
.fl-bar-chart__value {
  font-size: var(--fl-font-size-xs);
  color: var(--fl-color-text-secondary);
}
.fl-bar-chart__label {
  font-size: var(--fl-font-size-xs);
  color: var(--fl-color-text-secondary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fl-bar-chart__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--fl-color-text-secondary);
  font-size: var(--fl-font-size-sm);
}
/* 与表格、el-empty 保持同一套空态语言：浅蓝圆环 + 蓝点 */
.fl-bar-chart__empty-ring {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid #e0e9fb;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, rgba(31, 94, 255, 0.12) 0 38%, transparent 39% 55%, #eef3fd 56% 100%);
  box-shadow: 0 14px 28px -24px rgba(31, 94, 255, 0.55);
}
.fl-bar-chart__empty-ring::after {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #1f5eff;
}
</style>
