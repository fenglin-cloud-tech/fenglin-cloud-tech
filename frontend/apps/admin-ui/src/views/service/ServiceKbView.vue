<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const categories = ref<ServiceRecord[]>([])
const articles = ref<ServiceRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ status: '', categoryId: '' as number | '', keyword: '' })
const loading = ref(false)
const error = ref('')

const categoryVisible = ref(false)
const categorySaving = ref(false)
const categoryError = ref('')
const categoryForm = reactive({ name: '', parent_id: '', sort_no: 1 })

const articleVisible = ref(false)
const articleSaving = ref(false)
const articleError = ref('')
const articleForm = reactive({
  id: '',
  category_id: '' as number | '',
  title: '',
  summary: '',
  content: '',
  status: 'DRAFT',
  visibility: 'ALL',
  keywords: '',
})

const gapItems = [
  '知识库文章列表为 PageResult，支持状态/分类/关键字过滤。',
  '没有文章删除接口，只能通过状态（ARCHIVED）下架。',
  '反馈统计只在文章详情内累加 helpful_count，缺少独立反馈明细接口。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [categoryResult, articleResult] = await Promise.all([
      serviceApi.kb.categories().catch(() => [] as ServiceRecord[]),
      serviceApi.kb.articles({
        status: filters.status || undefined,
        categoryId: filters.categoryId ? filters.categoryId : undefined,
        keyword: filters.keyword || undefined,
        page: page.value,
        size: size.value,
      }),
    ])
    categories.value = categoryResult
    articles.value = articleResult.records
    total.value = articleResult.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openArticle(row?: ServiceRecord) {
  if (row) {
    articleForm.id = String(row.id)
    articleForm.category_id = row.category_id ?? ''
    articleForm.title = row.title ?? ''
    articleForm.summary = row.summary ?? ''
    articleForm.content = row.content ?? ''
    articleForm.status = row.status ?? 'DRAFT'
    articleForm.visibility = row.visibility ?? 'ALL'
    articleForm.keywords = Array.isArray(row.keywords) ? row.keywords.join(',') : ''
  } else {
    articleForm.id = ''
    articleForm.title = ''
    articleForm.summary = ''
    articleForm.content = ''
    articleForm.status = 'DRAFT'
    articleForm.keywords = ''
  }
  articleVisible.value = true
}

async function submitCategory() {
  if (!categoryForm.name.trim()) {
    categoryError.value = '请填写分类名称'
    return
  }
  categorySaving.value = true
  categoryError.value = ''
  try {
    await serviceApi.kb.createCategory({
      name: categoryForm.name.trim(),
      parent_id: categoryForm.parent_id ? categoryForm.parent_id : undefined,
      sort_no: Number(categoryForm.sort_no),
    })
    ElMessage.success('分类已创建')
    categoryVisible.value = false
    categoryForm.name = ''
    categories.value = await serviceApi.kb.categories()
  } catch (err) {
    categoryError.value = describeError(err)
  } finally {
    categorySaving.value = false
  }
}

async function submitArticle() {
  if (!articleForm.title.trim() || !articleForm.content.trim()) {
    articleError.value = '标题与正文不能为空'
    return
  }
  articleSaving.value = true
  articleError.value = ''
  try {
    await serviceApi.kb.saveArticle({
      id: articleForm.id ? articleForm.id : undefined,
      category_id: articleForm.category_id ? articleForm.category_id : undefined,
      title: articleForm.title.trim(),
      summary: articleForm.summary.trim() || undefined,
      content: articleForm.content,
      status: articleForm.status,
      visibility: articleForm.visibility,
      keywords: articleForm.keywords ? articleForm.keywords.split(',').map((k) => k.trim()).filter(Boolean) : undefined,
    })
    ElMessage.success('文章已保存')
    articleVisible.value = false
    await load()
  } catch (err) {
    articleError.value = describeError(err)
  } finally {
    articleSaving.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="知识库管理"
      description="知识分类、文章编写与反馈；数据来自 /api/service/v1/kb。"
    >
      <template #actions>
        <el-input v-model="filters.keyword" placeholder="标题关键字" clearable style="width: 170px" @keyup.enter="changePage(1)" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px" @change="changePage(1)">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-select v-model="filters.categoryId" placeholder="全部分类" clearable style="width: 150px" @change="changePage(1)">
          <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-button v-perm="'service:kb:category:manage'" plain @click="categoryVisible = true">新增分类</el-button>
        <el-button v-perm="'service:kb:edit'" type="primary" @click="openArticle()">新建文章</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="articles" v-loading="loading" border stripe max-height="560">
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column prop="category_id" label="分类ID" width="100" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="visibility" label="可见范围" width="110" />
        <el-table-column prop="view_count" label="浏览" width="90" />
        <el-table-column prop="helpful_count" label="有用" width="90" />
        <el-table-column prop="author_id" label="作者" width="90" />
        <el-table-column prop="published_at" label="发布时间" width="170" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-perm="'service:kb:edit'" link type="primary" @click="openArticle(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>

    <el-dialog v-model="categoryVisible" title="新增知识分类" width="440px">
      <el-alert v-if="categoryError" type="error" :closable="false" show-icon :title="categoryError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="分类名称" required><el-input v-model="categoryForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="上级ID"><el-input v-model="categoryForm.parent_id" placeholder="选填" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sort_no" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="submitCategory">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="articleVisible" :title="articleForm.id ? '编辑文章' : '新建文章'" width="640px">
      <el-alert v-if="articleError" type="error" :closable="false" show-icon :title="articleError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="标题" required><el-input v-model="articleForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="articleForm.category_id" clearable style="width: 100%">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要"><el-input v-model="articleForm.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="正文" required><el-input v-model="articleForm.content" type="textarea" :rows="8" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="articleForm.status" style="width: 100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="articleForm.visibility" style="width: 100%">
            <el-option label="全员" value="ALL" />
            <el-option label="内部" value="INTERNAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词"><el-input v-model="articleForm.keywords" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="articleVisible = false">取消</el-button>
        <el-button type="primary" :loading="articleSaving" @click="submitArticle">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
