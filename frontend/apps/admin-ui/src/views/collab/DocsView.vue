<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabCollaborator,
  type CollabDoc,
  type CollabDocPolicy,
  type CollabDocVersion,
  type CollabShareLink,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<CollabDoc[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ keyword: '', page: 1, size: 10 })

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({ title: '', doc_type: 'RICH', secret_level: 'INTERNAL', folder_id: '' })

const docTypeOptions = [
  { value: 'RICH', label: 'RICH' },
  { value: 'SHEET', label: 'SHEET' },
  { value: 'KANBAN', label: 'KANBAN' },
]
const secretLevelOptions = [
  { value: 'PUBLIC', label: 'PUBLIC' },
  { value: 'INTERNAL', label: 'INTERNAL' },
  { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL' },
]

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailSaving = ref(false)
const detailDoc = ref<CollabDoc | null>(null)
const detailForm = reactive({ title: '', content: '' })

const canEdit = computed(() => {
  const permission = detailDoc.value?.my_permission
  return permission === 'EDIT' || permission === 'MANAGE'
})

const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versionsError = ref('')
const versionsDoc = ref<CollabDoc | null>(null)
const versions = ref<CollabDocVersion[]>([])
const versionTotal = ref(0)
const versionQuery = reactive({ page: 1, size: 10 })

const collabVisible = ref(false)
const collabLoading = ref(false)
const collabError = ref('')
const collabDoc = ref<CollabDoc | null>(null)
const collaborators = ref<CollabCollaborator[]>([])
const addingCollaborator = ref(false)
const addError = ref('')
const collabForm = reactive({ subject_type: 'USER', subject_id: '', permission: 'READ' })
const subjectTypeOptions = ['USER', 'DEPT', 'ROLE']
const permissionOptions = ['READ', 'COMMENT', 'EDIT', 'MANAGE']

const shareVisible = ref(false)
const shareCreating = ref(false)
const shareError = ref('')
const shareDoc = ref<CollabDoc | null>(null)
const shareExpireDays = ref(7)
const shareResult = ref<CollabShareLink | null>(null)

const policyLoading = ref(false)
const policySaving = ref(false)
const policyError = ref('')
const policyForm = reactive<CollabDocPolicy>({
  confidential_no_link: false,
  confidential_no_download: false,
  watermark_template: '',
  max_concurrent_editors: 1,
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await collabApi.docs.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.page = 1
  void load()
}

async function loadPolicy() {
  policyLoading.value = true
  policyError.value = ''
  try {
    Object.assign(policyForm, await collabApi.admin.docPolicy())
  } catch (err) {
    policyError.value = describeError(err)
  } finally {
    policyLoading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { title: '', doc_type: 'RICH', secret_level: 'INTERNAL', folder_id: '' })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.title) {
    createError.value = '文档标题为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await collabApi.docs.create({
      title: createForm.title,
      doc_type: createForm.doc_type,
      secret_level: createForm.secret_level,
      folder_id: createForm.folder_id || undefined,
    })
    ElMessage.success('文档已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function openDetail(row: CollabDoc) {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detailDoc.value = null
  try {
    const doc = await collabApi.docs.detail(row.doc_id)
    detailDoc.value = doc
    detailForm.title = doc.title
    detailForm.content = doc.content ?? ''
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

async function saveDetail() {
  if (!detailDoc.value) return
  if (!detailForm.title) {
    detailError.value = '文档标题为必填项'
    return
  }
  detailSaving.value = true
  detailError.value = ''
  try {
    const doc = await collabApi.docs.update(detailDoc.value.doc_id, {
      title: detailForm.title,
      content: detailForm.content,
      version: detailDoc.value.current_version,
    })
    detailDoc.value = doc
    detailForm.title = doc.title
    detailForm.content = doc.content ?? ''
    ElMessage.success('文档已保存')
    await load()
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailSaving.value = false
  }
}

function openVersions(row: CollabDoc) {
  versionsDoc.value = row
  versions.value = []
  versionTotal.value = 0
  versionQuery.page = 1
  versionsError.value = ''
  versionsVisible.value = true
  void loadVersions()
}

async function loadVersions() {
  if (!versionsDoc.value) return
  versionsLoading.value = true
  versionsError.value = ''
  try {
    const result = await collabApi.docs.versions(versionsDoc.value.doc_id, versionQuery)
    versions.value = result.records
    versionTotal.value = result.total
  } catch (err) {
    versionsError.value = describeError(err)
  } finally {
    versionsLoading.value = false
  }
}

async function createSnapshot() {
  if (!versionsDoc.value) return
  versionsLoading.value = true
  versionsError.value = ''
  try {
    const result = await collabApi.docs.createVersion(versionsDoc.value.doc_id, { version_type: 'MANUAL' })
    ElMessage.success(`已创建快照 v${result.version_no}`)
    await loadVersions()
    await load()
  } catch (err) {
    versionsError.value = describeError(err)
  } finally {
    versionsLoading.value = false
  }
}

async function rollbackVersion(row: CollabDocVersion) {
  const doc = versionsDoc.value
  if (!doc) return
  const docId = doc.doc_id
  try {
    await ElMessageBox.confirm(`确认将文档回滚到版本 v${row.version_no}？`, '版本回滚', { type: 'warning' })
    const result = await collabApi.docs.rollback(docId, row.version_no)
    ElMessage.success(`已回滚到 v${row.version_no}，当前版本 v${result.current_version}`)
    await loadVersions()
    await load()
    if (detailDoc.value?.doc_id === docId) await openDetail(doc)
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

function openCollaborators(row: CollabDoc) {
  collabDoc.value = row
  collaborators.value = []
  collabForm.subject_type = 'USER'
  collabForm.subject_id = ''
  collabForm.permission = 'READ'
  addError.value = ''
  collabError.value = ''
  collabVisible.value = true
  void loadCollaborators()
}

async function loadCollaborators() {
  if (!collabDoc.value) return
  collabLoading.value = true
  collabError.value = ''
  try {
    collaborators.value = await collabApi.docs.collaborators(collabDoc.value.doc_id)
  } catch (err) {
    collabError.value = describeError(err)
  } finally {
    collabLoading.value = false
  }
}

async function addCollaborator() {
  if (!collabDoc.value) return
  const subjectId = Number(collabForm.subject_id)
  if (!collabForm.subject_id || !Number.isInteger(subjectId) || subjectId <= 0) {
    addError.value = '主体 ID 需为正整数'
    return
  }
  addingCollaborator.value = true
  addError.value = ''
  try {
    await collabApi.docs.addCollaborator(collabDoc.value.doc_id, {
      subject_type: collabForm.subject_type,
      subject_id: subjectId,
      permission: collabForm.permission,
    })
    ElMessage.success('协作者已添加')
    collabForm.subject_id = ''
    await loadCollaborators()
  } catch (err) {
    addError.value = describeError(err)
  } finally {
    addingCollaborator.value = false
  }
}

function openShare(row: CollabDoc) {
  shareDoc.value = row
  shareResult.value = null
  shareError.value = ''
  shareExpireDays.value = 7
  shareVisible.value = true
}

async function createShareLink() {
  if (!shareDoc.value) return
  shareCreating.value = true
  shareError.value = ''
  try {
    shareResult.value = await collabApi.docs.shareLink(shareDoc.value.doc_id, {
      expire_days: shareExpireDays.value,
    })
  } catch (err) {
    shareError.value = describeError(err)
  } finally {
    shareCreating.value = false
  }
}

async function copyShareLink() {
  if (!shareResult.value?.url) return
  try {
    await navigator.clipboard.writeText(shareResult.value.url)
    ElMessage.success('分享链接已复制')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function savePolicy() {
  policySaving.value = true
  policyError.value = ''
  try {
    Object.assign(
      policyForm,
      await collabApi.admin.saveDocPolicy({
        confidential_no_link: policyForm.confidential_no_link,
        confidential_no_download: policyForm.confidential_no_download,
        watermark_template: policyForm.watermark_template,
        max_concurrent_editors: policyForm.max_concurrent_editors,
      }),
    )
    ElMessage.success('文档密级策略已保存')
  } catch (err) {
    policyError.value = describeError(err)
  } finally {
    policySaving.value = false
  }
}

onMounted(() => {
  void Promise.allSettled([load(), loadPolicy()])
})
</script>

<template>
  <div>
    <PageHeader
      module="云协 Collab"
      title="文档中心"
      description="文档列表、版本快照、协作者授权与分享链接；正文为纯文本 content。"
    >
      <template #actions>
        <el-button v-perm="'biz:collab:doc:edit'" type="primary" @click="openCreate">新建文档</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端没有文档删除、目录树、全文检索接口；列表只有 keyword 过滤。',
        '文档正文是纯文本 content，没有富文本块/协同光标结构。',
        '协作者权限码来自字典 collab_doc_permission，页面枚举以后端字典为准。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="文档标题关键词" clearable style="width: 240px" @keyup.enter="query.page = 1; load()" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe row-key="doc_id">
        <el-table-column prop="doc_id" label="文档 ID" min-width="170" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="doc_type" label="类型" width="90" />
        <el-table-column prop="secret_level" label="密级" width="130">
          <template #default="{ row }">
            <el-tag :type="row.secret_level === 'CONFIDENTIAL' ? 'danger' : row.secret_level === 'INTERNAL' ? 'warning' : 'success'" size="small">
              {{ row.secret_level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="current_version" label="当前版本" width="100">
          <template #default="{ row }">v{{ row.current_version }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="my_permission" label="我的权限" width="110" />
        <el-table-column prop="owner_id" label="所有者" width="90" />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ row.updated_at ? new Date(row.updated_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openVersions(row)">版本</el-button>
            <el-button link type="primary" @click="openCollaborators(row)">协作者</el-button>
            <el-button v-perm="'biz:collab:doc:manage'" link type="warning" @click="openShare(row)">分享</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <div v-perm="'tenant:collab:doc:policy'" class="fl-card policy-card" v-loading="policyLoading">
      <div class="section-head">
        <h3>文档密级策略</h3>
        <el-button type="primary" :loading="policySaving" @click="savePolicy">保存策略</el-button>
      </div>
      <el-alert v-if="policyError" type="error" :closable="false" show-icon :title="policyError" style="margin-bottom: 12px" />
      <el-form label-width="180px" style="max-width: 680px">
        <el-form-item label="机密文档禁止分享链接">
          <el-switch v-model="policyForm.confidential_no_link" />
        </el-form-item>
        <el-form-item label="机密文档禁止下载">
          <el-switch v-model="policyForm.confidential_no_download" />
        </el-form-item>
        <el-form-item label="水印模板">
          <el-input v-model="policyForm.watermark_template" placeholder="留空表示不启用水印" />
        </el-form-item>
        <el-form-item label="最大并发编辑人数">
          <el-input-number v-model="policyForm.max_concurrent_editors" :min="1" :max="100" />
        </el-form-item>
      </el-form>
    </div>

    <el-dialog v-model="createVisible" title="新建文档" width="520px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="createForm.doc_type" style="width: 100%">
            <el-option v-for="opt in docTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <div class="fl-muted">枚举以后端为准</div>
        </el-form-item>
        <el-form-item label="密级">
          <el-select v-model="createForm.secret_level" style="width: 100%">
            <el-option v-for="opt in secretLevelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目录 ID">
          <el-input v-model="createForm.folder_id" placeholder="可选，字符串透传" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="文档详情" size="720px">
      <div v-loading="detailLoading">
        <el-alert v-if="detailError" type="error" :closable="false" show-icon :title="detailError" style="margin-bottom: 12px" />
        <template v-if="detailDoc">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="文档 ID">{{ detailDoc.doc_id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detailDoc.status }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detailDoc.doc_type }}</el-descriptions-item>
            <el-descriptions-item label="密级">{{ detailDoc.secret_level }}</el-descriptions-item>
            <el-descriptions-item label="当前版本">v{{ detailDoc.current_version }}</el-descriptions-item>
            <el-descriptions-item label="我的权限">{{ detailDoc.my_permission }}</el-descriptions-item>
            <el-descriptions-item label="所有者">{{ detailDoc.owner_id }}</el-descriptions-item>
            <el-descriptions-item label="共享范围">{{ detailDoc.share_scope }}</el-descriptions-item>
            <el-descriptions-item label="水印">{{ detailDoc.watermark_enabled ? '启用' : '未启用' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ detailDoc.updated_at ? new Date(detailDoc.updated_at).toLocaleString() : '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <h3>标题与正文</h3>
            <el-button v-if="canEdit" v-perm="'biz:collab:doc:edit'" type="primary" size="small" :loading="detailSaving" @click="saveDetail">
              保存
            </el-button>
          </div>
          <el-input v-model="detailForm.title" :disabled="!canEdit" maxlength="128" style="margin-bottom: 12px" />
          <el-input v-model="detailForm.content" type="textarea" :rows="16" :readonly="!canEdit" placeholder="纯文本正文" />
          <div class="fl-muted" style="margin-top: 8px">
            仅「编辑/管理」权限可修改；保存会携带当前版本号 {{ detailDoc.current_version }} 做乐观锁校验。
          </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="versionsVisible" title="版本历史" width="720px">
      <div v-if="versionsDoc" class="fl-muted" style="margin-bottom: 12px">
        文档：{{ versionsDoc.title }}（{{ versionsDoc.doc_id }}）
      </div>
      <div class="fl-toolbar">
        <el-button v-perm="'biz:collab:doc:edit'" type="primary" plain :loading="versionsLoading" @click="createSnapshot">创建快照</el-button>
        <el-button @click="loadVersions">刷新</el-button>
      </div>
      <el-alert v-if="versionsError" type="error" :closable="false" show-icon :title="versionsError" style="margin-bottom: 12px" />
      <el-table :data="versions" v-loading="versionsLoading" border size="small" row-key="version_no">
        <el-table-column prop="version_no" label="版本号" width="90">
          <template #default="{ row }">v{{ row.version_no }}</template>
        </el-table-column>
        <el-table-column prop="version_type" label="类型" width="110" />
        <el-table-column prop="editor_id" label="编辑人" width="100" />
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-perm="'biz:collab:doc:manage'" link type="warning" @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="versionQuery.page"
        v-model:page-size="versionQuery.size"
        :total="versionTotal"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="loadVersions"
      />
    </el-dialog>

    <el-dialog v-model="collabVisible" title="协作者" width="760px">
      <div v-if="collabDoc" class="fl-muted" style="margin-bottom: 12px">
        文档：{{ collabDoc.title }}（{{ collabDoc.doc_id }}）
      </div>
      <el-alert v-if="collabError" type="error" :closable="false" show-icon :title="collabError" style="margin-bottom: 12px" />
      <el-table :data="collaborators" v-loading="collabLoading" border size="small" row-key="id">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="subject_type" label="主体类型" width="110" />
        <el-table-column prop="subject_id" label="主体 ID" min-width="130" />
        <el-table-column prop="permission" label="权限" width="110" />
        <el-table-column prop="granted_by" label="授权人" width="100" />
      </el-table>

      <div class="section-head"><h3>新增协作者</h3></div>
      <el-alert v-if="addError" type="error" :closable="false" show-icon :title="addError" style="margin-bottom: 12px" />
      <div class="fl-toolbar">
        <el-select v-model="collabForm.subject_type" style="width: 130px">
          <el-option v-for="type in subjectTypeOptions" :key="type" :label="type" :value="type" />
        </el-select>
        <el-input v-model="collabForm.subject_id" placeholder="主体 ID" style="width: 180px" />
        <el-select v-model="collabForm.permission" style="width: 130px">
          <el-option v-for="perm in permissionOptions" :key="perm" :label="perm" :value="perm" />
        </el-select>
        <el-button v-perm="'biz:collab:doc:manage'" type="primary" :loading="addingCollaborator" @click="addCollaborator">添加</el-button>
      </div>
      <div class="fl-muted" style="margin-top: 8px">权限码来自字典 collab_doc_permission，枚举以后端为准。</div>
    </el-dialog>

    <el-dialog v-model="shareVisible" title="分享链接" width="560px">
      <div v-if="shareDoc" class="fl-muted" style="margin-bottom: 12px">
        文档：{{ shareDoc.title }}（{{ shareDoc.doc_id }}）
      </div>
      <el-alert v-if="shareError" type="error" :closable="false" show-icon :title="shareError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="有效期（天）">
          <el-input-number v-model="shareExpireDays" :min="1" :max="365" />
        </el-form-item>
      </el-form>
      <el-button v-perm="'biz:collab:doc:manage'" type="primary" :loading="shareCreating" @click="createShareLink">生成链接</el-button>
      <template v-if="shareResult">
        <el-descriptions :column="1" border size="small" style="margin-top: 16px">
          <el-descriptions-item label="链接">
            <span class="fl-mono">{{ shareResult.url }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="过期时间">
            {{ shareResult.expire_at ? new Date(shareResult.expire_at).toLocaleString() : '未设置' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-button style="margin-top: 12px" @click="copyShareLink">复制链接</el-button>
      </template>
      <template #footer>
        <el-button @click="shareVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.section-head h3 {
  margin: 0;
  font-size: 14px;
}
.policy-card {
  margin-top: var(--fl-space-md);
}
</style>
