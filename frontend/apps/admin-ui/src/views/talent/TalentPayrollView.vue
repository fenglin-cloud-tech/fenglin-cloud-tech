<script setup lang="ts">
import { GapNotice, PageHeader } from '@fenglin/ui-admin'

const gapItems = [
  '薪酬方案、薪资档案与发放批次的管理界面尚未上线，本页不展示虚构金额。',
  '员工侧「我的薪资单」已可用，入口在员工门户；本页对应的是管理端的薪酬配置与发放能力。',
  '薪酬数据属高敏字段，管理端接入时需补充字段级权限与脱敏规则，不能沿用普通员工查询接口。',
  '发放与个税计算依赖财务/银行侧能力，尚未接入。',
]
</script>

<template>
  <div>
    <PageHeader
      module="云才 Talent"
      title="薪酬管理"
      description="薪酬方案、薪资档案与发放；管理端界面尚未上线，本页只做缺口说明。"
    />

    <GapNotice :items="gapItems" />

    <div class="fl-card">
      <el-empty description="该功能尚未上线，接入后在此展示，不展示虚构金额" />
    </div>
  </div>
</template>
