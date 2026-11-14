<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Aim,
  AlarmClock,
  Bell,
  Box,
  Calendar,
  ChatDotRound,
  Cpu,
  DataAnalysis,
  Document,
  Finished,
  Flag,
  Grid,
  Warning,
  Money,
  Promotion,
  Reading,
  Search,
  Service,
  Share,
  Stamp,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  VideoCamera,
  Clock,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@fenglin/api-client'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const permissionVisible = ref(false)
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const navItems = [
  { path: '/overview', title: '我的工作台', icon: Grid, perm: '' },
  { path: '/my-leads', title: '我的线索', icon: Aim, perm: 'crm:lead:view' },
  { path: '/my-customers', title: '我的客户', icon: UserFilled, perm: 'crm:customer:view' },
  { path: '/my-opportunities', title: '我的商机', icon: TrendCharts, perm: 'crm:opportunity:view' },
  { path: '/my-quotations', title: '我的报价/合同', icon: Tickets, perm: 'crm:quotation:view' },
  { path: '/payment-reminders', title: '回款提醒', icon: AlarmClock, perm: 'crm:contract:view' },
]

const collabItems = [
  { path: '/messages', title: '消息', icon: ChatDotRound, perm: 'biz:collab:im:chat' },
  { path: '/docs', title: '文档', icon: Document, perm: 'biz:collab:doc:read' },
  { path: '/my-schedule', title: '我的日程', icon: Calendar, perm: 'biz:collab:calendar:read' },
  { path: '/meetings', title: '会议邀请', icon: VideoCamera, perm: 'biz:collab:meeting:join' },
  { path: '/my-okr', title: '我的 OKR', icon: Flag, perm: 'biz:collab:okr:read' },
  { path: '/approvals', title: '审批', icon: Stamp, perm: 'biz:collab:approval:read' },
  { path: '/notifications', title: '通知中心', icon: Bell, perm: 'biz:collab:workbench:personal' },
]

const talentItems = [
  { path: '/my-profile', title: '我的档案', icon: User, perm: 'talent:self:profile:view' },
  { path: '/my-attendance', title: '我的考勤与请假', icon: Clock, perm: 'talent:attendance:view' },
  { path: '/my-payslip', title: '我的薪酬单', icon: Money, perm: '' },
  { path: '/my-performance', title: '我的绩效', icon: TrendCharts, perm: '' },
  { path: '/my-training', title: '培训与课程', icon: Reading, perm: 'talent:course:list' },
  { path: '/internal-opportunities', title: '内部机会', icon: Promotion, perm: 'biz:talent:flow:market' },
]

const financeItems = [
  { path: '/my-expenses', title: '我的报销', icon: Tickets, perm: 'biz:finance:expense:view' },
  { path: '/my-timesheets', title: '我的工时', icon: Clock, perm: 'biz:finance:timesheet:view' },
  { path: '/my-carbon', title: '碳活动填报', icon: Flag, perm: 'biz:finance:carbon:submit' },
]

const rndItems = [
  { path: '/my-rnd-tasks', title: '我的任务', icon: Finished, perm: 'biz:devops:task:self:view' },
  { path: '/my-rnd-worklogs', title: '工时填报', icon: Clock, perm: 'biz:devops:worklog:self:view' },
  { path: '/my-rnd-defects', title: '缺陷提报', icon: Warning, perm: 'biz:devops:defect:self:view' },
  { path: '/my-rnd-contribution', title: '我的贡献', icon: TrendCharts, perm: 'biz:devops:contribution:self:view' },
]

const serviceItems = [
  { path: '/my-service-tickets', title: '我的工单', icon: Tickets, perm: 'service:ticket:view' },
  { path: '/service-catalog', title: '服务申请', icon: Grid, perm: 'service:catalog:view' },
  { path: '/service-kb', title: '知识库', icon: Reading, perm: 'service:kb:view' },
  { path: '/my-service-assets', title: '我的资产', icon: Box, perm: 'service:asset:view' },
]

const intelligenceItems = [
  { path: '/ai-workbench', title: '智能工作台', icon: Grid, perm: 'ai:chat:use' },
  { path: '/ai-copilot', title: 'AI Copilot', icon: ChatDotRound, perm: 'ai:chat:use' },
  { path: '/ai-search', title: '知识搜索', icon: Search, perm: 'ai:search:query' },
  { path: '/ai-kg', title: '知识图谱', icon: Share, perm: 'ai:kg:query' },
  { path: '/ai-tools', title: '文本工具箱', icon: Document, perm: 'ai:writing:use' },
  { path: '/ai-agents', title: '智能体', icon: Cpu, perm: 'ai:agent:run' },
  { path: '/ai-dashboards', title: '数据看板', icon: DataAnalysis, perm: 'ai:bi:view' },
  { path: '/ai-insights', title: '洞察与问数', icon: TrendCharts, perm: 'ai:bi:view' },
]

const legalItems = [
  { path: '/legal', title: '法务工作台', icon: Grid, perm: '' },
  { path: '/my-contracts', title: '我的合同', icon: Document, perm: 'legal:contract:view:self' },
  { path: '/my-sign-tasks', title: '我的签署任务', icon: Finished, perm: 'legal:sign:self:execute' },
  { path: '/my-seal-applications', title: '用印申请', icon: Stamp, perm: 'legal:seal:apply' },
  { path: '/my-data-release', title: '数据放行申请', icon: Share, perm: 'legal:dlp:release:apply' },
  { path: '/my-evidence', title: '我的存证', icon: Warning, perm: 'legal:evidence:view' },
]

// 菜单按后端权限点收敛：未授权项不展示，避免前端伪造可访问入口。
const visibleNavItems = computed(() => navItems.filter((item) => !item.perm || auth.can(item.perm)))
const visibleCollabItems = computed(() => collabItems.filter((item) => auth.can(item.perm)))
const visibleTalentItems = computed(() => talentItems.filter((item) => !item.perm || auth.can(item.perm)))
const visibleFinanceItems = computed(() => financeItems.filter((item) => auth.can(item.perm)))
const visibleRndItems = computed(() => rndItems.filter((item) => auth.can(item.perm)))
const visibleServiceItems = computed(() => serviceItems.filter((item) => auth.can(item.perm)))
const visibleIntelligenceItems = computed(() => intelligenceItems.filter((item) => auth.can(item.perm)))
const visibleLegalItems = computed(() => legalItems.filter((item) => !item.perm || auth.can(item.perm)))

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => String(route.meta.title ?? '员工门户'))

async function handleLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}

async function submitPasswordChange() {
  const form = passwordForm.value
  if (!form.oldPassword) {
    ElMessage.warning('请输入旧口令')
    return
  }
  if (form.newPassword.length < 12) {
    ElMessage.warning('新口令至少 12 位，且需包含大写、小写、数字和符号')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新口令不一致')
    return
  }
  passwordSaving.value = true
  try {
    await authApi.changePassword({
      old_password: form.oldPassword,
      new_password: form.newPassword,
    })
    ElMessage.success('口令已修改，其他设备上的登录会话已失效')
    passwordVisible.value = false
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (err) {
    const error = err as { status?: number; message?: string }
    if (error.status === 401) {
      ElMessage.error('旧口令错误')
    } else if (error.status === 400) {
      ElMessage.error(error.message || '新口令不符合强度要求，或与旧口令相同')
    } else {
      ElMessage.error(error.message || '修改口令失败')
    }
  } finally {
    passwordSaving.value = false
  }
}
</script>

<template>
  <el-container class="portal-layout">
    <el-header class="portal-header">
      <div class="brand">
        <span class="brand__logo">丰</span>
        <div class="brand__text">
          <strong>丰麟云枢</strong>
          <small>员工门户</small>
        </div>
      </div>
      <el-menu :default-active="activeMenu" mode="horizontal" router class="portal-menu">
        <el-menu-item v-for="item in visibleNavItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
        <el-sub-menu v-if="visibleCollabItems.length" index="collab">
          <template #title>
            <el-icon><Share /></el-icon>
            <span>云协协作</span>
          </template>
          <el-menu-item v-for="item in visibleCollabItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="visibleTalentItems.length" index="talent">
          <template #title>
            <el-icon><User /></el-icon>
            <span>云才人才</span>
          </template>
          <el-menu-item v-for="item in visibleTalentItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="visibleFinanceItems.length" index="finance">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>云财财务</span>
          </template>
          <el-menu-item v-for="item in visibleFinanceItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="visibleRndItems.length" index="rnd">
          <template #title>
            <el-icon><Finished /></el-icon>
            <span>云研研发</span>
          </template>
          <el-menu-item v-for="item in visibleRndItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="visibleServiceItems.length" index="service">
          <template #title>
            <el-icon><Service /></el-icon>
            <span>云服服务台</span>
          </template>
          <el-menu-item v-for="item in visibleServiceItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="visibleIntelligenceItems.length" index="intelligence">
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>云智 Intelligence</span>
          </template>
          <el-menu-item v-for="item in visibleIntelligenceItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="visibleLegalItems.length" index="legal">
          <template #title>
            <el-icon><Stamp /></el-icon>
            <span>云法 Legal</span>
          </template>
          <el-menu-item v-for="item in visibleLegalItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="header-right">
        <el-button text @click="permissionVisible = true">权限 {{ auth.permissions.length }}</el-button>
        <el-dropdown trigger="click">
          <span class="user-chip">
            <el-avatar :size="26">{{ auth.displayName.slice(0, 1) }}</el-avatar>
            <span>{{ auth.displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>租户：{{ auth.user?.tenant_code || auth.user?.tenant_id || '-' }}</el-dropdown-item>
              <el-dropdown-item disabled>账号：{{ auth.user?.username || '-' }}</el-dropdown-item>
              <el-dropdown-item divided @click="passwordVisible = true">修改密码</el-dropdown-item>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="portal-main">
      <div class="crumb">
        <strong>{{ currentTitle }}</strong>
        <span>{{ route.path }}</span>
      </div>
      <router-view />
    </el-main>

    <el-drawer v-model="permissionVisible" title="当前账号权限" size="440px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="用户">{{ auth.user?.username }} / {{ auth.user?.real_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ auth.user?.tenant_code || auth.user?.tenant_id }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ (auth.user?.roles || []).join('，') || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="perm-tags">
        <el-tag v-for="code in auth.permissions" :key="code" size="small" effect="plain">{{ code }}</el-tag>
        <span v-if="auth.permissions.length === 0" class="fl-muted">令牌未返回权限码</span>
      </div>
      <p class="fl-muted">菜单与按钮均按后端权限码收敛；无权限时操作会返回后端真实 403 错误，前端不伪造数据。</p>
    </el-drawer>

    <el-dialog v-model="passwordVisible" title="修改密码" width="440px">
      <el-form label-width="96px">
        <el-form-item label="旧口令">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新口令">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新口令">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <p class="fl-muted">新口令至少 12 位，需包含大写、小写、数字和符号；修改成功后，其他设备上的登录会话会失效。</p>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="submitPasswordChange">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
  background: var(--fl-color-bg);
}
.portal-header {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid var(--fl-color-border);
  padding: 0 24px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.brand__logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--fl-color-primary);
  color: #fff;
  font-weight: 700;
}
.brand__text strong {
  display: block;
  font-size: 15px;
}
.brand__text small {
  color: var(--fl-color-text-secondary);
  font-size: 11px;
}
.portal-menu {
  flex: 1;
  border-bottom: none;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
}
.portal-main {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 20px 24px 40px;
}
.crumb {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
}
.crumb strong {
  font-size: 18px;
}
.crumb span {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 16px 0;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
