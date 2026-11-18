<script setup lang="ts">
import { computed, defineComponent, h, type PropType } from 'vue'
import { sanitizeRichDoc, type RichBlock, type RichDoc, type RichRun } from '../richDoc'

const props = defineProps<{
  model: RichDoc | string
  /** 由页面用短期 asset_token 拼出图片地址；缺失时只显示占位，不请求外部链接。 */
  resolveImage?: (fileId: string) => string | undefined
}>()

const doc = computed(() => sanitizeRichDoc(props.model))

function isExternal(href: string) {
  return /^https?:/i.test(href)
}

/** 统一渲染 run：链接只保留安全 href，外链强制 target/rel；始终文本插值，不使用 v-html。 */
const RichRuns = defineComponent({
  name: 'RichRuns',
  props: { runs: { type: Array as PropType<RichRun[]>, required: true } },
  setup(runProps) {
    return () =>
      runProps.runs.map((run, index) => {
        const classes = {
          'rich-doc__run--bold': !!run.bold,
          'rich-doc__run--italic': !!run.italic,
          'rich-doc__run--underline': !!run.underline,
        }
        if (!run.href) {
          return h('span', { class: classes, key: index }, run.text)
        }
        return h(
          'a',
          {
            class: classes,
            href: run.href,
            target: isExternal(run.href) ? '_blank' : undefined,
            rel: isExternal(run.href) ? 'noopener noreferrer' : undefined,
            key: index,
          },
          run.text,
        )
      })
  },
})

/** 列表层级与有序序号在渲染时计算，模型里只存 level。 */
const rendered = computed(() => {
  const counters: number[] = []
  return doc.value.blocks.map((block, index) => {
    let marker = ''
    if (block.type === 'bullet') {
      marker = '•'
      counters.length = 0
    } else if (block.type === 'ordered') {
      const level = Math.min(5, Math.max(0, block.level ?? 0))
      counters[level] = (counters[level] ?? 0) + 1
      counters.length = level + 1
      marker = `${counters[level]}.`
    } else {
      counters.length = 0
    }
    return { block, index, marker }
  })
})

function blockTag(block: RichBlock) {
  if (block.type === 'heading') return `h${Math.min(3, Math.max(1, block.level ?? 1))}`
  if (block.type === 'quote') return 'blockquote'
  if (block.type === 'code') return 'pre'
  return 'div'
}

function listStyle(block: RichBlock) {
  if (block.type !== 'bullet' && block.type !== 'ordered') return undefined
  return { paddingLeft: `${20 + Math.min(5, Math.max(0, block.level ?? 0)) * 20}px` }
}
</script>

<template>
  <div class="rich-doc">
    <template v-for="entry in rendered" :key="entry.index">
      <div v-if="entry.block.type === 'image' && entry.block.fileId" class="rich-doc__image">
        <img
          :src="props.resolveImage?.(entry.block.fileId)"
          :width="entry.block.width"
          :height="entry.block.height"
          alt="文档图片"
          loading="lazy"
        />
      </div>
      <table v-else-if="entry.block.type === 'table' && entry.block.table" class="rich-doc__table">
        <thead>
          <tr>
            <th v-for="(cell, cellIndex) in entry.block.table.header" :key="cellIndex">
              <RichRuns :runs="cell" />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in entry.block.table.rows" :key="rowIndex">
            <td v-for="(cell, cellIndex) in row" :key="cellIndex">
              <RichRuns :runs="cell" />
            </td>
          </tr>
        </tbody>
      </table>
      <component
        :is="blockTag(entry.block)"
        v-else
        :class="['rich-doc__block', `rich-doc__block--${entry.block.type}`]"
        :style="listStyle(entry.block)"
      >
        <span v-if="entry.marker" class="rich-doc__marker">{{ entry.marker }}</span>
        <RichRuns :runs="entry.block.runs" />
      </component>
    </template>
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
.rich-doc__marker {
  display: inline-block;
  min-width: 18px;
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
.rich-doc__image img {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 8px 0;
  border-radius: 4px;
}
.rich-doc a {
  color: var(--fl-color-primary, #409eff);
  text-decoration: underline;
}
.rich-doc__table {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.rich-doc__table th,
.rich-doc__table td {
  border: 1px solid var(--fl-color-border-light, #dcdfe6);
  padding: 6px 10px;
  min-width: 60px;
  text-align: left;
  vertical-align: top;
}
.rich-doc__table th {
  background: var(--fl-color-fill-light, #f5f7fa);
  font-weight: 600;
}
</style>
