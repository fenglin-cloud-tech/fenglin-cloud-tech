<script setup lang="ts">
import { computed } from 'vue'
import { sanitizeRichDoc, type RichBlock, type RichDoc } from '../richDoc'

const props = defineProps<{ model: RichDoc | string }>()

const doc = computed(() => sanitizeRichDoc(props.model))

function blockTag(block: RichBlock) {
  if (block.type === 'heading') return `h${Math.min(3, Math.max(1, block.level ?? 1))}`
  if (block.type === 'quote') return 'blockquote'
  if (block.type === 'code') return 'pre'
  return 'div'
}
</script>

<template>
  <div class="rich-doc">
    <component
      :is="blockTag(block)"
      v-for="(block, index) in doc.blocks"
      :key="index"
      :class="['rich-doc__block', `rich-doc__block--${block.type}`]"
    >
      <span
        v-for="(run, runIndex) in block.runs"
        :key="runIndex"
        :class="{
          'rich-doc__run--bold': run.bold,
          'rich-doc__run--italic': run.italic,
          'rich-doc__run--underline': run.underline,
        }"
        >{{ run.text }}</span
      >
    </component>
  </div>
</template>

<style scoped>
.rich-doc {
  line-height: 1.75;
  word-break: break-word;
  color: var(--fl-color-text-primary, #303133);
}
.rich-doc__block {
  margin: 0 0 8px;
}
.rich-doc__block--heading {
  margin: 14px 0 8px;
  font-weight: 600;
}
.rich-doc__block--bullet {
  padding-left: 20px;
  position: relative;
}
.rich-doc__block--bullet::before {
  content: '•';
  position: absolute;
  left: 6px;
  color: var(--fl-color-text-secondary, #909399);
}
.rich-doc__block--quote {
  border-left: 3px solid var(--fl-color-border-light, #dcdfe6);
  color: var(--fl-color-text-secondary, #606266);
  padding-left: 12px;
}
.rich-doc__block--code {
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 4px;
  padding: 10px 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: pre-wrap;
}
.rich-doc__run--bold {
  font-weight: 700;
}
.rich-doc__run--italic {
  font-style: italic;
}
.rich-doc__run--underline {
  text-decoration: underline;
}
</style>
