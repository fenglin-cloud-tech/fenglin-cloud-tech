<script setup lang="ts">
import { GapNotice, PageHeader } from '@fenglin/ui-admin'

const gapItems = [
  '考勤记录、异常处理与月度汇总的管理界面尚未上线，本页只做缺口说明，不展示任何模拟数据。',
  '员工侧打卡与请假申请已可用，入口在员工门户；本页对应的是管理端的排班、异常处理与汇总能力。',
  '假期额度与考勤规则属于租户配置，管理端维护界面尚未上线。',
  '月度考勤汇总依赖排班与打卡数据，管理端暂无法查看。',
]
</script>

<template>
  <div>
    <PageHeader
      module="云才 Talent"
      title="考勤与假期"
      description="考勤记录、请假与假期额度；管理端界面尚未上线，本页只做缺口说明。"
    />

    <GapNotice :items="gapItems" />

    <div class="fl-card">
      <el-empty description="该功能尚未上线，接入后在此展示，不展示虚构数据" />
    </div>
  </div>
</template>
