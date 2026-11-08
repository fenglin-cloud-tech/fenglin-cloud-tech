<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { fileApi, type DownloadResult, type FilePolicyView, type PreviewResult, type UploadResult } from '@/api'
import { describeError } from '@/utils/error'

const activeTab = ref<'policy' | 'operations'>('policy')

const policyLoading = ref(false)
const policySaving = ref(false)
const policyError = ref('')
const policy = reactive<FilePolicyView>({
  allowed_ext: [],
  max_size_mb: 20,
  watermark: { enabled: false, sec_level_from: 3 },
  storage_quota_gb: 100,
})
const allowedExtText = ref('')

const uploadFile = ref<File | null>(null)
const uploading = ref(false)
const uploadError = ref('')
const uploadResult = ref<UploadResult | null>(null)
const uploadMeta = reactive({ biz_type: '', biz_id: '', sec_level: 1 })

const fileId = ref<number | undefined>(undefined)
const previewing = ref(false)
const previewError = ref('')
const preview = ref<PreviewResult | null>(null)
const downloadResult = ref<DownloadResult | null>(null)
const versions = ref<Array<{ ver_no: number; size: number; md5: string; created_at?: string }>>([])
const versionLoading = ref(false)
const versionError = ref('')

async function loadPolicy() {
  policyLoading.value = true
  policyError.value = ''
  try {
    const result = await fileApi.getPolicy()
    Object.assign(policy, result)
    allowedExtText.value = (result.allowed_ext ?? []).join(',')
  } catch (err) {
    policyError.value = describeError(err)
  } finally {
    policyLoading.value = false
  }
}

async function savePolicy() {
  policySaving.value = true
  policyError.value = ''
  try {
    const result = await fileApi.updatePolicy({
      allowed_ext: allowedExtText.value.split(',').map((item) => item.trim()).filter(Boolean),
      max_size_mb: policy.max_size_mb,
      watermark: policy.watermark,
      storage_quota_gb: policy.storage_quota_gb,
    })
    Object.assign(policy, result)
    ElMessage.success('文件策略已保存')
  } catch (err) {
    policyError.value = describeError(err)
  } finally {
    policySaving.value = false
  }
}

function onFileChange(file: { raw?: File }) {
  uploadFile.value = file.raw ?? null
}

async function submitUpload() {
  if (!uploadFile.value) {
    uploadError.value = '请选择文件'
    return
  }
  uploading.value = true
  uploadError.value = ''
  uploadResult.value = null
  try {
    uploadResult.value = await fileApi.upload(uploadFile.value, {
      biz_type: uploadMeta.biz_type || undefined,
      biz_id: uploadMeta.biz_id || undefined,
      sec_level: uploadMeta.sec_level,
    })
    fileId.value = uploadResult.value.file_id
    ElMessage.success('上传成功')
  } catch (err) {
    uploadError.value = describeError(err)
  } finally {
    uploading.value = false
  }
}

async function doPreview() {
  if (!fileId.value) return
  previewing.value = true
  previewError.value = ''
  preview.value = null
  try {
    preview.value = await fileApi.preview(fileId.value)
  } catch (err) {
    previewError.value = describeError(err)
  } finally {
    previewing.value = false
  }
}

async function doDownload() {
  if (!fileId.value) return
  try {
    downloadResult.value = await fileApi.download(fileId.value)
    window.open(downloadResult.value.download_url, '_blank', 'noopener')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openPreviewUrl() {
  if (preview.value?.preview_url) window.open(preview.value.preview_url, '_blank', 'noopener')
}

async function loadVersions() {
  if (!fileId.value) return
  versionLoading.value = true
  versionError.value = ''
  try {
    const result = await fileApi.versions(fileId.value)
    versions.value = result.records
  } catch (err) {
    versionError.value = describeError(err)
  } finally {
    versionLoading.value = false
  }
}

onMounted(loadPolicy)
</script>

<template>
  <div>
    <PageHeader module="文件中台 · A-19" title="文件中心" description="文件策略、上传、预览、下载与版本；MinIO 存储由后端适配。">
      <template #actions>
        <el-button :loading="policyLoading" @click="loadPolicy">刷新策略</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端未提供文件列表/搜索接口，本页只能按 file_id 操作，无法做全量文件治理列表。',
        '分片上传（5MB/片）由内部接口承载，管理台使用单文件上传；病毒扫描状态机未实现（缺少外部服务契约）。',
        '预览 URL / 下载 URL 为 15 分钟签名地址，高危密级水印当前仅返回文本，不做像素级渲染。',
        '存储配额字段单位为 GB，后端策略存于 hub_param 统一参数，无独立策略表。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="文件策略" name="policy">
          <el-alert v-if="policyError" type="error" :closable="false" show-icon :title="policyError" style="margin-bottom: 12px" />
          <el-form v-loading="policyLoading" label-width="150px" style="max-width: 640px">
            <el-form-item label="允许扩展名">
              <el-input v-model="allowedExtText" placeholder="逗号分隔，例如 pdf,docx,xlsx" />
            </el-form-item>
            <el-form-item label="单文件上限（MB）">
              <el-input-number v-model="policy.max_size_mb" :min="1" :max="2048" />
            </el-form-item>
            <el-form-item label="启用下载水印">
              <el-switch v-model="policy.watermark.enabled" />
            </el-form-item>
            <el-form-item label="水印起始密级">
              <el-input-number v-model="policy.watermark.sec_level_from" :min="1" :max="5" />
            </el-form-item>
            <el-form-item label="存储配额（GB）">
              <el-input-number v-model="policy.storage_quota_gb" :min="1" :max="100000" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="policySaving" @click="savePolicy">保存策略</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="上传与文件操作" name="operations">
          <div class="fl-grid fl-grid--2">
            <div class="operation-block">
              <h4>上传文件</h4>
              <el-alert v-if="uploadError" type="error" :closable="false" show-icon :title="uploadError" style="margin-bottom: 12px" />
              <el-form label-width="110px">
                <el-form-item label="选择文件">
                  <el-upload :auto-upload="false" :limit="1" :on-change="onFileChange" :show-file-list="true">
                    <el-button>选择文件</el-button>
                  </el-upload>
                </el-form-item>
                <el-form-item label="业务类型"><el-input v-model="uploadMeta.biz_type" placeholder="可选" /></el-form-item>
                <el-form-item label="业务 ID"><el-input v-model="uploadMeta.biz_id" placeholder="可选" /></el-form-item>
                <el-form-item label="密级">
                  <el-input-number v-model="uploadMeta.sec_level" :min="1" :max="5" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
                </el-form-item>
              </el-form>
              <el-descriptions v-if="uploadResult" :column="1" border size="small">
                <el-descriptions-item label="file_id">{{ uploadResult.file_id }}</el-descriptions-item>
                <el-descriptions-item label="file_no">{{ uploadResult.file_no }}</el-descriptions-item>
                <el-descriptions-item label="status">{{ uploadResult.status }}</el-descriptions-item>
                <el-descriptions-item label="object_key"><span class="fl-mono">{{ uploadResult.object_key }}</span></el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="operation-block">
              <h4>按 file_id 操作</h4>
              <div class="fl-toolbar">
                <el-input-number v-model="fileId" :min="1" placeholder="file_id" />
                <el-button :loading="previewing" @click="doPreview">预览</el-button>
                <el-button @click="doDownload">下载</el-button>
                <el-button :loading="versionLoading" @click="loadVersions">版本</el-button>
              </div>
              <el-alert v-if="previewError" type="error" :closable="false" show-icon :title="previewError" style="margin-bottom: 12px" />
              <div v-if="preview" class="preview-box">
                <p>类型：{{ preview.preview_type }}</p>
                <p class="fl-mono">{{ preview.preview_url }}</p>
                <p v-if="preview.watermark_text">水印文本：{{ preview.watermark_text }}</p>
                <el-button link type="primary" @click="openPreviewUrl">打开预览地址</el-button>
              </div>
              <div v-if="downloadResult" class="fl-muted" style="margin: 8px 0">
                下载地址已生成（水印：{{ downloadResult.watermark ? '是' : '否' }}）
              </div>
              <el-alert v-if="versionError" type="error" :closable="false" show-icon :title="versionError" style="margin: 12px 0" />
              <el-table v-if="versions.length" :data="versions" size="small" border>
                <el-table-column prop="ver_no" label="版本" width="80" />
                <el-table-column prop="size" label="大小(B)" width="110" />
                <el-table-column prop="md5" label="MD5" min-width="220" class-name="fl-mono" />
                <el-table-column label="创建时间" width="170">
                  <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else-if="!versionLoading && !versionError" description="输入 file_id 后查询版本" :image-size="60" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.operation-block {
  padding: 8px;
  border: 1px solid var(--fl-color-border);
  border-radius: var(--fl-radius-md);
}
.operation-block h4 {
  margin: 0 0 12px;
  font-size: 14px;
}
.preview-box {
  padding: 12px;
  background: #f8fafc;
  border-radius: var(--fl-radius-sm);
  word-break: break-all;
}
.preview-box p {
  margin: 4px 0;
  font-size: 12px;
}
</style>
