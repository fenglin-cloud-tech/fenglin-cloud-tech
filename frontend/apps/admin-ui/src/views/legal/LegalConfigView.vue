<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const tab = ref('reminder')

const reminderForm = reactive({ offset_days: 30, channels: ['INBOX', 'EMAIL'] })
const fieldForm = reactive({
  fields: [
    { field_key: 'name', visible: true, required: true },
    { field_key: 'amount', visible: true, required: false },
  ],
  fieldsText: '[{"field_key":"name","visible":true,"required":true}]',
})
const dictForm = reactive({ type: 'CONTRACT_CATEGORY', itemsText: '["SALES","PURCHASE"]' })
const caForm = reactive({ provider_code: 'ESIGN', secret_ref: 'kms://legal/esign', endpoint: '' })

const saving = ref(false)
const caProviders = ref<LegalRecord[]>([])
const caError = ref('')

const loading = ref(false)
const loadError = ref('')

function parseJson(text: string, expectArray = true): unknown | undefined {
  try {
    const value = JSON.parse(text)
    if (expectArray && !Array.isArray(value)) {
      ElMessage.error('内容需为 JSON 数组')
      return undefined
    }
    return value
  } catch {
    ElMessage.error('JSON 格式不合法')
    return undefined
  }
}

async function saveReminder() {
  saving.value = true
  try {
    await legalApi.config.saveReminder({ offset_days: reminderForm.offset_days, channels: reminderForm.channels })
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function saveFields() {
  const fields = parseJson(fieldForm.fieldsText)
  if (fields === undefined) return
  saving.value = true
  try {
    await legalApi.config.saveContractFields({ fields })
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function saveDictionary() {
  const items = parseJson(dictForm.itemsText)
  if (items === undefined) return
  saving.value = true
  try {
    await legalApi.config.saveDictionary({ type: dictForm.type, items })
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function saveCa() {
  if (!caForm.provider_code.trim()) {
    ElMessage.warning('服务商编码必填')
    return
  }
  if (!caForm.secret_ref.startsWith('kms://')) {
    ElMessage.warning('secret_ref 必须以 kms:// 开头')
    return
  }
  saving.value = true
  try {
    await legalApi.config.saveCaProvider({
      provider_code: caForm.provider_code.trim(),
      secret_ref: caForm.secret_ref,
      endpoint: caForm.endpoint || undefined,
    })
    ElMessage.success('已保存')
    await loadCaProviders()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function loadReminder() {
  const data = await legalApi.config.reminder()
  reminderForm.offset_days = Number(data.offset_days ?? 30)
  reminderForm.channels = Array.isArray(data.channels) ? data.channels : ['INBOX', 'EMAIL']
}

async function loadFields() {
  const data = await legalApi.config.contractFields()
  fieldForm.fieldsText = JSON.stringify(data.fields ?? [], null, 2)
}

async function loadDictionary() {
  const data = await legalApi.config.dictionary(dictForm.type)
  dictForm.itemsText = JSON.stringify(data.items ?? [], null, 2)
}

async function loadCaProviders() {
  caError.value = ''
  try {
    caProviders.value = await legalApi.config.caProviders()
  } catch (err) {
    caProviders.value = []
    caError.value = describeError(err)
  }
}

async function loadConfigs() {
  loading.value = true
  loadError.value = ''
  try {
    await Promise.all([loadReminder(), loadFields(), loadDictionary()])
  } catch (err) {
    loadError.value = describeError(err)
  } finally {
    loading.value = false
  }
  await loadCaProviders()
}

onMounted(loadConfigs)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="法务配置"
      description="提醒、合同字段、字典与 CA 服务商配置；接口位于 /api/legal/v1/admin/config/*，按租户持久化。"
    />
    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" style="margin-bottom: 12px" />

    <el-tabs v-model="tab" v-loading="loading">
      <el-tab-pane label="到期提醒" name="reminder">
        <el-card shadow="never">
          <el-form label-width="120px" style="max-width: 560px">
            <el-form-item label="提前天数">
              <el-input-number v-model="reminderForm.offset_days" :min="1" :max="365" />
            </el-form-item>
            <el-form-item label="通知渠道">
              <el-checkbox-group v-model="reminderForm.channels">
                <el-checkbox label="INBOX">站内信</el-checkbox>
                <el-checkbox label="EMAIL">邮件</el-checkbox>
                <el-checkbox label="IM">IM</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:legal:reminder:config'" type="primary" :loading="saving" @click="saveReminder">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="合同字段" name="fields">
        <el-card shadow="never">
          <el-alert type="info" :closable="false" show-icon title="字段配置以 JSON 数组提交：field_key / visible / required。" style="margin-bottom: 12px" />
          <el-input v-model="fieldForm.fieldsText" type="textarea" :rows="10" />
          <div style="margin-top: 12px">
            <el-button v-perm="'tenant:legal:field:config'" type="primary" :loading="saving" @click="saveFields">保存</el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="字典" name="dictionary">
        <el-card shadow="never">
          <el-form label-width="100px" style="max-width: 640px">
            <el-form-item label="字典类型"><el-input v-model="dictForm.type" /></el-form-item>
            <el-form-item label="字典项">
              <el-input v-model="dictForm.itemsText" type="textarea" :rows="8" />
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:legal:dict:config'" type="primary" :loading="saving" @click="saveDictionary">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="CA 服务商" name="ca">
        <el-card shadow="never">
          <el-alert v-if="caError" type="error" :closable="false" show-icon :title="caError" style="margin-bottom: 12px" />
          <el-form label-width="110px" style="max-width: 600px">
            <el-form-item label="服务商编码"><el-input v-model="caForm.provider_code" /></el-form-item>
            <el-form-item label="密钥引用"><el-input v-model="caForm.secret_ref" placeholder="kms://legal/esign" /></el-form-item>
            <el-form-item label="接入地址"><el-input v-model="caForm.endpoint" placeholder="https://" /></el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:legal:ca:config'" type="primary" :loading="saving" @click="saveCa">保存</el-button>
              <el-button @click="loadCaProviders">刷新列表</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="caProviders" border size="small">
            <el-table-column prop="provider_code" label="服务商编码" width="160" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
          <el-empty v-if="caProviders.length === 0" description="暂无 CA 服务商" :image-size="60" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
