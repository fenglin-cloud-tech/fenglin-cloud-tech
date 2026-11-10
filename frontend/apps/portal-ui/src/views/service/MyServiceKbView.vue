<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const categories = ref<ServiceRecord[]>([])
const articles = ref<ServiceRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const categoryId = ref<number | undefined>()
const loading = ref(false)
const error = ref('')

const drawer = ref(false)
const article = ref<ServiceRecord | null>(null)

const gapItems = [
  '知识库搜索接口只支持 keyword 模糊匹配标题/摘要，没有全文检索接口。',
  '反馈接口要求 service:kb:view，未提供撤销评价接口。',
  '文章正文为纯文本/Markdown 字符串，本页按纯文本展示，未引入 Markdown 渲染器。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    categories.value = await serviceApi.kb.categories()
    const result = await serviceApi.kb.articles({
      status: 'PUBLISHED',
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    articles.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openArticle(row: ServiceRecord) {
  try {
    article.value = await serviceApi.kb.article(Number(row.id))
    drawer.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function feedback(helpful: boolean) {
  if (!article.value) return
  try {
    await serviceApi.kb.feedback(Number(article.value.id), { helpful })
    ElMessage.success(helpful ? '已标记有帮助' : '已记录反馈')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服服务台"
      title="知识库"
      description="自助查询服务知识；数据来自 /api/service/v1/kb。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar" style="margin-bottom: 8px">
        <el-space>
          <el-input v-model="keyword" placeholder="搜索标题/摘要" clearable style="width: 240px" @keyup.enter="search" />
          <el-select v-model="categoryId" placeholder="全部分类" clearable style="width: 180px" @change="search">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button type="primary" @click="search">搜索</el-button>
        </el-space>
      </div>

      <el-table :data="articles" v-loading="loading" border stripe size="small">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="summary" label="摘要" min-width="260" />
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="view_count" label="浏览" width="80" />
        <el-table-column prop="helpful_count" label="有帮助" width="90" />
        <el-table-column prop="published_at" label="发布时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openArticle(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>

    <el-drawer v-model="drawer" :title="article?.title ?? '文章'" size="620px">
      <template v-if="article">
        <p class="fl-muted">{{ article.summary || '' }}</p>
        <el-divider />
        <div class="article-body">{{ article.content }}</div>
        <el-divider />
        <div class="fl-toolbar">
          <span class="fl-muted">这篇文档有帮助吗？</span>
          <el-space>
            <el-button size="small" type="success" plain @click="feedback(true)">有帮助</el-button>
            <el-button size="small" plain @click="feedback(false)">没帮助</el-button>
          </el-space>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.article-body {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 13px;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
