<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canUsage = computed(() => auth.can('tenant:ai:usage:view'))
const canGrant = computed(() => auth.can('tenant:ai:grant:manage'))
const canDataPerm = computed(() => auth.can('tenant:ai:bi:dataperm:config'))

const modeResult = ref<AiRecord | null>(null)
const modeLoading = ref(false)
const modeError = ref('')
const validateLoading = ref(false)
const validateResult = ref<AiRecord | null>(null)
const validateError = ref('')

const meteringForm = reactive({ mode: '', host_product: '', from: '', to: '' })
const meteringLoading = ref(false)
const meteringError = ref('')
const meteringResult = ref<AiRecord | null>(null)

const grantForm = reactive({ hostProduct: '' })
const grantLoading = ref(false)
const grantError = ref('')
const grantResult = ref<AiRecord | null>(null)

const permForm = reactive({
  subject_type: '',
  subject_id: '',
  resource_type: '',
  resource_id: '',
  data_scope: 'SELF',
  scope_dept_ids: '',
  column_hidden: '',
  row_filter_expr: '',
})
const permSaving = ref(false)
const permError = ref('')
const permResult = ref<AiRecord | null>(null)

const gapItems = [
  '能力授权与计量是平台 AI 中台（hub_ai_*）的只读代理，本服务不落库、不计费。',
  '双模式只有读取与一致性校验接口，没有写入接口。',
  '数据权限没有查询接口，本页只能提交配置，提交后不回显历史记录。',
]

async function loadMode() {
  if (!canUsage.value) return
  modeLoading.value = true
  modeError.value = ''
  try {
    modeResult.value = await intelligenceApi.admin.mode()
  } catch (err) {
    modeResult.value = null
    modeError.value = describeError(err)
  } finally {
    modeLoading.value = false
  }
}

async function validateMode() {
  validateLoading.value = true
  validateError.value = ''
  validateResult.value = null
  try {
    validateResult.value = await intelligenceApi.admin.validateMode()
    ElMessage.success('双模式一致性校验完成')
  } catch (err) {
    validateError.value = describeError(err)
  } finally {
    validateLoading.value = false
  }
}

async function loadMetering() {
  meteringLoading.value = true
  meteringError.value = ''
  meteringResult.value = null
  try {
    meteringResult.value = await intelligenceApi.admin.metering({
      mode: meteringForm.mode.trim() || undefined,
      host_product: meteringForm.host_product.trim() || undefined,
      from: meteringForm.from || undefined,
      to: meteringForm.to || undefined,
    })
  } catch (err) {
    meteringError.value = describeError(err)
  } finally {
    meteringLoading.value = false
  }
}

async function loadGrants() {
  grantLoading.value = true
  grantError.value = ''
  grantResult.value = null
  try {
    grantResult.value = await intelligenceApi.admin.grants(grantForm.hostProduct.trim() || undefined)
  } catch (err) {
    grantError.value = describeError(err)
  } finally {
    grantLoading.value = false
  }
}

async function submitDataPermission() {
  permError.value = ''
  if (!permForm.subject_type.trim() || !permForm.subject_id || !permForm.resource_type.trim()) {
    permError.value = '授权主体类型、主体 ID 与资源类型为必填项'
    return
  }
  permSaving.value = true
  permResult.value = null
  try {
    permResult.value = await intelligenceApi.dataPermissions.config({
      subject_type: permForm.subject_type.trim(),
      subject_id: permForm.subject_id,
      resource_type: permForm.resource_type.trim(),
      resource_id: permForm.resource_id ? permForm.resource_id : undefined,
      data_scope: permForm.data_scope,
      scope_dept_ids: permForm.scope_dept_ids.trim() || undefined,
      column_hidden: permForm.column_hidden.trim() || undefined,
      row_filter_expr: permForm.row_filter_expr.trim() || undefined,
    })
    ElMessage.success('数据权限已提交')
  } catch (err) {
    permError.value = describeError(err)
  } finally {
    permSaving.value = false
  }
}

onMounted(loadMode)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="双模式与治理"
      description="部署模式、用量计量、能力授权与 BI 数据权限；数据来自 /api/intelligence/v1/admin。"
    >
      <template #actions>
        <el-button :loading="modeLoading" @click="loadMode">刷新双模式</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="modeError" type="error" :closable="false" show-icon :title="modeError" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card v-loading="modeLoading" shadow="never" style="margin-bottom: 12px">
          <template #header>双模式</template>
          <el-alert
            v-if="!canUsage"
            type="info"
            :closable="false"
            show-icon
            title="当前账号缺少 tenant:ai:usage:view，无法读取双模式与计量。"
            style="margin-bottom: 12px"
          />
          <el-descriptions v-if="modeResult" :column="1" border size="small">
            <el-descriptions-item label="部署模式">{{ modeResult.deploy_mode ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="模型访问模式">{{ modeResult.model_access_mode ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="支持的部署模式">
              {{ Array.isArray(modeResult.supported_deploy_modes) ? modeResult.supported_deploy_modes.join(' / ') : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="能力来源">{{ modeResult.capability_source ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="计量来源">{{ modeResult.metering_source ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="模型网关归属">{{ modeResult.model_gateway_owner ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务归属">{{ modeResult.business_owner ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无模式信息" :image-size="60" />

          <el-divider content-position="left">一致性校验</el-divider>
          <el-button v-perm="'tenant:ai:usage:view'" type="primary" :loading="validateLoading" @click="validateMode">
            执行校验
          </el-button>
          <el-alert v-if="validateError" type="error" :closable="false" show-icon :title="validateError" style="margin-top: 12px" />
          <pre v-if="validateResult" class="fl-mono result-json">{{ JSON.stringify(validateResult, null, 2) }}</pre>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card v-loading="meteringLoading" shadow="never" style="margin-bottom: 12px">
          <template #header>用量计量</template>
          <el-form label-width="100px">
            <el-form-item label="模式"><el-input v-model="meteringForm.mode" placeholder="选填" /></el-form-item>
            <el-form-item label="宿主产品"><el-input v-model="meteringForm.host_product" placeholder="选填，如 hub" /></el-form-item>
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="meteringForm.from"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="开始时间"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="meteringForm.to"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="结束时间"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:ai:usage:view'" type="primary" :loading="meteringLoading" @click="loadMetering">
                查询计量
              </el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="meteringError" type="error" :closable="false" show-icon :title="meteringError" style="margin-bottom: 12px" />
          <pre v-if="meteringResult" class="fl-mono result-json">{{ JSON.stringify(meteringResult, null, 2) }}</pre>
          <el-empty v-else description="尚未查询计量" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card v-loading="grantLoading" shadow="never" style="margin-bottom: 12px">
          <template #header>能力授权</template>
          <el-alert
            v-if="!canGrant"
            type="info"
            :closable="false"
            show-icon
            title="当前账号缺少 tenant:ai:grant:manage，无法读取能力授权。"
            style="margin-bottom: 12px"
          />
          <div class="fl-toolbar" style="margin-bottom: 12px">
            <el-input v-model="grantForm.hostProduct" placeholder="宿主产品，选填" clearable style="width: 200px" />
            <el-button v-perm="'tenant:ai:grant:manage'" type="primary" :loading="grantLoading" @click="loadGrants">
              查询授权
            </el-button>
          </div>
          <el-alert v-if="grantError" type="error" :closable="false" show-icon :title="grantError" style="margin-bottom: 12px" />
          <pre v-if="grantResult" class="fl-mono result-json">{{ JSON.stringify(grantResult, null, 2) }}</pre>
          <el-empty v-else description="尚未查询能力授权" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header>BI 数据权限</template>
          <el-alert
            v-if="!canDataPerm"
            type="info"
            :closable="false"
            show-icon
            title="当前账号缺少 tenant:ai:bi:dataperm:config，无法提交数据权限。"
            style="margin-bottom: 12px"
          />
          <el-alert v-if="permError" type="error" :closable="false" show-icon :title="permError" style="margin-bottom: 12px" />
          <el-form label-width="120px">
            <el-form-item label="主体类型" required>
              <el-select v-model="permForm.subject_type" style="width: 100%">
                <el-option label="用户（USER）" value="USER" />
                <el-option label="部门（DEPT）" value="DEPT" />
                <el-option label="角色（ROLE）" value="ROLE" />
              </el-select>
            </el-form-item>
            <el-form-item label="主体 ID" required><el-input v-model="permForm.subject_id" /></el-form-item>
            <el-form-item label="资源类型" required>
              <el-select v-model="permForm.resource_type" style="width: 100%">
                <el-option label="指标（METRIC）" value="METRIC" />
                <el-option label="数据集（DATASET）" value="DATASET" />
                <el-option label="看板（DASHBOARD）" value="DASHBOARD" />
              </el-select>
            </el-form-item>
            <el-form-item label="资源 ID"><el-input v-model="permForm.resource_id" placeholder="选填" /></el-form-item>
            <el-form-item label="数据范围">
              <el-select v-model="permForm.data_scope" style="width: 100%">
                <el-option label="仅本人（SELF）" value="SELF" />
                <el-option label="本部门（DEPT）" value="DEPT" />
                <el-option label="指定部门（CUSTOM）" value="CUSTOM" />
                <el-option label="全部（ALL）" value="ALL" />
              </el-select>
            </el-form-item>
            <el-form-item label="部门 ID 列表">
              <el-input v-model="permForm.scope_dept_ids" placeholder="英文逗号分隔，选填" />
            </el-form-item>
            <el-form-item label="隐藏列">
              <el-input v-model="permForm.column_hidden" placeholder="英文逗号分隔，选填" />
            </el-form-item>
            <el-form-item label="行过滤表达式">
              <el-input v-model="permForm.row_filter_expr" placeholder="选填，如 dept_id = :deptId" />
            </el-form-item>
            <el-form-item>
              <el-button
                v-perm="'tenant:ai:bi:dataperm:config'"
                type="primary"
                :loading="permSaving"
                @click="submitDataPermission"
              >
                提交数据权限
              </el-button>
            </el-form-item>
          </el-form>
          <pre v-if="permResult" class="fl-mono result-json">{{ JSON.stringify(permResult, null, 2) }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.result-json {
  margin: 0;
  padding: 12px;
  max-height: 280px;
  overflow: auto;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
