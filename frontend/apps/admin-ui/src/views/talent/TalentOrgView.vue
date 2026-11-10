<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { talentApi, type TalentGrade, type TalentOrgDetail, type TalentOrgNode, type TalentPosition } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const gapItems = [
  '组织树默认只返回启用节点；停用组织需显式传 include_disabled，本页暂未提供开关。',
  '岗位列表没有状态与关键字过滤参数，只支持分页。',
  '职级只提供列表查询，没有新建/调整接口。',
  '组织负责人只接受 leader_id，前端无法按姓名检索成员。',
]

const activeTab = ref('tree')

const tree = ref<TalentOrgNode[]>([])
const treeLoading = ref(false)
const treeError = ref('')
const selectedNode = ref<TalentOrgNode | null>(null)
const detail = ref<TalentOrgDetail | null>(null)
const detailLoading = ref(false)

const orgDialogVisible = ref(false)
const orgSaving = ref(false)
const orgError = ref('')
const orgForm = reactive({
  parent_id: '',
  org_code: '',
  org_name: '',
  org_type: 'DEPT',
  leader_id: '',
  sort_no: 0,
})

const positions = ref<TalentPosition[]>([])
const positionTotal = ref(0)
const positionLoading = ref(false)
const positionError = ref('')
const positionQuery = reactive({ page: 1, size: 10 })

const grades = ref<TalentGrade[]>([])
const gradeLoading = ref(false)
const gradeError = ref('')

const positionDialogVisible = ref(false)
const positionSaving = ref(false)
const positionFormError = ref('')
const positionForm = reactive({
  position_code: '',
  position_name: '',
  org_id: '',
  job_family: '',
  job_sequence: '',
  headcount: 1,
  description: '',
})

const orgTypeOptions = [
  { value: 'COMPANY', label: '公司' },
  { value: 'DEPT', label: '部门' },
  { value: 'TEAM', label: '小组' },
]

function toId(text: string): string | undefined {
  return text.trim() ? text.trim() : undefined
}

async function loadTree() {
  treeLoading.value = true
  treeError.value = ''
  try {
    tree.value = await talentApi.orgs.tree()
  } catch (err) {
    treeError.value = describeError(err)
  } finally {
    treeLoading.value = false
  }
}

async function loadDetail(node: TalentOrgNode) {
  selectedNode.value = node
  detailLoading.value = true
  try {
    detail.value = await talentApi.orgs.detail(node.id)
  } catch (err) {
    detail.value = null
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

function openCreateOrg() {
  Object.assign(orgForm, {
    parent_id: selectedNode.value ? String(selectedNode.value.id) : '',
    org_code: '',
    org_name: '',
    org_type: 'DEPT',
    leader_id: '',
    sort_no: 0,
  })
  orgError.value = ''
  orgDialogVisible.value = true
}

async function submitOrg() {
  if (!orgForm.org_code.trim() || !orgForm.org_name.trim()) {
    orgError.value = '组织编码与组织名称为必填项'
    return
  }
  orgSaving.value = true
  orgError.value = ''
  try {
    await talentApi.orgs.create({
      parent_id: toId(orgForm.parent_id),
      org_code: orgForm.org_code.trim(),
      org_name: orgForm.org_name.trim(),
      org_type: orgForm.org_type,
      leader_id: toId(orgForm.leader_id),
      sort_no: orgForm.sort_no,
    })
    ElMessage.success('组织已创建')
    orgDialogVisible.value = false
    await loadTree()
  } catch (err) {
    orgError.value = describeError(err)
  } finally {
    orgSaving.value = false
  }
}

async function loadPositions() {
  positionLoading.value = true
  positionError.value = ''
  try {
    const result = await talentApi.positions.list({ page: positionQuery.page, size: positionQuery.size })
    positions.value = result.records
    positionTotal.value = result.total
  } catch (err) {
    positionError.value = describeError(err)
  } finally {
    positionLoading.value = false
  }
}

function openCreatePosition() {
  Object.assign(positionForm, {
    position_code: '',
    position_name: '',
    org_id: selectedNode.value ? String(selectedNode.value.id) : '',
    job_family: '',
    job_sequence: '',
    headcount: 1,
    description: '',
  })
  positionFormError.value = ''
  positionDialogVisible.value = true
}

async function submitPosition() {
  if (!positionForm.position_code.trim() || !positionForm.position_name.trim()) {
    positionFormError.value = '岗位编码与岗位名称为必填项'
    return
  }
  positionSaving.value = true
  positionFormError.value = ''
  try {
    await talentApi.positions.create({
      position_code: positionForm.position_code.trim(),
      position_name: positionForm.position_name.trim(),
      org_id: toId(positionForm.org_id),
      job_family: positionForm.job_family.trim() || undefined,
      job_sequence: positionForm.job_sequence.trim() || undefined,
      headcount: positionForm.headcount,
      description: positionForm.description.trim() || undefined,
    })
    ElMessage.success('岗位已创建')
    positionDialogVisible.value = false
    await loadPositions()
  } catch (err) {
    positionFormError.value = describeError(err)
  } finally {
    positionSaving.value = false
  }
}

async function loadGrades() {
  gradeLoading.value = true
  gradeError.value = ''
  try {
    grades.value = await talentApi.grades.list()
  } catch (err) {
    gradeError.value = describeError(err)
  } finally {
    gradeLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadTree(), loadPositions(), loadGrades()])
})
</script>

<template>
  <div>
    <PageHeader module="云才 Talent" title="组织与岗位" description="组织树、岗位与职级维护；组织编码与岗位编码为业务唯一键。">
      <template #actions>
        <el-button v-perm="'talent:admin:org:create'" type="primary" @click="openCreateOrg">新建组织</el-button>
        <el-button :loading="treeLoading" @click="loadTree">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="activeTab" class="fl-card" style="padding: 8px 16px">
      <el-tab-pane label="组织架构" name="tree">
        <el-alert v-if="treeError" type="error" :closable="false" show-icon :title="treeError" style="margin-bottom: 12px" />
        <el-row :gutter="16">
          <el-col :span="10">
            <el-card shadow="never" v-loading="treeLoading">
              <el-tree
                :data="tree"
                node-key="id"
                :props="{ label: 'org_name', children: 'children' }"
                default-expand-all
                highlight-current
                :expand-on-click-node="false"
                @node-click="loadDetail"
              />
              <el-empty v-if="!treeLoading && tree.length === 0" description="暂无组织节点" />
            </el-card>
          </el-col>
          <el-col :span="14">
            <el-card shadow="never" v-loading="detailLoading">
              <template #header>组织详情</template>
              <el-descriptions v-if="detail" :column="2" border>
                <el-descriptions-item label="组织 ID">{{ detail.id }}</el-descriptions-item>
                <el-descriptions-item label="组织编码">{{ detail.org_code }}</el-descriptions-item>
                <el-descriptions-item label="组织名称">{{ detail.org_name }}</el-descriptions-item>
                <el-descriptions-item label="组织类型">{{ detail.org_type }}</el-descriptions-item>
                <el-descriptions-item label="层级">{{ detail.level }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
                <el-descriptions-item label="负责人">{{ detail.leader?.name ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="在编人数">{{ detail.employee_count }}</el-descriptions-item>
                <el-descriptions-item label="路径" :span="2">{{ detail.path }}</el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="点击左侧组织节点查看详情" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="岗位与职级" name="position">
        <div class="fl-toolbar" style="margin-bottom: 12px">
          <el-button v-perm="'talent:admin:position:create'" type="primary" @click="openCreatePosition">新建岗位</el-button>
          <el-button :loading="positionLoading" @click="loadPositions">刷新岗位</el-button>
        </div>
        <el-alert v-if="positionError" type="error" :closable="false" show-icon :title="positionError" style="margin-bottom: 12px" />
        <el-table :data="positions" v-loading="positionLoading" border stripe>
          <el-table-column prop="position_code" label="岗位编码" width="150" />
          <el-table-column prop="position_name" label="岗位名称" min-width="160" />
          <el-table-column prop="job_family" label="职族" width="120" />
          <el-table-column prop="job_sequence" label="职序" width="120" />
          <el-table-column prop="headcount" label="编制" width="90" />
          <el-table-column prop="org_id" label="所属组织" width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>
        <el-pagination
          v-model:current-page="positionQuery.page"
          v-model:page-size="positionQuery.size"
          :total="positionTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 16px; justify-content: flex-end"
          @change="loadPositions"
        />

        <el-divider content-position="left">职级</el-divider>
        <el-alert v-if="gradeError" type="error" :closable="false" show-icon :title="gradeError" style="margin-bottom: 12px" />
        <el-table :data="grades" v-loading="gradeLoading" border stripe>
          <el-table-column prop="grade_code" label="职级编码" width="140" />
          <el-table-column prop="grade_name" label="职级名称" min-width="140" />
          <el-table-column prop="sequence" label="序列" width="120" />
          <el-table-column prop="grade_level" label="级别" width="90" />
          <el-table-column prop="band_min" label="带宽下限" width="120" />
          <el-table-column prop="band_max" label="带宽上限" width="120" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="orgDialogVisible" title="新建组织" width="560px">
      <el-alert v-if="orgError" type="error" :closable="false" show-icon :title="orgError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="上级组织 ID">
          <el-input v-model="orgForm.parent_id" placeholder="留空表示根组织" />
        </el-form-item>
        <el-form-item label="组织编码" required>
          <el-input v-model="orgForm.org_code" maxlength="64" />
        </el-form-item>
        <el-form-item label="组织名称" required>
          <el-input v-model="orgForm.org_name" maxlength="128" />
        </el-form-item>
        <el-form-item label="组织类型">
          <el-select v-model="orgForm.org_type" style="width: 100%">
            <el-option v-for="opt in orgTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input v-model="orgForm.leader_id" placeholder="选填，成员 ID" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="orgForm.sort_no" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orgSaving" @click="submitOrg">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="positionDialogVisible" title="新建岗位" width="560px">
      <el-alert v-if="positionFormError" type="error" :closable="false" show-icon :title="positionFormError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="岗位编码" required>
          <el-input v-model="positionForm.position_code" maxlength="64" />
        </el-form-item>
        <el-form-item label="岗位名称" required>
          <el-input v-model="positionForm.position_name" maxlength="128" />
        </el-form-item>
        <el-form-item label="所属组织 ID">
          <el-input v-model="positionForm.org_id" placeholder="选填，默认取当前选中组织" />
        </el-form-item>
        <el-form-item label="职族">
          <el-input v-model="positionForm.job_family" maxlength="64" />
        </el-form-item>
        <el-form-item label="职序">
          <el-input v-model="positionForm.job_sequence" maxlength="64" />
        </el-form-item>
        <el-form-item label="编制人数">
          <el-input-number v-model="positionForm.headcount" :min="0" />
        </el-form-item>
        <el-form-item label="岗位说明">
          <el-input v-model="positionForm.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="positionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="positionSaving" @click="submitPosition">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
