<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabCollaborator,
  type CollabComment,
  type CollabDoc,
  type CollabDocFolder,
  type CollabDocVersion,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const rows = ref<CollabDoc[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const scope = ref<'mine' | 'shared'>('mine')
const query = reactive({ keyword: '', page: 1, size: 10 })

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({ title: '', doc_type: 'RICH', secret_level: 'INTERNAL' })

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

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailSaving = ref(false)
const detailDoc = ref<CollabDoc | null>(null)
const detailForm = reactive({ title: '', content: '' })
const editing = ref(false)
const activeTab = ref('content')

const versions = ref<CollabDocVersion[]>([])
const versionTotal = ref(0)
const versionLoading = ref(false)
const versionError = ref('')
const versionQuery = reactive({ page: 1, size: 10 })
const versionsLoaded = ref(false)

const collaborators = ref<CollabCollaborator[]>([])
const collabLoading = ref(false)
const collabError = ref('')
const collabLoaded = ref(false)

const comments = ref<CollabComment[]>([])
const commentLoading = ref(false)
const commentError = ref('')
const commentDraft = ref('')
const commentSubmitting = ref(false)
const commentsLoaded = ref(false)

const canEdit = computed(() => {
  const permission = detailDoc.value?.my_permission
  return permission === 'EDIT' || permission === 'MANAGE'
})

const gapItems = [
  '目录管理已接入：可新建/重命名/删除自己的目录，把文档归入或移出目录；目录非空时禁止删除。',
  '分享链接：文档所有者可用 self:share 生成带有效期的同租户链接（1-365 天），过期返回 2002009。',
  '评论 anchor 无可视化定位，本页按时间顺序展示。',
  '文档正文维持纯文本（换行保留），富文本编辑器放二期。',
]

const folders = ref<CollabDocFolder[]>([])
const foldersLoading = ref(false)
const folderDialogVisible = ref(false)
const folderEditing = ref<CollabDocFolder | null>(null)
const folderForm = reactive({ name: '' })
const folderSaving = ref(false)

const shareVisible = ref(false)
const shareLoading = ref(false)
const shareLink = ref('')
const shareExpire = ref('')

function folderName(id?: string | null) {
  if (!id) return '未归类'
  return folders.value.find((item) => item.folder_id === id)?.name ?? '未归类'
}

async function loadFolders() {
  foldersLoading.value = true
  try {
    folders.value = await collabApi.docs.folders()
  } catch (err) {
    console.warn('folders failed', err)
    folders.value = []
  } finally {
    foldersLoading.value = false
  }
}

function openFolderDialog(folder?: CollabDocFolder) {
  folderEditing.value = folder ?? null
  folderForm.name = folder?.name ?? ''
  folderDialogVisible.value = true
}

async function submitFolder() {
  if (!folderForm.name.trim()) {
    ElMessage.warning('目录名不能为空')
    return
  }
  folderSaving.value = true
  try {
    if (folderEditing.value) {
      await collabApi.docs.renameFolder(folderEditing.value.folder_id, folderForm.name.trim())
      ElMessage.success('目录已重命名')
    } else {
      await collabApi.docs.createFolder({ name: folderForm.name.trim() })
      ElMessage.success('目录已创建')
    }
    folderDialogVisible.value = false
    await loadFolders()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    folderSaving.value = false
  }
}

async function removeFolder(folder: CollabDocFolder) {
  try {
    await ElMessageBox.confirm(`确认删除目录「${folder.name}」？只有空目录可以删除。`, '删除目录', { type: 'warning' })
  } catch {
    return
  }
  try {
    await collabApi.docs.deleteFolder(folder.folder_id)
    ElMessage.success('目录已删除')
    await loadFolders()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function moveToFolder(row: CollabDoc, folderId: string) {
  try {
    if (!folderId) {
      if (!row.folder_id) return
      await collabApi.docs.unfileDoc(row.folder_id, row.doc_id)
      ;(row as CollabDoc & { folder_name?: string }).folder_name = '未归类'
      row.folder_id = null
      ElMessage.success('已移出目录')
    } else {
      await collabApi.docs.moveDocToFolder(folderId, row.doc_id)
      row.folder_id = folderId
      ;(row as CollabDoc & { folder_name?: string }).folder_name = folderName(folderId)
      ElMessage.success('已归入目录')
    }
    await loadFolders()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function createShareLink() {
  if (!detailDoc.value) return
  shareLoading.value = true
  try {
    const link = await collabApi.docs.shareLink(detailDoc.value.doc_id, { expire_days: 7 })
    shareLink.value = `${window.location.origin}/#/docs/share/${link.link_token}`
    shareExpire.value = fmt(link.expire_at)
    shareVisible.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    shareLoading.value = false
  }
}

async function copyShareLink() {
  try {
    await navigator.clipboard.writeText(shareLink.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

function fmt(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const api = scope.value === 'shared' ? collabApi.docs.shared : collabApi.docs.my
    const result = await api({
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function changeScope() {
  query.page = 1
  void load()
}

function openCreate() {
  Object.assign(createForm, { title: '', doc_type: 'RICH', secret_level: 'INTERNAL' })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.title.trim()) {
    createError.value = '文档标题为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await collabApi.docs.create({
      title: createForm.title.trim(),
      doc_type: createForm.doc_type,
      secret_level: createForm.secret_level,
    })
    ElMessage.success('文档已创建')
    createVisible.value = false
    query.page = 1
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

function resetDrawerState() {
  detailError.value = ''
  detailDoc.value = null
  editing.value = false
  activeTab.value = 'content'
  versions.value = []
  versionTotal.value = 0
  versionQuery.page = 1
  versionError.value = ''
  versionsLoaded.value = false
  collaborators.value = []
  collabError.value = ''
  collabLoaded.value = false
  comments.value = []
  commentError.value = ''
  commentDraft.value = ''
  commentsLoaded.value = false
}

async function openDetail(row: CollabDoc) {
  drawerVisible.value = true
  resetDrawerState()
  detailLoading.value = true
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

function startEdit() {
  if (!detailDoc.value) return
  detailForm.title = detailDoc.value.title
  detailForm.content = detailDoc.value.content ?? ''
  editing.value = true
}

function cancelEdit() {
  if (!detailDoc.value) return
  detailForm.title = detailDoc.value.title
  detailForm.content = detailDoc.value.content ?? ''
  editing.value = false
}

async function saveDetail() {
  if (!detailDoc.value) return
  if (!detailForm.title.trim()) {
    detailError.value = '文档标题为必填项'
    return
  }
  detailSaving.value = true
  detailError.value = ''
  try {
    const doc = await collabApi.docs.update(detailDoc.value.doc_id, {
      title: detailForm.title.trim(),
      content: detailForm.content,
      version: detailDoc.value.current_version,
    })
    detailDoc.value = doc
    detailForm.title = doc.title
    detailForm.content = doc.content ?? ''
    editing.value = false
    ElMessage.success('文档已保存')
    await load()
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailSaving.value = false
  }
}

async function loadVersions() {
  if (!detailDoc.value) return
  versionLoading.value = true
  versionError.value = ''
  try {
    const result = await collabApi.docs.versions(detailDoc.value.doc_id, versionQuery)
    versions.value = result.records
    versionTotal.value = result.total
    versionsLoaded.value = true
  } catch (err) {
    versionError.value = describeError(err)
  } finally {
    versionLoading.value = false
  }
}

async function loadCollaborators() {
  if (!detailDoc.value) return
  collabLoading.value = true
  collabError.value = ''
  try {
    collaborators.value = await collabApi.docs.collaborators(detailDoc.value.doc_id)
    collabLoaded.value = true
  } catch (err) {
    collabError.value = describeError(err)
  } finally {
    collabLoading.value = false
  }
}

async function loadComments() {
  if (!detailDoc.value) return
  commentLoading.value = true
  commentError.value = ''
  try {
    comments.value = await collabApi.docs.comments(detailDoc.value.doc_id)
    commentsLoaded.value = true
  } catch (err) {
    commentError.value = describeError(err)
  } finally {
    commentLoading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'versions' && !versionsLoaded.value) void loadVersions()
  if (name === 'collaborators' && !collabLoaded.value) void loadCollaborators()
  if (name === 'comments' && !commentsLoaded.value) void loadComments()
}

async function submitComment() {
  if (!detailDoc.value) return
  const content = commentDraft.value.trim()
  if (!content) {
    commentError.value = '评论内容不能为空'
    return
  }
  commentSubmitting.value = true
  commentError.value = ''
  try {
    await collabApi.docs.createComment(detailDoc.value.doc_id, { content })
    commentDraft.value = ''
    ElMessage.success('评论已发布')
    await loadComments()
  } catch (err) {
    commentError.value = describeError(err)
  } finally {
    commentSubmitting.value = false
  }
}

onMounted(() => {
  void load()
  void loadFolders()
})
</script>

<template>
  <div>
    <PageHeader title="文档" description="我创建的与共享给我的文档；详情支持正文查看、版本、协作者与评论。">
      <template #actions>
        <el-button v-perm="'biz:collab:doc:edit'" type="primary" @click="openCreate">新建文档</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px" v-loading="foldersLoading">
      <div class="fl-toolbar">
        <span class="fl-muted">我的目录：</span>
        <el-tag v-for="folder in folders" :key="folder.folder_id" size="small" type="info" style="margin-right: 6px">
          {{ folder.name }}（{{ folder.doc_count ?? 0 }}）
        </el-tag>
        <span v-if="!folders.length" class="fl-muted">暂无目录</span>
        <el-button size="small" type="primary" plain @click="openFolderDialog()">新建目录</el-button>
      </div>
      <el-table v-if="folders.length" :data="folders" size="small" border style="margin-top: 8px">
        <el-table-column prop="name" label="目录" min-width="200" />
        <el-table-column label="文档数" width="100">
          <template #default="{ row }">{{ row.doc_count ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFolderDialog(row)">重命名</el-button>
            <el-button link type="danger" @click="removeFolder(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-radio-group v-model="scope" @change="changeScope">
          <el-radio-button value="mine">我创建的</el-radio-button>
          <el-radio-button value="shared">共享给我</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="query.keyword"
          placeholder="文档标题关键词"
          clearable
          style="width: 240px"
          @keyup.enter="query.page = 1; load()"
        />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.keyword = ''; query.page = 1; load()">重置</el-button>
        <span class="fl-muted">共 {{ total }} 篇</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe row-key="doc_id">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="doc_type" label="类型" width="90" />
        <el-table-column label="密级" width="130">
          <template #default="{ row }">
            <el-tag
              :type="row.secret_level === 'CONFIDENTIAL' ? 'danger' : row.secret_level === 'INTERNAL' ? 'warning' : 'success'"
              size="small"
            >
              {{ row.secret_level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前版本" width="100">
          <template #default="{ row }">v{{ row.current_version }}</template>
        </el-table-column>
        <el-table-column prop="my_permission" label="我的权限" width="110" />
        <el-table-column label="所属目录" width="130">
          <template #default="{ row }">{{ row.folder_name ?? folderName(row.folder_id) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ fmt(row.updated_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">打开</el-button>
            <el-dropdown v-if="folders.length" trigger="click" @command="(folderId: string) => moveToFolder(row, folderId)">
              <el-button link type="primary">归入目录</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="folder in folders" :key="folder.folder_id" :command="folder.folder_id">
                    {{ folder.name }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.folder_id" command="" divided>移出目录（未归类）</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
    </el-card>

    <el-dialog v-model="createVisible" title="新建文档" width="520px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" maxlength="128" placeholder="文档标题" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="createForm.doc_type" style="width: 100%">
            <el-option v-for="opt in docTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="密级">
          <el-select v-model="createForm.secret_level" style="width: 100%">
            <el-option v-for="opt in secretLevelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button v-perm="'biz:collab:doc:edit'" type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="folderDialogVisible" :title="folderEditing ? '重命名目录' : '新建目录'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="目录名" required>
          <el-input v-model="folderForm.name" maxlength="64" placeholder="例如：2026 年度制度" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="folderSaving" @click="submitFolder">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shareVisible" title="分享链接" width="560px">
      <el-alert type="info" :closable="false" show-icon title="链接仅同租户内可访问，7 天后自动失效。" style="margin-bottom: 12px" />
      <el-input v-model="shareLink" readonly />
      <div class="fl-muted" style="margin-top: 8px">过期时间：{{ shareExpire }}</div>
      <template #footer>
        <el-button @click="shareVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyShareLink">复制链接</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="文档详情" size="760px">
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
            <el-descriptions-item label="更新时间">{{ fmt(detailDoc.updated_at) }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="String(detailDoc.owner_id) === String(auth.userId)" style="margin-top: 8px">
            <el-button v-perm="'biz:collab:doc:self:share'" size="small" :loading="shareLoading" @click="createShareLink">
              生成分享链接（7 天有效）
            </el-button>
          </div>

          <el-tabs v-model="activeTab" style="margin-top: 16px" @tab-change="onTabChange">
            <el-tab-pane label="正文" name="content">
              <div class="fl-toolbar">
                <el-input v-model="detailForm.title" :readonly="!editing" maxlength="128" placeholder="文档标题" style="flex: 1" />
                <template v-if="canEdit">
                  <el-button
                    v-if="!editing"
                    v-perm="'biz:collab:doc:edit'"
                    type="primary"
                    plain
                    @click="startEdit"
                  >
                    编辑
                  </el-button>
                  <template v-else>
                    <el-button @click="cancelEdit">取消</el-button>
                    <el-button v-perm="'biz:collab:doc:edit'" type="primary" :loading="detailSaving" @click="saveDetail">
                      保存
                    </el-button>
                  </template>
                </template>
              </div>
              <el-input
                v-model="detailForm.content"
                type="textarea"
                :rows="18"
                :readonly="!editing"
                placeholder="暂无正文"
                style="margin-top: 12px"
              />
              <div class="fl-muted" style="margin-top: 8px">
                纯文本正文；保存携带当前版本号 {{ detailDoc.current_version }} 做乐观锁校验。
              </div>
            </el-tab-pane>

            <el-tab-pane label="版本" name="versions">
              <el-alert v-if="versionError" type="error" :closable="false" show-icon :title="versionError" style="margin-bottom: 12px" />
              <el-table :data="versions" v-loading="versionLoading" border size="small" row-key="version_no">
                <el-table-column label="版本号" width="100">
                  <template #default="{ row }">v{{ row.version_no }}</template>
                </el-table-column>
                <el-table-column prop="version_type" label="类型" width="120" />
                <el-table-column prop="editor_id" label="编辑人" width="110">
                  <template #default="{ row }">{{ row.editor_id ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="创建时间" min-width="170">
                  <template #default="{ row }">{{ fmt(row.created_at) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!versionLoading && !versions.length" description="暂无版本记录" :image-size="60" />
              <el-pagination
                v-model:current-page="versionQuery.page"
                v-model:page-size="versionQuery.size"
                :total="versionTotal"
                layout="total, prev, pager, next"
                style="margin-top: 12px; justify-content: flex-end"
                @change="loadVersions"
              />
            </el-tab-pane>

            <el-tab-pane label="协作者" name="collaborators">
              <el-alert v-if="collabError" type="error" :closable="false" show-icon :title="collabError" style="margin-bottom: 12px" />
              <el-table :data="collaborators" v-loading="collabLoading" border size="small" row-key="id">
                <el-table-column prop="id" label="ID" width="90" />
                <el-table-column prop="subject_type" label="主体类型" width="120" />
                <el-table-column prop="subject_id" label="主体 ID" min-width="140" />
                <el-table-column prop="permission" label="权限" width="120" />
                <el-table-column label="授权人" width="110">
                  <template #default="{ row }">{{ row.granted_by ?? '-' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!collabLoading && !collaborators.length" description="暂无协作者" :image-size="60" />
              <div class="fl-muted" style="margin-top: 8px">员工端仅查看协作者，不提供授权操作。</div>
            </el-tab-pane>

            <el-tab-pane label="评论" name="comments">
              <el-alert v-if="commentError" type="error" :closable="false" show-icon :title="commentError" style="margin-bottom: 12px" />
              <div v-loading="commentLoading">
                <el-empty v-if="!comments.length" description="暂无评论" :image-size="60" />
                <div v-for="item in comments" :key="item.comment_id" class="comment-item">
                  <div class="comment-meta">
                    <span>用户 {{ item.author_id }}</span>
                    <span>{{ fmt(item.created_at) }}</span>
                    <el-tag v-if="item.resolved" size="small" type="success">已解决</el-tag>
                  </div>
                  <div class="comment-body">{{ item.content }}</div>
                </div>
              </div>
              <el-input
                v-model="commentDraft"
                type="textarea"
                :rows="3"
                maxlength="1000"
                placeholder="输入评论内容"
                style="margin-top: 12px"
              />
              <div class="comment-actions">
                <el-button
                  v-perm="'biz:collab:doc:comment'"
                  type="primary"
                  size="small"
                  :loading="commentSubmitting"
                  @click="submitComment"
                >
                  发表评论
                </el-button>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--fl-color-border-light, #ebeef5);
}
.comment-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--fl-color-text-secondary);
}
.comment-body {
  margin-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}
.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
