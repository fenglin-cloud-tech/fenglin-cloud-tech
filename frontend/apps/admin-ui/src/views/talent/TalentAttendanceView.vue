<script setup lang="ts">
import { GapNotice, PageHeader } from '@fenglin/ui-admin'

const gapItems = [
  '云才后端当前没有考勤记录、排班、打卡、请假单的 Controller，也没有对应数据表，页面不提供任何模拟数据。',
  '需要新增的接口建议：GET /api/talent/v1/attendance/records、POST /api/talent/v1/attendance/leaves、GET /api/talent/v1/attendance/leaves/my。',
  '假期额度与考勤规则属于租户配置，需要接口暴露后才可维护。',
  '月度考勤汇总依赖排班与打卡数据，暂无法计算。',
]
</script>

<template>
  <div>
    <PageHeader
      module="云才 Talent"
      title="考勤与假期"
      description="考勤记录、请假与假期额度；当前后端能力未覆盖，本页只做缺口说明。"
    />

    <GapNotice :items="gapItems" />

    <div class="fl-card">
      <el-empty description="后端未提供考勤/假期接口，待接口就绪后接入，不展示虚构数据" />
    </div>
  </div>
</template>
