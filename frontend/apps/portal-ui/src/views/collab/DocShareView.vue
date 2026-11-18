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
    <PageHeader title="分享文档" description="同租户内有效，链接过期后需重新生成。" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="doc-share__head">
          <span>{{ doc?.title || '文档' }}</span>
          <el-tag v-if="doc?.share_scope === 'LINK'" type="success" size="small">链接分享</el-tag>
        </div>
      </template>
      <RichDocContent v-if="richView" :model="richView" />
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
