#!/usr/bin/env node
/**
 * 雪花 ID 精度静态检查（规范见《丰麟云枢 · 平台总体规范与统一标准》5.6）
 *
 * 命中规则：
 *   ID_INPUT_NUMBER  把 ID 绑到 el-input-number / type="number" 数字控件
 *   ID_NUMBER_CAST   ID 被 Number()/parseInt()/parseFloat()/~~/一元 + 转成 number
 *
 * 用法：
 *   node scripts/check-id-precision.mjs                 全仓检查，有命中即退出码 1
 *   node scripts/check-id-precision.mjs --baseline <f>  与基线比对：新增命中失败；基线中已消失的条目也失败（强制清理，不做永久白名单）
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'

const ROOT = resolve(process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : '.')
const SCAN_DIRS = ['apps', 'packages']
const EXTS = ['.vue', '.ts', '.tsx']
const SKIP = new Set(['node_modules', 'dist', '.turbo', 'coverage'])

const IDENT = /(?:^|[^\w$])([A-Za-z_$][\w$]*(?:_ids?|Ids?|ID)|id)(?=$|[^\w$])/
const ID_FIELD = /(?:^|[^\w$])([A-Za-z_$][\w$]*(?:_id|Id|ID))(?=$|[^\w$])/
const ID_ARRAY = /(?:^|[^\w$])([A-Za-z_$][\w$]*(?:_ids|Ids|ID))(?=$|[^\w$])/
const CASTS = [
  /\bNumber\s*\(\s*([^)]*)\)/,
  /\bparseInt\s*\(\s*([^,)]*)/,
  /\bparseFloat\s*\(\s*([^,)]*)/,
  /~~\s*([A-Za-z_$][\w$.]*)/,
  /(?:^|[=(,:?]\s*)\+\s*([A-Za-z_$][\w$.]*(?:_id|Id|ID)\b)/,
]

function isId(expr) {
  if (!expr) return false
  const cleaned = expr.trim()
  if (cleaned.includes('(') || cleaned.includes('/') || cleaned.includes('%')) return false
  return IDENT.test(cleaned) && (ID_FIELD.test(cleaned) || ID_ARRAY.test(cleaned))
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (SKIP.has(name)) continue
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) walk(p, out)
    else if (EXTS.some((e) => name.endsWith(e))) out.push(p)
  }
  return out
}

const hits = []
for (const scanDir of SCAN_DIRS) {
  const base = join(ROOT, scanDir)
  let files
  try {
    files = walk(base)
  } catch {
    continue
  }
  for (const file of files) {
    const rel = relative(ROOT, file).replace(/\\/g, '/')
    const lines = readFileSync(file, 'utf8').split(/\r?\n/)
    lines.forEach((line, i) => {
      const no = i + 1
      if (line.trimStart().startsWith('//') || line.trimStart().startsWith('*')) return
      // 规则 1：ID 绑数字控件
      if (/el-input-number/.test(line) || /type="number"/.test(line)) {
        const model = line.match(/v-model(?::[\w-]+)?="([^"]+)"/)
        if (model && isId(model[1])) {
          hits.push({ file: rel, line: no, rule: 'ID_INPUT_NUMBER', text: line.trim() })
          return
        }
      }
      // 规则 2：ID 数值强转
      if (/\b(Number|parseInt|parseFloat)\s*\(|~~|\+\s*[A-Za-z_$]/.test(line)) {
        for (const re of CASTS) {
          const m = line.match(re)
          if (m && isId(m[1])) {
            hits.push({ file: rel, line: no, rule: 'ID_NUMBER_CAST', text: line.trim() })
            return
          }
        }
      }
    })
  }
}

const baselineArg = process.argv.indexOf('--baseline')
const baselinePath = baselineArg > 0 ? resolve(process.argv[baselineArg + 1]) : null
const key = (h) => `${h.file}:${h.line}:${h.rule}`

if (baselinePath) {
  let baseline = []
  try {
    baseline = JSON.parse(readFileSync(baselinePath, 'utf8'))
  } catch {
    console.error(`无法读取基线文件：${baselinePath}`)
    process.exit(2)
  }
  const baseSet = new Set(baseline)
  const currentSet = new Set(hits.map(key))
  const added = hits.filter((h) => !baseSet.has(key(h)))
  const stale = [...baseSet].filter((k) => !currentSet.has(k))
  console.log(`扫描完成：当前命中 ${hits.length} 条，基线 ${baseSet.size} 条`)
  for (const h of added) console.log(`新增命中 ${h.file}:${h.line} [${h.rule}] ${h.text}`)
  for (const k of stale) console.log(`基线已失效（请从基线删除）：${k}`)
  if (added.length || stale.length) process.exit(1)
  console.log('通过：无新增命中，基线无失效条目')
} else {
  console.log(`扫描完成：命中 ${hits.length} 条`)
  for (const h of hits) console.log(`${h.file}:${h.line} [${h.rule}] ${h.text}`)
  if (hits.length) process.exit(1)
  console.log('通过：未发现 ID 数值化用法')
}
