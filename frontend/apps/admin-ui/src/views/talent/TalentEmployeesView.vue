<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, CODE_COLUMN_WIDTH } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentEmployeeDetail,
  type TalentEmployeeListItem,
  type TalentGrade,
  type TalentOrgNode,
  type TalentPosition,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const gapItems = [
  '编辑仅支持姓名、邮箱、工作地点三个字段；组织/岗位/职级调整请走调动单。',
  '批量导入接口接收 JSON 行数组，后端没有模板下载，本页未做 Excel 解析。',
  '手机号与身份证号按后端脱敏规则返回，页面不展示明文。',
]

const orgs = ref<TalentOrgNode[]>([])
const positions = ref<TalentPosition[]>([])
const grades = ref<TalentGrade[]>([])
const employeeOptions = ref<TalentEmployeeListItem[]>([])

const flatOrgs = computed(() => {
  const out: TalentOrgNode[] = []
  const walk = (nodes: TalentOrgNode[]) => {
    nodes.forEach((node) => {
      out.push(node)
      if (node.children?.length) walk(node.children)
    })
  }
  walk(orgs.value)
  return out
})

async function loadSelectors() {
  try {
    const [tree, posPage, gradeList, empPage] = await Promise.all([
      talentApi.orgs.tree({ depth: 6 }),
      talentApi.positions.list({ page: 1, size: 200 }),
      talentApi.grades.list(),
      talentApi.employees.list({ page: 1, size: 200 }),
    ])
    orgs.value = tree
    positions.value = posPage.records
    grades.value = gradeList
    employeeOptions.value = empPage.records
  } catch {
    // 选择器加载失败时仍保留手工输入能力
  }
}

const rows = ref<TalentEmployeeListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({
  keyword: '',
  empStatus: '',
  orgId: '',
  includeSubOrg: false,
  page: 1,
  size: 10,
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<TalentEmployeeDetail | null>(null)

const editVisible = ref(false)
const editing = ref(false)
const editError = ref('')
const editForm = reactive({
  name: '',
  email: '',
  work_location: '',
  version: 0,
})

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  emp_no: '',
  name: '',
  gender: 'UNKNOWN',
  birth_date: '',
  phone: '',
  id_card: '',
  email: '',
  org_id: '',
  position_id: '',
  grade_id: '',
  manager_id: '',
  employment_type: 'REGULAR',
  hire_date: '',
  work_location: '',
})

const statusOptions = [
  { value: 'ACTIVE', label: '在职' },
  { value: 'PROBATION', label: '试用期' },
  { value: 'PENDING', label: '待入职' },
  { value: 'RESIGNED', label: '已离职' },
]

const genderOptions = [
  { value: 'M', label: '男' },
  { value: 'F', label: '女' },
  { value: 'UNKNOWN', label: '未知' },
]

const employmentOptions = [
  { value: 'REGULAR', label: '正式' },
  { value: 'INTERN', label: '实习' },
  { value: 'OUTSOURCE', label: '外包' },
]

function toId(text: string): string | undefined {
  return text.trim() ? text.trim() : undefined
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await talentApi.employees.list({
      keyword: query.keyword.trim() || undefined,
      empStatus: query.empStatus || undefined,
      orgId: toId(query.orgId),
      includeSubOrg: query.includeSubOrg || undefined,
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

function search() {
  query.page = 1
  void load()
}

async function openDetail(row: TalentEmployeeListItem) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await talentApi.employees.detail(row.id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

function openEdit() {
  if (!detail.value) return
  editForm.name = detail.value.name ?? ''
  editForm.email = detail.value.email ?? ''
  editForm.work_location = detail.value.work_location ?? ''
  editForm.version = detail.value.version ?? 0
  editError.value = ''
  editVisible.value = true
}

async function submitEdit() {
  if (!detail.value) return
  if (!editForm.name.trim()) {
    editError.value = '姓名不能为空'
    return
  }
  editing.value = true
  editError.value = ''
  try {
    await talentApi.employees.update(detail.value.id, {
      name: editForm.name.trim(),
      email: editForm.email.trim() || undefined,
      work_location: editForm.work_location.trim() || undefined,
      version: editForm.version,
    })
    ElMessage.success('员工信息已更新')
    editVisible.value = false
    await openDetail({ id: detail.value.id } as TalentEmployeeListItem)
    await load()
  } catch (err) {
    editError.value = describeError(err)
  } finally {
    editing.value = false
  }
}

function openCreate() {
  Object.assign(createForm, {
    emp_no: '',
    name: '',
    gender: 'UNKNOWN',
    birth_date: '',
    phone: '',
    id_card: '',
    email: '',
    org_id: '',
    position_id: '',
    grade_id: '',
    manager_id: '',
    employment_type: 'REGULAR',
    hire_date: '',
    work_location: '',
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.emp_no.trim() || !createForm.name.trim()) {
    createError.value = '工号与姓名为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await talentApi.employees.create({
      emp_no: createForm.emp_no.trim(),
      name: createForm.name.trim(),
      gender: createForm.gender,
      birth_date: createForm.birth_date || undefined,
      phone: createForm.phone.trim() || undefined,
      id_card: createForm.id_card.trim() || undefined,
      email: createForm.email.trim() || undefined,
      org_id: toId(createForm.org_id),
      position_id: toId(createForm.position_id),
      grade_id: toId(createForm.grade_id),
      manager_id: toId(createForm.manager_id),
      employment_type: createForm.employment_type,
      hire_date: createForm.hire_date || undefined,
      work_location: createForm.work_location.trim() || undefined,
    })
    ElMessage.success('员工已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function exportRoster() {
  try {
    await talentApi.employees.exportRoster({
      orgId: toId(query.orgId),
      includeSubOrg: query.includeSubOrg || undefined,
      empStatus: query.empStatus || undefined,
    })
    ElMessage.success('花名册已开始下载')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(async () => {
  await Promise.all([load(), loadSelectors()])
})
</script>

<template>
  <div>
    <PageHeader module="云才 Talent" title="员工档案" description="员工主数据查询与建档；敏感字段按后端脱敏返回。">
      <template #actions>
        <el-button v-perm="'talent:admin:employee:create'" type="primary" @click="openCreate">新建员工</el-button>
        <el-button v-perm="'talent:admin:employee:export'" @click="exportRoster">导出花名册</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <div class="fl-card">
      <div class="fl-toolbar" style="margin-bottom: 12px; display: flex; gap: 8px; flex-wrap: wrap">
        <el-input v-model="query.keyword" placeholder="工号 / 姓名关键字" clearable style="width: 220px" @keyup.enter="search" />
        <el-select v-model="query.empStatus" clearable placeholder="员工状态" style="width: 160px" @change="search">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="query.orgId" clearable filterable placeholder="组织" style="width: 200px" @change="search">
          <el-option v-for="o in flatOrgs" :key="String(o.id)" :label="o.org_name" :value="o.id" />
        </el-select>
        <el-checkbox v-model="query.includeSubOrg">含下级组织</el-checkbox>
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="emp_no" label="工号" :width="CODE_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column label="组织" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.org?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="position" label="岗位" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.position ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="grade" label="职级" width="110">
          <template #default="{ row }">{{ row.grade ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="emp_status" label="状态" width="110" />
        <el-table-column prop="hire_date" label="入职日期" width="130">
          <template #default="{ row }">{{ row.hire_date ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="masked_phone" label="手机号" width="140" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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
    </div>

    <el-drawer v-model="detailVisible" title="员工详情" size="520px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="工号">{{ detail.emp_no }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detail.gender }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ detail.birth_date ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.masked_phone }}</el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ detail.masked_id_card }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.email ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="组织">{{ detail.org?.name ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ detail.position?.name ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="职级">{{ detail.grade?.name ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="直属上级">{{ detail.manager?.name ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="用工类型">{{ detail.employment_type }}</el-descriptions-item>
          <el-descriptions-item label="入职日期">{{ detail.hire_date ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="转正日期">{{ detail.formal_date ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="员工状态">{{ detail.emp_status }}</el-descriptions-item>
          <el-descriptions-item label="工作地点">{{ detail.work_location ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!detailLoading" description="未获取到员工详情" />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!detail" @click="openEdit">编辑</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="editVisible" title="编辑员工" width="520px">
      <el-alert v-if="editError" type="error" :closable="false" show-icon :title="editError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="姓名" required>
          <el-input v-model="editForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="工作地点">
          <el-input v-model="editForm.work_location" maxlength="128" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model.number="editForm.version" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新建员工" width="640px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="工号" required>
          <el-input v-model="createForm.emp_no" maxlength="64" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="createForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="createForm.gender" style="width: 100%">
            <el-option v-for="opt in genderOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="createForm.birth_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="createForm.id_card" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="组织">
          <el-select v-model="createForm.org_id" clearable filterable placeholder="选填" style="width: 100%">
            <el-option v-for="o in flatOrgs" :key="String(o.id)" :label="o.org_name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="createForm.position_id" clearable filterable placeholder="选填" style="width: 100%">
            <el-option
              v-for="p in positions"
              :key="String(p.id)"
              :label="`${p.position_name}（${p.position_code}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="职级">
          <el-select v-model="createForm.grade_id" clearable filterable placeholder="选填" style="width: 100%">
            <el-option v-for="g in grades" :key="String(g.id)" :label="`${g.grade_name}（${g.grade_code}）`" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="直属上级">
          <el-select v-model="createForm.manager_id" clearable filterable placeholder="选填" style="width: 100%">
            <el-option
              v-for="e in employeeOptions"
              :key="String(e.id)"
              :label="`${e.name}（${e.emp_no}）`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用工类型">
          <el-select v-model="createForm.employment_type" style="width: 100%">
            <el-option v-for="opt in employmentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期">
          <el-date-picker v-model="createForm.hire_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工作地点">
          <el-input v-model="createForm.work_location" maxlength="128" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
