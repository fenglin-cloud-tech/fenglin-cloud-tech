<script setup lang="ts">
import { GapNotice, PageHeader } from '@fenglin/ui-admin'

const gapItems = [
  '云才后端当前没有薪酬方案、薪资档案、薪酬单、发放批次的 Controller，本页不展示虚构金额。',
  '需要新增的接口建议：GET/POST /api/talent/v1/payroll/plans、GET /api/talent/v1/payroll/slips、POST /api/talent/v1/payroll/batches。',
  '薪酬数据属高敏字段，接入时需补充字段级权限与脱敏规则，不能沿用普通员工查询接口。',
  '发放与个税计算依赖财务/银行侧能力，暂未接入。',
]
</script>

<template>
  <div>
    <PageHeader
      module="云才 Talent"
      title="薪酬管理"
      description="薪酬方案、薪资档案与发放；当前后端能力未覆盖，本页只做缺口说明。"
    />

    <GapNotice :items="gapItems" />

    <div class="fl-card">
      <el-empty description="后端未提供薪酬接口，待接口就绪后接入，不展示虚构金额" />
    </div>
  </div>
</template>
