<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndWorklog } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const worklogs = ref<RndWorklog[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const dialogVisible = ref(false)

const today = new Date().toISOString().slice(0, 10)
const form = reactive({ task_id: '', work_date: today, hours: '8', remark: '' })

const totalHours = computed(() =>
  worklogs.value.reduce((sum, item) => sum + Number(item.hours || 0), 0).toFixed(1),
)

const gapItems = [
  '填报工时必须手工填写任务 ID：没有“我的任务下拉选择”接口，任务来自“我的任务”页。',
  '工时接口没有撤回/修改接口，提交后只能新增记录。',
]

async function loadWorklogs() {
  loading.value = true
  error.value = ''
  try {
    worklogs.value = await rndApi.worklogs.my({ page: 1, size: 200 })
  } catch (err) {
    worklogs.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.task_id) {
    formError.value = '请填写任务 ID'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await rndApi.worklogs.create({
      task_id: form.task_id,
      work_date: form.work_date,
      hours: form.hours,
      remark: form.remark.trim() || undefined,
    })
    ElMessage.success('工时已登记')
    dialogVisible.value = false
    form.task_id = ''
    form.remark = ''
    await loadWorklogs()
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}

onMounted(loadWorklogs)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="工时填报"
      description="登记研发工时并查看本人记录；数据来自 /api/rnd/v1/worklogs/my。"
    >
      <template #actions>
        <el-button v-perm="'biz:devops:worklog:self:submit'" type="primary" @click="dialogVisible = true">登记工时</el-button>
        <el-button :loading="loading" @click="loadWorklogs">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-tag type="success" effect="plain">本人累计 {{ totalHours }} 小时</el-tag>
        <span class="fl-muted">共 {{ worklogs.length }} 条记录</span>
      </div>
      <el-table :data="worklogs" v-loading="loading" border stripe max-height="520">
        <el-table-column prop="id" label="记录ID" width="100" />
        <el-table-column prop="task_id" label="任务ID" width="120" />
        <el-table-column prop="work_date" label="工作日期" width="130" />
        <el-table-column prop="hours" label="工时" width="100" />
        <el-table-column prop="remark" label="说明" min-width="220" />
        <el-table-column prop="created_at" label="登记时间" width="180" />
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="登记工时" width="480px">
      <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="任务ID" required>
          <el-input v-model="form.task_id" placeholder="从“我的任务”获取任务 ID" />
        </el-form-item>
        <el-form-item label="工作日期">
          <el-date-picker v-model="form.work_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工时"><el-input v-model="form.hours" placeholder="如 8" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
