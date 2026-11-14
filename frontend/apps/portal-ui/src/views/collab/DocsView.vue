<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabCollaborator,
  type CollabComment,
  type CollabDoc,
  type CollabDocVersion,
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
  '员工端没有文档删除、目录管理、分享链接接口（分享仅管理端提供）。',
  '评论 anchor 无可视化定位，本页按时间顺序展示。',
  '文档正文是纯文本 content。',
]

function fmt(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await collabApi.docs.my({
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

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="文档" description="我创建的文档与详情，支持正文编辑、版本、协作者与评论查看；列表来自 /api/collab/v1/docs/my。">
      <template #actions>
        <el-button v-perm="'biz:collab:doc:edit'" type="primary" @click="openCreate">新建文档</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
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
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ fmt(row.updated_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">打开</el-button>
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
