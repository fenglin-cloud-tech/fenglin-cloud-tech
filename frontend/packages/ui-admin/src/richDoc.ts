/**
 * 云协文档富文本模型（COL-07 方案 A）。
 *
 * 正文仍整体加密存储，`content_format` 只决定解密后的解释方式：
 * - PLAIN：纯文本；
 * - RICH_JSON：`RichDoc` 结构化 JSON。
 *
 * 安全约束：模型里只有文本与受控标记（加粗/斜体/下划线/块类型），
 * 不保留任何 HTML 标签或属性；渲染端只做文本插值，不使用 v-html。
 */

export type RichBlockType = 'paragraph' | 'heading' | 'bullet' | 'quote' | 'code'

export interface RichRun {
  text: string
  bold?: boolean
  italic?: boolean
  underline?: boolean
}

export interface RichBlock {
  type: RichBlockType
  level?: number
  runs: RichRun[]
}

export interface RichDoc {
  version: 1
  blocks: RichBlock[]
}

const BLOCK_TYPES: RichBlockType[] = ['paragraph', 'heading', 'bullet', 'quote', 'code']
const MAX_BLOCKS = 5000
const MAX_RUNS_PER_BLOCK = 500
const MAX_TEXT_LENGTH = 100000

/** 与后端保持一致：2 MB（按 UTF-8 字节计）。 */
export const MAX_CONTENT_BYTES = 2 * 1024 * 1024

export function emptyRichDoc(): RichDoc {
  return { version: 1, blocks: [{ type: 'paragraph', runs: [{ text: '' }] }] }
}

/** 去掉标签与属性，任何 `<img onerror>` 一类载荷都不会进入渲染模型。 */
export function stripMarkup(value: unknown): string {
  return String(value ?? '')
    .replace(/\u0000/g, '')
    .replace(/<\/?[a-zA-Z][^>]*>/g, '')
}

function cleanText(value: unknown): string {
  return stripMarkup(value).slice(0, MAX_TEXT_LENGTH)
}

function cleanRuns(input: unknown): RichRun[] {
  if (!Array.isArray(input)) {
    const text = cleanText(input)
    return text ? [{ text }] : []
  }
  const runs: RichRun[] = []
  for (const item of input.slice(0, MAX_RUNS_PER_BLOCK)) {
    if (typeof item === 'string') {
      if (item) runs.push({ text: cleanText(item) })
      continue
    }
    if (!item || typeof item !== 'object') continue
    const source = item as Record<string, unknown>
    const text = cleanText(source.text)
    if (!text) continue
    const run: RichRun = { text }
    if (source.bold === true) run.bold = true
    if (source.italic === true) run.italic = true
    if (source.underline === true) run.underline = true
    runs.push(run)
  }
  return runs
}

/**
 * 将任意来源（JSON 字符串 / 对象 / 历史脏数据）收敛为安全的 RichDoc。
 * 无法识别时返回空文档，由调用方决定是否回退纯文本展示。
 */
export function sanitizeRichDoc(input: unknown): RichDoc {
  let source: unknown = input
  if (typeof input === 'string') {
    try {
      source = JSON.parse(input)
    } catch {
      return plainToRichDoc(input)
    }
  }
  if (!source || typeof source !== 'object') {
    return emptyRichDoc()
  }
  const rawBlocks = (source as Record<string, unknown>).blocks
  if (!Array.isArray(rawBlocks)) {
    return emptyRichDoc()
  }
  const blocks: RichBlock[] = []
  for (const raw of rawBlocks.slice(0, MAX_BLOCKS)) {
    if (!raw || typeof raw !== 'object') continue
    const item = raw as Record<string, unknown>
    const type = BLOCK_TYPES.includes(item.type as RichBlockType)
      ? (item.type as RichBlockType)
      : 'paragraph'
    const block: RichBlock = { type, runs: cleanRuns(item.runs) }
    if (type === 'heading') {
      const level = Number(item.level)
      block.level = Number.isFinite(level) ? Math.min(3, Math.max(1, Math.trunc(level))) : 1
    }
    if (!block.runs.length && type !== 'paragraph') continue
    blocks.push(block)
  }
  return { version: 1, blocks: blocks.length ? blocks : emptyRichDoc().blocks }
}

/** 解析接口返回的正文字段；格式不是 RICH_JSON 或 JSON 不可用时返回 null，调用方按纯文本展示。 */
export function parseRichContent(content: string | undefined | null, format: string | undefined | null): RichDoc | null {
  if (format !== 'RICH_JSON' || !content) return null
  try {
    const parsed = JSON.parse(content)
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.blocks)) return null
    return sanitizeRichDoc(parsed)
  } catch {
    return null
  }
}

/** 富文本转纯文本（切换格式或降级展示时使用）。 */
export function richDocToPlain(doc: RichDoc): string {
  const safe = sanitizeRichDoc(doc)
  return safe.blocks
    .map((block) => {
      const text = block.runs.map((run) => run.text).join('')
      if (block.type === 'heading') return text
      if (block.type === 'bullet') return `• ${text}`
      return text
    })
    .join('\n')
}

/** 纯文本转富文本：一行一个段落，保留空行。 */
export function plainToRichDoc(text: string): RichDoc {
  const normalized = String(text ?? '').replace(/\r\n?/g, '\n')
  if (!normalized) return emptyRichDoc()
  const lines = normalized.split('\n')
  return {
    version: 1,
    blocks: lines.map((line) => ({ type: 'paragraph', runs: line ? [{ text: cleanText(line) }] : [] })),
  }
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function runsToHtml(runs: RichRun[]): string {
  return runs
    .map((run) => {
      let html = escapeHtml(run.text)
      if (run.underline) html = `<u>${html}</u>`
      if (run.italic) html = `<em>${html}</em>`
      if (run.bold) html = `<strong>${html}</strong>`
      return html
    })
    .join('')
}

/**
 * 生成 contenteditable 初始 HTML。仅由已清洗的模型生成，文本先转义，
 * 因此这里赋值 innerHTML 不会引入外部标签或属性。
 */
export function richDocToEditorHtml(doc: RichDoc): string {
  const safe = sanitizeRichDoc(doc)
  return safe.blocks
    .map((block) => {
      const inner = runsToHtml(block.runs)
      if (block.type === 'heading') {
        const level = Math.min(3, Math.max(1, block.level ?? 1))
        return `<h${level}>${inner || '<br>'}</h${level}>`
      }
      if (block.type === 'bullet') return `<ul><li>${inner || '<br>'}</li></ul>`
      if (block.type === 'quote') return `<blockquote>${inner || '<br>'}</blockquote>`
      if (block.type === 'code') return `<pre>${inner || '<br>'}</pre>`
      return `<p>${inner || '<br>'}</p>`
    })
    .join('')
}

interface EditorMarks {
  bold?: boolean
  italic?: boolean
  underline?: boolean
}

function collectRuns(node: Node, marks: EditorMarks, out: RichRun[]): void {
  if (node.nodeType === Node.TEXT_NODE) {
    const text = node.textContent ?? ''
    if (!text) return
    if (!text.trim() && !out.length) return
    out.push({ text: cleanText(text), ...marks })
    return
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return
  const element = node as HTMLElement
  const tag = element.tagName.toUpperCase()
  if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'IMG' || tag === 'IFRAME' || tag === 'OBJECT') return
  const nextMarks: EditorMarks = { ...marks }
  if (tag === 'STRONG' || tag === 'B') nextMarks.bold = true
  if (tag === 'EM' || tag === 'I') nextMarks.italic = true
  if (tag === 'U') nextMarks.underline = true
  if (tag === 'BR') {
    out.push({ text: '\n', ...marks })
    return
  }
  element.childNodes.forEach((child) => collectRuns(child, nextMarks, out))
}

function runsFromElement(element: Element): RichRun[] {
  const runs: RichRun[] = []
  element.childNodes.forEach((child) => collectRuns(child, {}, runs))
  return cleanRuns(runs)
}

function blockFromElement(element: HTMLElement): RichBlock[] {
  const tag = element.tagName.toUpperCase()
  if (tag === 'UL' || tag === 'OL') {
    return Array.from(element.children)
      .filter((child) => child.tagName.toUpperCase() === 'LI')
      .map((child) => ({ type: 'bullet' as RichBlockType, runs: runsFromElement(child) }))
  }
  if (tag === 'H1' || tag === 'H2' || tag === 'H3') {
    return [{ type: 'heading', level: Number(tag.slice(1)), runs: runsFromElement(element) }]
  }
  if (tag === 'BLOCKQUOTE') {
    return [{ type: 'quote', runs: runsFromElement(element) }]
  }
  if (tag === 'PRE') {
    return [{ type: 'code', runs: runsFromElement(element) }]
  }
  if (tag === 'P' || tag === 'DIV') {
    return [{ type: 'paragraph', runs: runsFromElement(element) }]
  }
  // 其他内联容器（span 等）：整段作为普通段落，标签与属性一律丢弃。
  return [{ type: 'paragraph', runs: runsFromElement(element) }]
}

/**
 * contenteditable DOM → RichDoc。
 * 只识别白名单标签，忽略所有属性；img/script/iframe 等节点整体丢弃。
 */
export function editorDomToRichDoc(root: HTMLElement): RichDoc {
  const blocks: RichBlock[] = []
  root.childNodes.forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      const text = child.textContent ?? ''
      if (text.trim()) blocks.push({ type: 'paragraph', runs: cleanRuns(text) })
      return
    }
    if (child.nodeType !== Node.ELEMENT_NODE) return
    const element = child as HTMLElement
    const tag = element.tagName.toUpperCase()
    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'IMG' || tag === 'IFRAME' || tag === 'OBJECT') return
    blocks.push(...blockFromElement(element))
  })
  const cleaned = blocks.filter((block) => block.runs.length || block.type === 'paragraph')
  return { version: 1, blocks: cleaned.length ? cleaned.slice(0, MAX_BLOCKS) : emptyRichDoc().blocks }
}

/** UTF-8 字节数（用于 2 MB 上限的前端预检）。 */
export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value ?? '').length
}
