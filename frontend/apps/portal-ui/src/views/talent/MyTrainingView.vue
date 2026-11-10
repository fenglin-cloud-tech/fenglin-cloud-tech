<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentCertificate,
  type TalentCourse,
  type TalentEnrollmentResult,
  type TalentId,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const loading = ref(false)
const error = ref('')
const courses = ref<TalentCourse[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

const certificates = ref<TalentCertificate[]>([])
const certLoading = ref(false)
const certError = ref('')

const enrollVisible = ref(false)
const enrollSaving = ref(false)
const enrollError = ref('')
const enrollForm = reactive({ course_id: '' as TalentId | '', session_id: '' })
const enrollment = ref<TalentEnrollmentResult | null>(null)
const enrolledCourseTitle = ref('')

const progressVisible = ref(false)
const progressSaving = ref(false)
const progressError = ref('')
const progressForm = reactive({ learning_id: '', progress: 50, study_seconds: 0 })

const gapItems = [
  '后端只有“创建班次”接口（POST /training/courses/{id}/sessions），没有班次查询接口；报名时需手工填写培训管理员提供的班次 ID。',
  '后端没有“我的报名/学习记录”查询接口，报名成功返回的学习记录 ID 只在本次页面会话内保留，可用于上报学习进度。',
  '考试提交需要试卷 ID 与答案结构（POST /training/exams/{id}/submit），员工端未开放考试入口。',
]

const modeLabels: Record<string, string> = {
  ONLINE: '线上',
  OFFLINE: '线下',
  BLENDED: '混合',
}

const courseStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下架',
}

function label(map: Record<string, string>, value?: string) {
  return value ? map[value] ?? value : '-'
}

async function loadCourses() {
  loading.value = true
  error.value = ''
  try {
    const page = await talentApi.training.courses({
      keyword: query.keyword || undefined,
      courseStatus: 'PUBLISHED',
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

async function loadCertificates() {
  certLoading.value = true
  certError.value = ''
  try {
    certificates.value = await talentApi.training.certificates()
  } catch (err) {
    certError.value = describeError(err)
  } finally {
    certLoading.value = false
  }
}

function openEnroll(row: TalentCourse) {
  enrollForm.course_id = row.id
  enrollForm.session_id = ''
  enrolledCourseTitle.value = row.title
  enrollError.value = ''
  enrollVisible.value = true
}

async function submitEnroll() {
  if (!enrollForm.course_id || !enrollForm.session_id.trim()) {
    enrollError.value = '课程与班次 ID 不能为空'
    return
  }
  enrollSaving.value = true
  enrollError.value = ''
  try {
    const result = await talentApi.training.enroll({
      course_id: enrollForm.course_id,
      session_id: enrollForm.session_id.trim(),
    })
    enrollment.value = result
    ElMessage.success(
      result.waitlist
        ? `已进入候补，候补位次 ${result.waitlist_no ?? '-'}`
        : `报名成功，学习记录 ID：${result.id}`,
    )
    enrollVisible.value = false
  } catch (err) {
    enrollError.value = describeError(err)
  } finally {
    enrollSaving.value = false
  }
}

function openProgress() {
  progressForm.learning_id = String(enrollment.value?.id ?? '')
  progressForm.progress = 50
  progressForm.study_seconds = 0
  progressError.value = ''
  progressVisible.value = true
}

async function submitProgress() {
  if (!progressForm.learning_id.trim()) {
    progressError.value = '学习记录 ID 不能为空'
    return
  }
  if (progressForm.progress < 0 || progressForm.progress > 100) {
    progressError.value = '进度需在 0-100 之间'
    return
  }
  progressSaving.value = true
  progressError.value = ''
  try {
    const result = await talentApi.training.reportProgress(progressForm.learning_id.trim(), {
      progress: progressForm.progress,
      study_seconds: progressForm.study_seconds || undefined,
    })
    ElMessage.success(`进度已更新为 ${result.progress}%（${result.learn_status}）`)
    progressVisible.value = false
  } catch (err) {
    progressError.value = describeError(err)
  } finally {
    progressSaving.value = false
  }
}

onMounted(() => {
  void loadCourses()
  void loadCertificates()
})
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="培训与课程" description="查看已发布课程、报名班次、上报学习进度与查看证书。">
      <template #actions>
        <el-button :loading="loading || certLoading" @click="loadCourses(); loadCertificates()">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card v-if="enrollment" shadow="never" style="margin-bottom: 16px">
      <div class="fl-toolbar">
        <span>
          已报名《{{ enrolledCourseTitle }}》，学习记录 ID：<strong>{{ enrollment.id }}</strong>，状态：{{
            enrollment.enroll_status
          }}
        </span>
        <el-button type="primary" plain size="small" @click="openProgress">上报学习进度</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="课程名称/编码"
          clearable
          style="width: 220px"
          @keyup.enter="query.page = 1; loadCourses()"
        />
        <el-button type="primary" plain @click="query.page = 1; loadCourses()">查询</el-button>
        <span class="fl-muted">共 {{ total }} 门已发布课程</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="courses" v-loading="loading" border stripe>
        <el-table-column prop="course_code" label="课程编码" width="160" />
        <el-table-column prop="title" label="课程名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="授课形式" width="100">
          <template #default="{ row }">{{ label(modeLabels, row.mode) }}</template>
        </el-table-column>
        <el-table-column label="课时" width="80">
          <template #default="{ row }">{{ row.duration_hours ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="学分" width="80">
          <template #default="{ row }">{{ row.credit ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ label(courseStatusLabels, row.course_status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEnroll(row)">报名</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="loadCourses"
      />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>我的证书</template>
      <el-alert v-if="certError" type="error" :closable="false" show-icon :title="certError" style="margin-bottom: 12px" />
      <el-table v-if="certificates.length" :data="certificates" v-loading="certLoading" border stripe size="small">
        <el-table-column prop="cert_no" label="证书编号" width="200" />
        <el-table-column prop="cert_name" label="证书名称" min-width="200" />
        <el-table-column prop="issue_date" label="颁发日期" width="130" />
        <el-table-column prop="expire_date" label="有效期至" width="130" />
      </el-table>
      <el-empty v-else-if="!certError" description="暂无证书" :image-size="64" />
    </el-card>

    <el-dialog v-model="enrollVisible" title="课程报名" width="480px">
      <el-alert v-if="enrollError" type="error" :closable="false" show-icon :title="enrollError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="课程">
          <el-text>{{ enrolledCourseTitle }}</el-text>
        </el-form-item>
        <el-form-item label="班次 ID" required>
          <el-input v-model="enrollForm.session_id" placeholder="由培训管理员创建班次后提供" />
        </el-form-item>
        <el-form-item label="说明">
          <span class="fl-muted">后端没有班次查询接口，无法在员工端选择班次；请向培训管理员索取班次 ID。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enrollVisible = false">取消</el-button>
        <el-button type="primary" :loading="enrollSaving" @click="submitEnroll">报名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progressVisible" title="上报学习进度" width="460px">
      <el-alert
        v-if="progressError"
        type="error"
        :closable="false"
        show-icon
        :title="progressError"
        style="margin-bottom: 12px"
      />
      <el-form label-width="100px">
        <el-form-item label="学习记录 ID" required>
          <el-input v-model="progressForm.learning_id" />
        </el-form-item>
        <el-form-item label="进度(%)" required>
          <el-input-number v-model="progressForm.progress" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="学习时长(秒)">
          <el-input-number v-model="progressForm.study_seconds" :min="0" :step="60" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" :loading="progressSaving" @click="submitProgress">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
