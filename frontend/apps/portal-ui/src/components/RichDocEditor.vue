<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  editorDomToRichDoc,
  escapeHtml,
  htmlToRichDoc,
  richDocToEditorHtml,
  sanitizeHref,
  type RichDoc,
} from '@fenglin/ui-admin'

const props = defineProps<{ modelValue: RichDoc }>()
const emit = defineEmits<{ 'update:modelValue': [RichDoc] }>()

const editorRef = ref<HTMLElement | null>(null)
const linkUrl = ref('')
let lastEmitted = ''
let syncing = false
let savedRange: Range | null = null

/** 点击链接输入框前先记住编辑器里的选区，避免输入 URL 后丢失加链接的目标。 */
function rememberSelection() {
  const selection = window.getSelection()
  if (selection && selection.rangeCount && editorRef.value?.contains(selection.anchorNode)) {
    savedRange = selection.getRangeAt(0).cloneRange()
  }
}

function renderFromModel() {
  const el = editorRef.value
  if (!el) return
  // 只由已清洗的模型生成 HTML，文本与链接地址均已在 richDocToEditorHtml 中转义。
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

/** 链接：协议白名单在 sanitizeHref 里统一校验，非法协议直接拒绝。 */
function applyLink() {
  const href = sanitizeHref(linkUrl.value)
  if (!href) {
    ElMessage.error('仅支持 http/https/mailto 链接')
    return
  }
  const el = editorRef.value
  if (!el) return
  el.focus()
  const selection = window.getSelection()
  if (selection && savedRange) {
    selection.removeAllRanges()
    selection.addRange(savedRange)
  }
  const collapsed = !selection || selection.isCollapsed
  if (collapsed) {
    document.execCommand('insertHTML', false, `<a href="${escapeHtml(href)}">${escapeHtml(href)}</a>`)
  } else {
    document.execCommand('createLink', false, href)
  }
  savedRange = null
  linkUrl.value = ''
  onInput()
}

function insertTable() {
  const el = editorRef.value
  if (!el) return
  el.focus()
  document.execCommand(
    'insertHTML',
    false,
    '<table><thead><tr><th>表头1</th><th>表头2</th></tr></thead>'
      + '<tbody><tr><td>单元格</td><td>单元格</td></tr></tbody></table><p><br></p>',
  )
  onInput()
}

/**
 * 粘贴：HTML 走 DOMParser + 白名单模型往返，保留加粗/斜体/列表/表格，
 * 图片、脚本与全部属性在模型转换时丢弃；无 HTML 时退化为纯文本。
 */
function onPaste(event: ClipboardEvent) {
  event.preventDefault()
  const html = event.clipboardData?.getData('text/html') ?? ''
  const text = event.clipboardData?.getData('text/plain') ?? ''
  if (html) {
    const safeHtml = richDocToEditorHtml(htmlToRichDoc(html))
    document.execCommand('insertHTML', false, safeHtml)
  } else {
    document.execCommand('insertText', false, text)
  }
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
      <el-button size="small" @mousedown.prevent="exec('insertUnorderedList')">无序列表</el-button>
      <el-button size="small" @mousedown.prevent="exec('insertOrderedList')">有序列表</el-button>
      <el-button size="small" @mousedown.prevent="exec('outdent')">减少缩进</el-button>
      <el-button size="small" @mousedown.prevent="exec('indent')">增加缩进</el-button>
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<blockquote>')">引用</el-button>
      <el-button size="small" @mousedown.prevent="exec('formatBlock', '<pre>')">代码块</el-button>
      <el-button size="small" @mousedown.prevent="insertTable">插入表格</el-button>
      <el-input
        v-model="linkUrl"
        class="rich-editor__link-input"
        size="small"
        placeholder="https:// 或 mailto:"
        clearable
        @mousedown="rememberSelection"
        @keyup.enter="applyLink"
      />
      <el-button size="small" @mousedown.prevent="applyLink">链接</el-button>
    </div>
    <div
      ref="editorRef"
      class="rich-editor__body"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      data-placeholder="输入正文…（粘贴保留加粗/斜体/列表/表格，图片与脚本会被移除）"
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
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-bottom: 1px solid var(--fl-color-border-light, #ebeef5);
}
.rich-editor__toolbar .el-button + .el-button {
  margin-left: 0;
}
.rich-editor__link-input {
  width: 190px;
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
.rich-editor__body :deep(ul),
.rich-editor__body :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}
.rich-editor__body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}
.rich-editor__body :deep(th),
.rich-editor__body :deep(td) {
  border: 1px solid var(--fl-color-border-light, #dcdfe6);
  padding: 6px 10px;
  min-width: 60px;
  text-align: left;
}
.rich-editor__body :deep(th) {
  background: var(--fl-color-fill-light, #f5f7fa);
}
.rich-editor__body :deep(a) {
  color: var(--fl-color-primary, #409eff);
  text-decoration: underline;
}
</style>
