<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader } from '@fenglin/ui-admin'
import { talentApi, type TalentCourse, type TalentId } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const loading = ref(false)
const error = ref('')
const courses = ref<TalentCourse[]>([])
const total = ref(0)
const query = reactive({ keyword: '', mode: '', courseStatus: '', page: 1, size: 20 })

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const courseForm = reactive({
  course_code: '',
  title: '',
  category: '',
  lecturer: '',
  mode: 'ONLINE',
  duration_hours: 2,
  credit: 1,
  description: '',
})

const sessionVisible = ref(false)
const sessionSaving = ref(false)
const sessionError = ref('')
const sessionCourse = ref<TalentCourse | null>(null)
const sessionForm = reactive({ start_at: '', end_at: '', capacity: 30, enroll_deadline: '', venue: '' })
const createdSessions = ref<Array<{ id: TalentId; title: string; status: string; startAt: string }>>([])

const modeOptions = [
  { value: 'ONLINE', label: '线上' },
  { value: 'OFFLINE', label: '线下' },
  { value: 'BLENDED', label: '混合' },
]
const courseStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下架',
}

function modeLabel(value?: string) {
  return modeOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function courseStatusLabel(value?: string) {
  return courseStatusLabels[value ?? ''] ?? value ?? '-'
}

function statusTag(value?: string) {
  if (value === 'PUBLISHED') return 'success'
  if (value === 'OFFLINE') return 'info'
  return 'warning'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await talentApi.training.courses({
      keyword: query.keyword || undefined,
      mode: query.mode || undefined,
      courseStatus: query.courseStatus || undefined,
      page: query.page,
      size: query.size,
    })
    courses.value = page.records
    total.value = page.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(courseForm, {
    course_code: '',
    title: '',
    category: '',
    lecturer: '',
    mode: 'ONLINE',
    duration_hours: 2,
    credit: 1,
    description: '',
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!courseForm.course_code.trim() || !courseForm.title.trim()) {
    createError.value = '课程编码与名称不能为空'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await talentApi.training.createCourse({
      course_code: courseForm.course_code.trim(),
      title: courseForm.title.trim(),
      category: courseForm.category.trim() || undefined,
      lecturer: courseForm.lecturer.trim() || undefined,
      mode: courseForm.mode,
      duration_hours: Number(courseForm.duration_hours),
      credit: Number(courseForm.credit),
      description: courseForm.description.trim() || undefined,
    })
    ElMessage.success('课程已创建（草稿）')
    createVisible.value = false
    query.page = 1
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function changeStatus(row: TalentCourse, action: 'publish' | 'offline') {
  try {
    if (action === 'publish') {
      await talentApi.training.publishCourse(row.id)
      ElMessage.success('课程已发布')
    } else {
      await talentApi.training.offlineCourse(row.id)
      ElMessage.success('课程已下架')
    }
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openSession(row: TalentCourse) {
  sessionCourse.value = row
  Object.assign(sessionForm, { start_at: '', end_at: '', capacity: 30, enroll_deadline: '', venue: '' })
  sessionError.value = ''
  sessionVisible.value = true
}

async function submitSession() {
  if (!sessionCourse.value) return
  if (!sessionForm.start_at || !sessionForm.end_at) {
    sessionError.value = '请选择开始与结束时间'
    return
  }
  sessionSaving.value = true
  sessionError.value = ''
  try {
    const created = await talentApi.training.createSession(sessionCourse.value.id, {
      start_at: sessionForm.start_at,
      end_at: sessionForm.end_at,
      capacity: Number(sessionForm.capacity),
      enroll_deadline: sessionForm.enroll_deadline || undefined,
      venue: sessionForm.venue.trim() || undefined,
    })
    createdSessions.value.unshift({
      id: created.id,
      title: sessionCourse.value.title,
      status: created.session_status,
      startAt: sessionForm.start_at,
    })
    ElMessage.success('班次已创建，班次 ID 已记录在下方列表')
    sessionVisible.value = false
  } catch (err) {
    sessionError.value = describeError(err)
  } finally {
    sessionSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云才 Talent"
      title="培训管理"
      description="课程创建、发布/下架与班次创建；班次创建后可把班次 ID 交给员工端报名使用。"
    >
      <template #actions>
        <el-button v-perm="'talent:admin:course:create'" type="primary" @click="openCreate">新建课程</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="课程编码/名称"
          clearable
          style="width: 220px"
          @keyup.enter="query.page = 1; load()"
        />
        <el-select v-model="query.mode" placeholder="授课形式" clearable style="width: 140px" @change="query.page = 1; load()">
          <el-option v-for="opt in modeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select
          v-model="query.courseStatus"
          placeholder="课程状态"
          clearable
          style="width: 140px"
          @change="query.page = 1; load()"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下架" value="OFFLINE" />
        </el-select>
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <span class="fl-muted">共 {{ total }} 门课程</span>
      </div>

      <el-table :data="courses" v-loading="loading" border stripe>
        <el-table-column prop="course_code" label="课程编码" width="160" />
        <el-table-column prop="title" label="课程名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="授课形式" width="100">
          <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
        </el-table-column>
        <el-table-column label="课时" width="80">
          <template #default="{ row }">{{ row.duration_hours ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="学分" width="80">
          <template #default="{ row }">{{ row.credit ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.course_status)" size="small">{{ courseStatusLabel(row.course_status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.course_status !== 'PUBLISHED'"
              v-perm="'talent:admin:course:create'"
              link
              type="success"
              @click="changeStatus(row, 'publish')"
            >
              发布
            </el-button>
            <el-button
              v-if="row.course_status === 'PUBLISHED'"
              v-perm="'talent:admin:course:create'"
              link
              type="warning"
              @click="changeStatus(row, 'offline')"
            >
              下架
            </el-button>
            <el-button v-perm="'talent:admin:session:create'" link type="primary" @click="openSession(row)">
              开班次
            </el-button>
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

    <el-card v-if="createdSessions.length" shadow="never" style="margin-top: 16px">
      <template #header>本次创建的班次</template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="后端未提供班次查询接口，这里只保留本次页面会话内创建的班次；刷新页面后需从数据库或后续接口获取班次 ID。"
        style="margin-bottom: 12px"
      />
      <el-table :data="createdSessions" border size="small">
        <el-table-column prop="id" label="班次 ID" width="200" />
        <el-table-column prop="title" label="课程" min-width="180" />
        <el-table-column prop="startAt" label="开始时间" width="200" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建课程" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="课程编码" required>
          <el-input v-model="courseForm.course_code" maxlength="32" />
        </el-form-item>
        <el-form-item label="课程名称" required>
          <el-input v-model="courseForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="courseForm.category" maxlength="64" />
        </el-form-item>
        <el-form-item label="讲师">
          <el-input v-model="courseForm.lecturer" maxlength="64" />
        </el-form-item>
        <el-form-item label="授课形式" required>
          <el-select v-model="courseForm.mode" style="width: 100%">
            <el-option v-for="opt in modeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="课时">
          <el-input-number v-model="courseForm.duration_hours" :min="0" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="学分">
          <el-input-number v-model="courseForm.credit" :min="0" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="课程简介">
          <el-input v-model="courseForm.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sessionVisible" title="创建班次" width="540px">
      <el-alert v-if="sessionError" type="error" :closable="false" show-icon :title="sessionError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="课程">
          <el-text>{{ sessionCourse?.title }}</el-text>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="sessionForm.start_at"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="sessionForm.end_at"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报名截止">
          <el-date-picker
            v-model="sessionForm.enroll_deadline"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="容量" required>
          <el-input-number v-model="sessionForm.capacity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="sessionForm.venue" maxlength="128" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sessionVisible = false">取消</el-button>
        <el-button type="primary" :loading="sessionSaving" @click="submitSession">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
