<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { editorDomToRichDoc, richDocToEditorHtml, type RichDoc } from '@fenglin/ui-admin'

const props = defineProps<{ modelValue: RichDoc }>()
const emit = defineEmits<{ 'update:modelValue': [RichDoc] }>()

const editorRef = ref<HTMLElement | null>(null)
let lastEmitted = ''
let syncing = false

function renderFromModel() {
  const el = editorRef.value
  if (!el) return
  // 只由已清洗的模型生成 HTML，文本已在 richDocToEditorHtml 中转义。
  el.innerHTML = richDocToEditorHtml(props.modelValue)
}

function onInput() {
  const el = editorRef.value
  if (!el) return
  const doc = editorDomToRichDoc(el)
  const json = JSON.stringify(doc)
  if (json === lastEmitted) return
  lastEmitted = json
  syncing = true
  emit('update:modelValue', doc)
  void nextTick(() => {
    syncing = false
  })
}

function exec(command: string, value?: string) {
  const el = editorRef.value
  if (!el) return
  el.focus()
  document.execCommand(command, false, value)
  onInput()
}

/**
 * 粘贴只接受纯文本：外部 HTML（含属性/脚本/图片）一律不进入编辑器。
 */
function onPaste(event: ClipboardEvent) {
  event.preventDefault()
  const text = event.clipboardData?.getData('text/plain') ?? ''
  document.execCommand('insertText', false, text)
  onInput()
}

watch(
  () => props.modelValue,
  () => {
    if (!syncing) renderFromModel()
  },
  { deep: true },
)

onMounted(renderFromModel)
</script>

<template>
  <div class="rich-editor">
    <div class="rich-editor__toolbar">
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<p>')">正文</el-button>
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<h1>')">标题1</el-button>
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<h2>')">标题2</el-button>
      <el-button size="small" @mousedown.prevent="exec('bold')"><b>B</b></el-button>
      <el-button size="small" @mousedown.prevent="exec('italic')"><i>I</i></el-button>
      <el-button size="small" @mousedown.prevent="exec('underline')"><u>U</u></el-button>
      <el-button size="small" @mousedown.prevent="exec('insertUnorderedList')">列表</el-button>
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<blockquote>')">引用</el-button>
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<pre>')">代码块</el-button>
    </div>
    <div
      ref="editorRef"
      class="rich-editor__body"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      data-placeholder="输入正文…（粘贴仅保留纯文本）"
      @input="onInput"
      @paste="onPaste"
    />
  </div>
</template>

<style scoped>
.rich-editor {
  border: 1px solid var(--fl-color-border, #dcdfe6);
  border-radius: 4px;
  overflow: hidden;
}
.rich-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-bottom: 1px solid var(--fl-color-border-light, #ebeef5);
}
.rich-editor__toolbar .el-button + .el-button {
  margin-left: 0;
}
.rich-editor__body {
  min-height: 360px;
  max-height: 560px;
  overflow: auto;
  padding: 12px 14px;
  line-height: 1.75;
  outline: none;
  word-break: break-word;
}
.rich-editor__body:empty::before {
  content: attr(data-placeholder);
  color: var(--fl-color-text-placeholder, #a8abb2);
}
.rich-editor__body :deep(h1) {
  font-size: 22px;
  margin: 12px 0 8px;
}
.rich-editor__body :deep(h2) {
  font-size: 18px;
  margin: 12px 0 8px;
}
.rich-editor__body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 12px;
  border-left: 3px solid var(--fl-color-border, #dcdfe6);
  color: var(--fl-color-text-secondary, #606266);
}
.rich-editor__body :deep(pre) {
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 4px;
  padding: 10px 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: pre-wrap;
}
.rich-editor__body :deep(ul) {
  margin: 8px 0;
  padding-left: 22px;
}
</style>
