<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const active = ref('problem')
const saving = ref(false)
const error = ref('')

const problemForm = reactive({
  title: '',
  description: '',
  root_cause: '',
  workaround: '',
  known_error: false,
  owner_id: '',
  ticket_ids: '',
})

const aggregateForm = reactive({
  id: '',
  ticket_ids: '',
  status: 'KNOWN_ERROR',
  root_cause: '',
  workaround: '',
  known_error: true,
})

const changeForm = reactive({
  title: '',
  change_type: 'NORMAL',
  plan: '',
  rollback_plan: '',
  risk_level: 'MEDIUM',
  planned_start: '',
  planned_end: '',
  approval_flow_key: '',
  owner_id: '',
})

const approveForm = reactive({ id: '', approved: true, comment: '' })

const gapItems = [
  '问题与变更都没有列表/详情查询接口，本页只提供创建、问题聚合与变更审批。',
  '问题聚合需要先知道工单 ID，工单 ID 可在“工单工作台”查看。',
  '变更审批依赖工作流（svcChangeApproval），工作流服务不可用时返回真实错误。',
]

function ids(text: string) {
  return text.split(',').map((item) => Number(item.trim())).filter((item) => Number.isFinite(item) && item > 0)
}

async function createProblem() {
  if (!problemForm.title.trim()) {
    error.value = '请填写问题标题'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const created = await serviceApi.problems.create({
      title: problemForm.title.trim(),
      description: problemForm.description.trim() || undefined,
      root_cause: problemForm.root_cause.trim() || undefined,
      workaround: problemForm.workaround.trim() || undefined,
      known_error: problemForm.known_error,
      owner_id: problemForm.owner_id ? Number(problemForm.owner_id) : undefined,
      ticket_ids: ids(problemForm.ticket_ids),
    })
    ElMessage.success(`问题已创建：${created.problem_no ?? created.id}`)
    problemForm.title = ''
    problemForm.description = ''
    problemForm.ticket_ids = ''
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function aggregate() {
  if (!aggregateForm.id) {
    error.value = '请填写问题 ID'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await serviceApi.problems.aggregate(Number(aggregateForm.id), {
      ticket_ids: ids(aggregateForm.ticket_ids),
      status: aggregateForm.status,
      root_cause: aggregateForm.root_cause.trim() || undefined,
      workaround: aggregateForm.workaround.trim() || undefined,
      known_error: aggregateForm.known_error,
    })
    ElMessage.success('问题已聚合')
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function createChange() {
  if (!changeForm.title.trim()) {
    error.value = '请填写变更标题'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const created = await serviceApi.changes.create({
      title: changeForm.title.trim(),
      change_type: changeForm.change_type,
      plan: changeForm.plan.trim() || undefined,
      rollback_plan: changeForm.rollback_plan.trim() || undefined,
      risk_level: changeForm.risk_level,
      planned_start: changeForm.planned_start || undefined,
      planned_end: changeForm.planned_end || undefined,
      approval_flow_key: changeForm.approval_flow_key.trim() || undefined,
      owner_id: changeForm.owner_id ? Number(changeForm.owner_id) : undefined,
    })
    ElMessage.success(`变更已创建：${created.change_no ?? created.id}`)
    changeForm.title = ''
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function approveChange() {
  if (!approveForm.id) {
    error.value = '请填写变更 ID'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await serviceApi.changes.approve(Number(approveForm.id), {
      approved: approveForm.approved,
      comment: approveForm.comment.trim() || undefined,
    })
    ElMessage.success('审批结果已提交')
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="问题与变更"
      description="问题创建/聚合与变更审批；数据来自 /api/service/v1/problems 与 /changes。"
    />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="active">
        <el-tab-pane label="问题管理" name="problem">
          <el-row :gutter="16">
            <el-col :span="12">
              <h4>创建问题</h4>
              <el-form label-width="100px">
                <el-form-item label="标题" required><el-input v-model="problemForm.title" maxlength="200" /></el-form-item>
                <el-form-item label="描述"><el-input v-model="problemForm.description" type="textarea" :rows="3" /></el-form-item>
                <el-form-item label="根因"><el-input v-model="problemForm.root_cause" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="临时方案"><el-input v-model="problemForm.workaround" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="已知错误"><el-switch v-model="problemForm.known_error" /></el-form-item>
                <el-form-item label="负责人ID"><el-input v-model="problemForm.owner_id" /></el-form-item>
                <el-form-item label="关联工单"><el-input v-model="problemForm.ticket_ids" placeholder="逗号分隔工单ID" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:problem:manage'" type="primary" :loading="saving" @click="createProblem">创建问题</el-button>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="12">
              <h4>聚合问题</h4>
              <el-form label-width="100px">
                <el-form-item label="问题ID" required><el-input v-model="aggregateForm.id" /></el-form-item>
                <el-form-item label="工单ID"><el-input v-model="aggregateForm.ticket_ids" placeholder="逗号分隔" /></el-form-item>
                <el-form-item label="状态"><el-input v-model="aggregateForm.status" /></el-form-item>
                <el-form-item label="根因"><el-input v-model="aggregateForm.root_cause" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="临时方案"><el-input v-model="aggregateForm.workaround" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="已知错误"><el-switch v-model="aggregateForm.known_error" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:problem:manage'" type="primary" plain :loading="saving" @click="aggregate">执行聚合</el-button>
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="变更管理" name="change">
          <el-row :gutter="16">
            <el-col :span="12">
              <h4>创建变更</h4>
              <el-form label-width="110px">
                <el-form-item label="标题" required><el-input v-model="changeForm.title" maxlength="200" /></el-form-item>
                <el-form-item label="变更类型">
                  <el-select v-model="changeForm.change_type" style="width: 100%">
                    <el-option label="标准变更" value="STANDARD" />
                    <el-option label="普通变更" value="NORMAL" />
                    <el-option label="紧急变更" value="EMERGENCY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="风险等级">
                  <el-select v-model="changeForm.risk_level" style="width: 100%">
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MEDIUM" />
                    <el-option label="低" value="LOW" />
                  </el-select>
                </el-form-item>
                <el-form-item label="实施计划"><el-input v-model="changeForm.plan" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="回滚方案"><el-input v-model="changeForm.rollback_plan" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="计划开始">
                  <el-date-picker v-model="changeForm.planned_start" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
                </el-form-item>
                <el-form-item label="计划结束">
                  <el-date-picker v-model="changeForm.planned_end" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
                </el-form-item>
                <el-form-item label="审批流Key"><el-input v-model="changeForm.approval_flow_key" placeholder="选填" /></el-form-item>
                <el-form-item label="负责人ID"><el-input v-model="changeForm.owner_id" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:change:manage'" type="primary" :loading="saving" @click="createChange">创建变更</el-button>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="12">
              <h4>审批变更</h4>
              <el-form label-width="110px">
                <el-form-item label="变更ID" required><el-input v-model="approveForm.id" /></el-form-item>
                <el-form-item label="审批结果"><el-switch v-model="approveForm.approved" active-text="通过" inactive-text="驳回" /></el-form-item>
                <el-form-item label="审批意见"><el-input v-model="approveForm.comment" type="textarea" :rows="3" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:change:manage'" type="primary" :loading="saving" @click="approveChange">提交审批</el-button>
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
