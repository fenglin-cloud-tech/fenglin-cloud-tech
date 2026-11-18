/**
 * 云协文档富文本模型（COL-07 方案 A）。
 *
 * 正文仍整体加密存储，`content_format` 只决定解密后的解释方式：
 * - PLAIN：纯文本；
 * - RICH_JSON：`RichDoc` 结构化 JSON。
 *
 * 安全约束：模型里只有文本与受控标记（加粗/斜体/下划线、链接、列表、表格），
 * 不保留任何 HTML 标签或属性；渲染端只做文本插值，不使用 v-html。
 * 链接协议、标签白名单与属性丢弃都在本文件的模型转换里完成。
 */

export type RichBlockType = 'paragraph' | 'heading' | 'bullet' | 'ordered' | 'quote' | 'code' | 'table'

export interface RichRun {
  text: string
  bold?: boolean
  italic?: boolean
  underline?: boolean
  /** 仅允许 http/https/mailto，其他协议在清洗时直接丢弃。 */
  href?: string
}

/** 表格单元格：每格是一组受控 run，不保存任何内嵌 HTML。 */
export type RichCell = RichRun[]

export interface RichTable {
  header: RichCell[]
  rows: RichCell[][]
}

export interface RichBlock {
  type: RichBlockType
  /** 标题层级（1-3）或列表缩进层级（0-5）。 */
  level?: number
  runs: RichRun[]
  table?: RichTable
}

export interface RichDoc {
  version: 1
  blocks: RichBlock[]
}

const BLOCK_TYPES: RichBlockType[] = ['paragraph', 'heading', 'bullet', 'ordered', 'quote', 'code', 'table']
const LIST_TYPES: RichBlockType[] = ['bullet', 'ordered']
const MAX_BLOCKS = 5000
const MAX_RUNS_PER_BLOCK = 500
const MAX_TEXT_LENGTH = 100000
const MAX_LIST_LEVEL = 5
const MAX_TABLE_ROWS = 20
const MAX_TABLE_COLS = 8
const MAX_HREF_LENGTH = 2048

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

/**
 * 链接协议白名单：只放行 http/https/mailto。
 * 先剔除控制字符与空白（防 `java\nscript:` 一类绕过），再交给 URL 解析；
 * `javascript:`/`data:`/`vbscript:`/相对路径/协议相对地址一律返回 undefined。
 */
export function sanitizeHref(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const raw = value.replace(/[\u0000-\u0020\u007f]/g, '')
  if (!raw || raw.length > MAX_HREF_LENGTH) return undefined
  if (/^mailto:/i.test(raw)) return raw
  if (!/^https?:\/\//i.test(raw)) return undefined
  try {
    const url = new URL(raw)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return undefined
    return url.toString()
  } catch {
    return undefined
  }
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
    const href = sanitizeHref(source.href)
    if (href) run.href = href
    runs.push(run)
  }
  return runs
}

function sanitizeTable(input: unknown): RichTable | null {
  if (!input || typeof input !== 'object') return null
  const source = input as Record<string, unknown>
  const header = Array.isArray(source.header) ? source.header : []
  const body = Array.isArray(source.rows) ? source.rows.slice(0, MAX_TABLE_ROWS) : []
  const matrix: unknown[][] = [header, ...body.filter((row) => Array.isArray(row)) as unknown[][]]
  let cols = 0
  for (const row of matrix) cols = Math.max(cols, Math.min(row.length, MAX_TABLE_COLS))
  if (!cols) return null
  const normalized = matrix.map((row) => {
    const cells = row.slice(0, cols).map((cell) => cleanRuns(cell))
    while (cells.length < cols) cells.push([])
    return cells
  })
  return { header: normalized[0], rows: normalized.slice(1) }
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
    const block: RichBlock = { type, runs: [] }
    if (type === 'table') {
      const table = sanitizeTable(item.table)
      if (!table) continue
      block.table = table
    } else {
      block.runs = cleanRuns(item.runs)
    }
    if (type === 'heading') {
      const level = Number(item.level)
      block.level = Number.isFinite(level) ? Math.min(3, Math.max(1, Math.trunc(level))) : 1
    } else if (LIST_TYPES.includes(type)) {
      const level = Number(item.level)
      block.level = Number.isFinite(level) ? Math.min(MAX_LIST_LEVEL, Math.max(0, Math.trunc(level))) : 0
    }
    if (!block.runs.length && type !== 'paragraph' && type !== 'table') continue
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

function tableToPlain(table: RichTable): string {
  return [table.header, ...table.rows]
    .map((row) => row.map((cell) => cell.map((run) => run.text).join('')).join(' | '))
    .join('\n')
}

/** 富文本转纯文本（切换格式或降级展示时使用）。 */
export function richDocToPlain(doc: RichDoc): string {
  const safe = sanitizeRichDoc(doc)
  const counters: number[] = []
  return safe.blocks
    .map((block) => {
      if (block.type === 'table' && block.table) return tableToPlain(block.table)
      const text = block.runs.map((run) => run.text).join('')
      if (block.type === 'heading') return text
      const level = Math.min(MAX_LIST_LEVEL, Math.max(0, block.level ?? 0))
      const indent = '  '.repeat(level)
      if (block.type === 'bullet') return `${indent}• ${text}`
      if (block.type === 'ordered') {
        counters[level] = (counters[level] ?? 0) + 1
        counters.length = level + 1
        return `${indent}${counters[level]}. ${text}`
      }
      counters.length = 0
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

export function escapeHtml(value: string): string {
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
      const href = sanitizeHref(run.href)
      if (href) html = `<a href="${escapeHtml(href)}">${html}</a>`
      return html
    })
    .join('')
}

function tableToHtml(table: RichTable): string {
  const header = table.header.map((cell) => `<th>${runsToHtml(cell) || '<br>'}</th>`).join('')
  const body = table.rows
    .map((row) => `<tr>${row.map((cell) => `<td>${runsToHtml(cell) || '<br>'}</td>`).join('')}</tr>`)
    .join('')
  return `<table><thead><tr>${header}</tr></thead>${body ? `<tbody>${body}</tbody>` : ''}</table>`
}

interface ListNode {
  type: 'bullet' | 'ordered'
  runs: RichRun[]
  children: ListNode[]
}

function buildListTree(blocks: RichBlock[]): ListNode[] {
  const roots: ListNode[] = []
  const stack: ListNode[] = []
  for (const block of blocks) {
    const level = Math.min(Math.max(0, block.level ?? 0), stack.length)
    const node: ListNode = { type: block.type as 'bullet' | 'ordered', runs: block.runs, children: [] }
    if (level === 0 || !stack.length) {
      roots.push(node)
    } else {
      stack[level - 1].children.push(node)
    }
    stack.length = level
    stack.push(node)
  }
  return roots
}

function listTreeToHtml(nodes: ListNode[]): string {
  let html = ''
  let index = 0
  while (index < nodes.length) {
    const type = nodes[index].type
    let end = index
    while (end < nodes.length && nodes[end].type === type) end += 1
    const tag = type === 'ordered' ? 'ol' : 'ul'
    const items = nodes
      .slice(index, end)
      .map((node) => `<li>${runsToHtml(node.runs)}${node.children.length ? listTreeToHtml(node.children) : ''}</li>`)
      .join('')
    html += `<${tag}>${items}</${tag}>`
    index = end
  }
  return html
}

/**
 * 生成 contenteditable 初始 HTML。仅由已清洗的模型生成，文本先转义，
 * 因此这里赋值 innerHTML 不会引入外部标签或属性。
 */
export function richDocToEditorHtml(doc: RichDoc): string {
  const safe = sanitizeRichDoc(doc)
  const parts: string[] = []
  let index = 0
  while (index < safe.blocks.length) {
    const block = safe.blocks[index]
    if (LIST_TYPES.includes(block.type)) {
      let end = index
      while (end < safe.blocks.length && LIST_TYPES.includes(safe.blocks[end].type)) end += 1
      parts.push(listTreeToHtml(buildListTree(safe.blocks.slice(index, end))))
      index = end
      continue
    }
    if (block.type === 'table' && block.table) {
      parts.push(tableToHtml(block.table))
    } else if (block.type === 'heading') {
      const level = Math.min(3, Math.max(1, block.level ?? 1))
      parts.push(`<h${level}>${runsToHtml(block.runs) || '<br>'}</h${level}>`)
    } else if (block.type === 'quote') {
      parts.push(`<blockquote>${runsToHtml(block.runs) || '<br>'}</blockquote>`)
    } else if (block.type === 'code') {
      parts.push(`<pre>${runsToHtml(block.runs) || '<br>'}</pre>`)
    } else {
      parts.push(`<p>${runsToHtml(block.runs) || '<br>'}</p>`)
    }
    index += 1
  }
  return parts.join('')
}

interface EditorMarks {
  bold?: boolean
  italic?: boolean
  underline?: boolean
  href?: string
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
  if (tag === 'A') {
    // 只读取 href 一个属性，且必须通过协议白名单；onclick 等属性一律不进入模型。
    const href = sanitizeHref(element.getAttribute('href'))
    if (href) nextMarks.href = href
  }
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

function listBlocksFrom(list: HTMLElement, level: number, out: RichBlock[]): void {
  const type: RichBlockType = list.tagName.toUpperCase() === 'OL' ? 'ordered' : 'bullet'
  const items = Array.from(list.children).filter((child) => child.tagName.toUpperCase() === 'LI')
  for (const item of items) {
    const runs: RichRun[] = []
    const extras: RichBlock[] = []
    item.childNodes.forEach((child) => {
      if (child.nodeType === Node.ELEMENT_NODE) {
        const tag = (child as HTMLElement).tagName.toUpperCase()
        if (tag === 'UL' || tag === 'OL') return
        // 列表项里粘进来的表格/标题/引用等块级结构上提为同级块，避免被拍平成文本。
        if (['TABLE', 'H1', 'H2', 'H3', 'BLOCKQUOTE', 'PRE'].includes(tag)) {
          extras.push(...blockFromElement(child as HTMLElement))
          return
        }
      }
      collectRuns(child, {}, runs)
    })
    out.push({ type, level: Math.min(level, MAX_LIST_LEVEL), runs: cleanRuns(runs) })
    const nested = Array.from(item.children).filter((child) => {
      const tag = child.tagName.toUpperCase()
      return tag === 'UL' || tag === 'OL'
    })
    for (const child of nested) {
      if (level < MAX_LIST_LEVEL) listBlocksFrom(child as HTMLElement, level + 1, out)
    }
    out.push(...extras)
  }
}

function tableFromElement(element: HTMLElement): RichTable | null {
  const headRow = element.querySelector(':scope > thead > tr') as HTMLTableRowElement | null
  const body = element.querySelector(':scope > tbody') as HTMLTableSectionElement | null
  let bodyRows: HTMLTableRowElement[]
  if (body) {
    bodyRows = Array.from(body.children).filter((child) => child.tagName.toUpperCase() === 'TR') as HTMLTableRowElement[]
  } else {
    bodyRows = Array.from(element.children).filter((child) => child.tagName.toUpperCase() === 'TR') as HTMLTableRowElement[]
  }
  const rows: HTMLTableRowElement[] = []
  if (headRow) {
    rows.push(headRow)
  } else if (bodyRows.length) {
    rows.push(bodyRows.shift() as HTMLTableRowElement)
  }
  rows.push(...bodyRows.slice(0, MAX_TABLE_ROWS))
  const matrix = rows.map((row) =>
    Array.from(row.children)
      .filter((cell) => ['TD', 'TH'].includes(cell.tagName.toUpperCase()))
      .slice(0, MAX_TABLE_COLS)
      .map((cell) => runsFromElement(cell)),
  )
  const cols = matrix.reduce((max, row) => Math.max(max, row.length), 0)
  if (!cols) return null
  const normalized = matrix.map((row) => {
    const cells = row.slice(0, cols)
    while (cells.length < cols) cells.push([])
    return cells
  })
  return { header: normalized[0], rows: normalized.slice(1) }
}

function blockFromElement(element: HTMLElement): RichBlock[] {
  const tag = element.tagName.toUpperCase()
  if (tag === 'UL' || tag === 'OL') {
    const blocks: RichBlock[] = []
    listBlocksFrom(element, 0, blocks)
    return blocks
  }
  if (tag === 'TABLE') {
    const table = tableFromElement(element)
    return table ? [{ type: 'table', runs: [], table }] : []
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
  if (tag === 'P' || tag === 'DIV' || tag === 'SECTION' || tag === 'ARTICLE') {
    // 粘贴时常有多层 div 包裹；发现块级子节点时逐块下钻，避免把表格/列表拍平成文本。
    const blockChildren = Array.from(element.children).filter((child) =>
      ['P', 'DIV', 'UL', 'OL', 'TABLE', 'H1', 'H2', 'H3', 'BLOCKQUOTE', 'PRE', 'SECTION', 'ARTICLE'].includes(
        child.tagName.toUpperCase(),
      ),
    )
    if (tag !== 'P' && blockChildren.length) {
      return blockChildren.flatMap((child) => blockFromElement(child as HTMLElement))
    }
    return [{ type: 'paragraph', runs: runsFromElement(element) }]
  }
  // 其他内联容器（span 等）：整段作为普通段落，标签与属性一律丢弃。
  return [{ type: 'paragraph', runs: runsFromElement(element) }]
}

/**
 * contenteditable DOM → RichDoc。
 * 只识别白名单标签，忽略除安全 href 外的所有属性；
 * img/script/iframe 等节点整体丢弃，内嵌 HTML 不会进入模型。
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
  const cleaned = blocks.filter((block) => block.runs.length || block.type === 'paragraph' || block.type === 'table')
  return { version: 1, blocks: cleaned.length ? cleaned.slice(0, MAX_BLOCKS) : emptyRichDoc().blocks }
}

/**
 * 粘贴 HTML → RichDoc。DOMParser 生成的文档没有浏览上下文，
 * 脚本不会执行、图片不会加载，再走同一套白名单转换。
 */
export function htmlToRichDoc(html: string): RichDoc {
  if (typeof DOMParser === 'undefined') {
    return plainToRichDoc(stripMarkup(html))
  }
  const parsed = new DOMParser().parseFromString(html, 'text/html')
  return editorDomToRichDoc(parsed.body)
}

/** UTF-8 字节数（用于 2 MB 上限的前端预检）。 */
export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value ?? '').length
}
