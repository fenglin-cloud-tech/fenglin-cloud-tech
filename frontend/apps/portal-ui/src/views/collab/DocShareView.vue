<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { PageHeader, RichDocContent, parseRichContent } from '@fenglin/ui-admin'
import { collabApi, type CollabDoc } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const doc = ref<CollabDoc | null>(null)

const richView = computed(() => parseRichContent(doc.value?.content, doc.value?.content_format))

/** 分享图片必须走分享代理端点，令牌绑定链接哈希；不回退到登录态图片地址。 */
function resolveImage(fileId: string) {
  const token = doc.value?.image_tokens?.[fileId]
  if (!token) return undefined
  return `/api/collab/v1/docs/shared/${String(route.params.token)}/images/${fileId}?asset_token=${token}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    doc.value = await collabApi.docs.openShared(String(route.params.token))
  } catch (err) {
    doc.value = null
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="doc-share">
    <PageHeader title="分享文档" description="持有链接即可查看，链接过期后自动失效。" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="doc-share__head">
          <span>{{ doc?.title || '文档' }}</span>
          <el-tag v-if="doc?.share_scope === 'LINK'" type="success" size="small">链接分享</el-tag>
        </div>
      </template>
      <RichDocContent v-if="richView" :model="richView" :resolve-image="resolveImage" />
      <pre v-else class="doc-share__content">{{ doc?.content || '（空文档）' }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.doc-share__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.doc-share__content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.7;
}
</style>
