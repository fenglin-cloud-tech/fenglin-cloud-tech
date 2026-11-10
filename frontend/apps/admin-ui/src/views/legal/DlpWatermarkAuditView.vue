<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime } from './labels'

const tab = ref('watermarks')

const watermarks = ref<LegalRecord[]>([])
const watermarkTotal = ref(0)
const watermarkLoading = ref(false)
const watermarkError = ref('')
const watermarkFilter = reactive({ biz_type: '', trace_status: '' })

const terminals = ref<LegalRecord[]>([])
const terminalTotal = ref(0)
const terminalLoading = ref(false)
const terminalError = ref('')
const terminalStatus = ref('')

const auditLogs = ref<LegalRecord[]>([])
const auditTotal = ref(0)
const auditLoading = ref(false)
const auditError = ref('')
const auditAction = ref('')
const verifyResult = ref<LegalRecord | null>(null)

const traceVisible = ref(false)
const traceLoading = ref(false)
const traceMode = ref('embed_code')
const traceValue = ref('')
const traceResult = ref<LegalRecord | null>(null)

const watermarkVisible = ref(false)
const watermarkSaving = ref(false)
const watermarkForm = reactive({
  biz_type: 'CONTRACT',
  biz_id: '',
  watermark_type: 'BOTH',
  file_hash: '',
  terminal_id: undefined as number | undefined,
  user_id: undefined as number | undefined,
  ip: '',
})

const gapItems = [
  '水印查看需 legal:dlp:watermark:view，生成需 legal:dlp:watermark:manage，溯源需 legal:dlp:trace。',
  '终端列表需 tenant:legal:dlp:terminal:view；终端注册由 DLP 客户端调用内部接口，管理端不模拟注册。',
  '审计日志查看需 legal:dlp:audit:view，链式校验需 legal:dlp:audit:integrity；校验按 chain_id 与序号区间进行，结果以后端返回为准。',
]

async function loadWatermarks() {
  watermarkLoading.value = true
  watermarkError.value = ''
  try {
    const result = await legalApi.dlp.watermarks({
      biz_type: watermarkFilter.biz_type || undefined,
      trace_status: watermarkFilter.trace_status || undefined,
      page: 1,
      size: 50,
    })
    watermarks.value = result.records
    watermarkTotal.value = result.total
  } catch (err) {
    watermarks.value = []
    watermarkError.value = describeError(err)
  } finally {
    watermarkLoading.value = false
  }
}

async function loadTerminals() {
  terminalLoading.value = true
  terminalError.value = ''
  try {
    const result = await legalApi.dlp.terminals({ status: terminalStatus.value || undefined, page: 1, size: 50 })
    terminals.value = result.records
    terminalTotal.value = result.total
  } catch (err) {
    terminals.value = []
    terminalError.value = describeError(err)
  } finally {
    terminalLoading.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  auditError.value = ''
  try {
    const result = await legalApi.dlp.auditLogs({ action: auditAction.value || undefined, page: 1, size: 50 })
    auditLogs.value = result.records
    auditTotal.value = result.total
  } catch (err) {
    auditLogs.value = []
    auditError.value = describeError(err)
  } finally {
    auditLoading.value = false
  }
}

async function verifyChain() {
  try {
    verifyResult.value = await legalApi.dlp.verifyAudit({})
  } catch (err) {
    verifyResult.value = null
    ElMessage.error(describeError(err))
  }
}

async function trace() {
  if (!traceValue.value.trim()) {
    ElMessage.warning('请填写溯源码或文件哈希')
    return
  }
  traceLoading.value = true
  try {
    traceResult.value = await legalApi.dlp.trace({ [traceMode.value]: traceValue.value.trim() })
  } catch (err) {
    traceResult.value = null
    ElMessage.error(describeError(err))
  } finally {
    traceLoading.value = false
  }
}

async function createWatermark() {
  if (!watermarkForm.biz_type || !watermarkForm.biz_id.trim()) {
    ElMessage.warning('业务类型与业务ID必填')
    return
  }
  watermarkSaving.value = true
  try {
    const result = await legalApi.dlp.createWatermark({
      biz_type: watermarkForm.biz_type,
      biz_id: watermarkForm.biz_id.trim(),
      watermark_type: watermarkForm.watermark_type,
      file_hash: watermarkForm.file_hash || undefined,
      terminal_id: watermarkForm.terminal_id ?? undefined,
      user_id: watermarkForm.user_id ?? undefined,
      ip: watermarkForm.ip || undefined,
    })
    ElMessage.success(`水印已生成：${result.watermark_no ?? ''}`)
    watermarkVisible.value = false
    watermarkForm.biz_id = ''
    watermarkForm.file_hash = ''
    await loadWatermarks()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    watermarkSaving.value = false
  }
}

function switchTab(value: string) {
  tab.value = value
  if (value === 'terminals' && terminals.value.length === 0) loadTerminals()
  if (value === 'audit' && auditLogs.value.length === 0) loadAuditLogs()
}

onMounted(loadWatermarks)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="DLP 水印与审计"
      description="水印记录、泄露溯源、终端与审计链；数据来自 /api/legal/v1/admin/dlp/*。"
    >
      <template #actions>
        <el-button v-perm="'legal:dlp:watermark:manage'" type="primary" @click="watermarkVisible = true">生成水印</el-button>
        <el-button v-perm="'legal:dlp:trace'" plain @click="traceVisible = true">泄露溯源</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane label="水印记录" name="watermarks">
        <el-alert v-if="watermarkError" type="error" :closable="false" show-icon :title="watermarkError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="watermarkFilter.biz_type" placeholder="业务类型" clearable style="width: 140px" />
            <el-input v-model="watermarkFilter.trace_status" placeholder="溯源状态" clearable style="width: 130px" />
            <el-button :loading="watermarkLoading" @click="loadWatermarks">查询</el-button>
          </div>
          <el-table :data="watermarks" v-loading="watermarkLoading" border stripe max-height="520">
            <el-table-column prop="watermark_no" label="水印编号" width="180" />
            <el-table-column prop="biz_type" label="业务类型" width="110" />
            <el-table-column prop="biz_id" label="业务ID" width="170" />
            <el-table-column prop="watermark_type" label="水印类型" width="110" />
            <el-table-column prop="embed_code" label="嵌入码" min-width="200" show-overflow-tooltip />
            <el-table-column prop="user_id" label="用户" width="90" />
            <el-table-column prop="terminal_id" label="终端" width="160" show-overflow-tooltip />
            <el-table-column prop="trace_status" label="溯源状态" width="110" />
            <el-table-column label="生成时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.applied_at) }}</template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ watermarkTotal }} 条</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="终端" name="terminals">
        <el-alert v-if="terminalError" type="error" :closable="false" show-icon :title="terminalError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="terminalStatus" placeholder="终端状态" clearable style="width: 140px" />
            <el-button :loading="terminalLoading" @click="loadTerminals">查询</el-button>
          </div>
          <el-table :data="terminals" v-loading="terminalLoading" border stripe max-height="520">
            <el-table-column prop="terminal_no" label="终端编号" width="170" />
            <el-table-column prop="hostname" label="主机名" width="150" />
            <el-table-column prop="user_id" label="绑定用户" width="100" />
            <el-table-column prop="os_type" label="操作系统" width="110" />
            <el-table-column prop="agent_version" label="客户端版本" width="130" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="最近心跳" width="170">
              <template #default="{ row }">{{ fmtTime(row.last_heartbeat_at) }}</template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ terminalTotal }} 条</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="审计日志" name="audit">
        <el-alert v-if="auditError" type="error" :closable="false" show-icon :title="auditError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="auditAction" placeholder="动作" clearable style="width: 140px" />
            <el-button :loading="auditLoading" @click="loadAuditLogs">查询</el-button>
            <el-button v-perm="'legal:dlp:audit:integrity'" type="primary" plain @click="verifyChain">校验审计链</el-button>
          </div>
          <el-descriptions v-if="verifyResult" :column="3" border size="small" style="margin-bottom: 12px">
            <el-descriptions-item label="校验结果">{{ verifyResult.valid === false ? '不一致' : '通过' }}</el-descriptions-item>
            <el-descriptions-item label="校验条数">{{ verifyResult.checked ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="断点序号">{{ verifyResult.broken_at || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="auditLogs" v-loading="auditLoading" border stripe max-height="520">
            <el-table-column prop="seq_no" label="序号" width="90" />
            <el-table-column prop="action" label="动作" width="140" />
            <el-table-column prop="target_type" label="对象类型" width="130" />
            <el-table-column prop="target_id" label="对象ID" width="170" />
            <el-table-column prop="operator_id" label="操作人" width="90" />
            <el-table-column prop="result" label="结果" width="100" />
            <el-table-column prop="prev_hash" label="前序哈希" min-width="160" show-overflow-tooltip />
            <el-table-column prop="block_hash" label="本条哈希" min-width="160" show-overflow-tooltip />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.occurred_at) }}</template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ auditTotal }} 条</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="traceVisible" title="泄露溯源" width="560px">
      <div class="toolbar">
        <el-select v-model="traceMode" style="width: 130px">
          <el-option label="嵌入码" value="embed_code" />
          <el-option label="文件哈希" value="file_hash" />
        </el-select>
        <el-input v-model="traceValue" :placeholder="traceMode === 'embed_code' ? '水印嵌入码' : '文件哈希'" clearable style="flex: 1" @keyup.enter="trace" />
        <el-button type="primary" :loading="traceLoading" @click="trace">溯源</el-button>
      </div>
      <el-descriptions v-if="traceResult" :column="2" border size="small">
        <el-descriptions-item label="水印编号">{{ traceResult.watermark_no ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="溯源状态">{{ traceResult.trace_status ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ traceResult.user_id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="终端">{{ traceResult.terminal_id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ traceResult.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联事件">{{ traceResult.related_event_no || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-if="!traceResult && !traceLoading" description="输入嵌入码或文件哈希后查询" :image-size="60" />
    </el-dialog>

    <el-dialog v-model="watermarkVisible" title="生成水印" width="520px">
      <el-form label-width="110px">
        <el-form-item label="业务类型" required>
          <el-select v-model="watermarkForm.biz_type" style="width: 100%">
            <el-option label="合同" value="CONTRACT" />
            <el-option label="存证" value="EVIDENCE" />
            <el-option label="文件" value="FILE" />
            <el-option label="报表" value="REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID" required><el-input v-model="watermarkForm.biz_id" /></el-form-item>
        <el-form-item label="水印类型">
          <el-select v-model="watermarkForm.watermark_type" style="width: 100%">
            <el-option label="明文" value="TEXT" />
            <el-option label="隐式" value="EMBED" />
            <el-option label="两者" value="BOTH" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件哈希"><el-input v-model="watermarkForm.file_hash" placeholder="可选，溯源用" /></el-form-item>
        <el-form-item label="用户ID"><el-input-number v-model="watermarkForm.user_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="终端ID"><el-input-number v-model="watermarkForm.terminal_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="IP"><el-input v-model="watermarkForm.ip" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="watermarkVisible = false">取消</el-button>
        <el-button type="primary" :loading="watermarkSaving" @click="createWatermark">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.total {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: right;
}
</style>
