<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { talentApi, type TalentEmployeeDetail, type TalentEmployeeSkill } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const detail = ref<TalentEmployeeDetail | null>(null)
const skills = ref<TalentEmployeeSkill[]>([])
const loading = ref(false)
const error = ref('')
const skillsError = ref('')

const gapItems = [
  '员工端只开放本人档案只读视图；修改姓名、组织、岗位等字段走 HR 管理端（PUT /employees/{id} 需要 talent:admin:employee:manage）。',
  '手机号与身份证号由后端按权限脱敏返回（masked_phone / masked_id_card），页面不还原明文。',
  '档案与技能均按登录账号绑定的员工在服务端收敛（GET /employees/my、/skills/employees/my），不接收前端传入的员工 ID；账号未绑定员工时返回 403（3001007）。',
]

const statusLabels: Record<string, string> = {
  ACTIVE: '在职',
  PROBATION: '试用期',
  PENDING_ONBOARD: '待入职',
  RESIGNED: '已离职',
  TERMINATED: '已终止',
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await talentApi.employees.myProfile()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
  try {
    skills.value = await talentApi.skills.myEmployeeSkills()
  } catch (err) {
    skillsError.value = describeError(err)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="我的档案" description="查看本人基础档案与技能标签；敏感字段为后端脱敏结果。" />

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never" v-loading="loading">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="工号">{{ detail.emp_no }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ detail.gender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ detail.birth_date || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.masked_phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detail.masked_id_card || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="组织">{{ detail.org?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detail.position?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="职级">{{ detail.grade?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="直属上级">{{ detail.manager?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用工类型">{{ detail.employment_type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入职日期">{{ detail.hire_date || '-' }}</el-descriptions-item>
        <el-descriptions-item label="转正日期">{{ detail.formal_date || '-' }}</el-descriptions-item>
        <el-descriptions-item label="员工状态">{{ statusLabel(detail.emp_status) }}</el-descriptions-item>
        <el-descriptions-item label="工作地点">{{ detail.work_location || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loading && !error" description="暂无档案数据" :image-size="80" />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>我的技能</template>
      <el-alert
        v-if="skillsError"
        type="error"
        :closable="false"
        show-icon
        :title="skillsError"
        style="margin-bottom: 12px"
      />
      <el-table v-if="skills.length" :data="skills" border stripe size="small">
        <el-table-column prop="skill_name" label="技能" min-width="180" />
        <el-table-column prop="self_level" label="自评等级" width="110" />
        <el-table-column prop="cert_level" label="认证等级" width="110" />
        <el-table-column label="经验年限" width="110">
          <template #default="{ row }">{{ row.years ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="last_used_at" label="最近使用" width="140" />
      </el-table>
      <el-empty v-else-if="!skillsError" description="尚未登记技能标签" :image-size="64" />
    </el-card>
  </div>
</template>
