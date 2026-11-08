export interface AdminMenuItem {
  path: string
  name: string
  title: string
  module: string
  icon: string
  requiredPermission?: string
}

/**
 * 13 个中台管理页面。菜单项与 UIUX 规范 A-02/A-03/A-09/A-16/A-18/A-19/A-20/A-22/A-23/A-24/A-25/A-26
 * 及网关治理页对齐；requiredPermission 缺失时菜单显示“受限”标识，页面内动作再按权限码收敛。
 */
export const adminMenu: AdminMenuItem[] = [
  { path: '/overview', name: 'overview', title: '运营概览', module: '报表中心', icon: 'Odometer' },
  {
    path: '/tenants',
    name: 'tenants',
    title: '租户管理',
    module: '租户中台',
    icon: 'OfficeBuilding',
    requiredPermission: 'platform:tenant:manage',
  },
  {
    path: '/iam',
    name: 'iam',
    title: '身份与权限',
    module: 'IAM',
    icon: 'User',
    requiredPermission: 'hub:iam:user:read',
  },
  {
    path: '/workflow',
    name: 'workflow',
    title: '流程中心',
    module: '工作流中台',
    icon: 'Share',
    requiredPermission: 'tenant:workflow:def:manage',
  },
  {
    path: '/messages',
    name: 'messages',
    title: '消息中心',
    module: '消息中台',
    icon: 'Bell',
    requiredPermission: 'tenant:message:template:manage',
  },
  {
    path: '/files',
    name: 'files',
    title: '文件中心',
    module: '文件中台',
    icon: 'Folder',
    requiredPermission: 'tenant:file:policy:manage',
  },
  {
    path: '/audit',
    name: 'audit',
    title: '审计中心',
    module: '审计中台',
    icon: 'Document',
    requiredPermission: 'hub:audit:read',
  },
  {
    path: '/search',
    name: 'search',
    title: '搜索中心',
    module: '搜索中台',
    icon: 'Search',
    requiredPermission: 'platform:search:index:manage',
  },
  {
    path: '/openapi',
    name: 'openapi',
    title: '开放平台',
    module: '开放能力',
    icon: 'Connection',
    requiredPermission: 'platform:openapi:calllog:view',
  },
  {
    path: '/report',
    name: 'report',
    title: '报表中心',
    module: '报表中台',
    icon: 'DataAnalysis',
    requiredPermission: 'tenant:report:metric:manage',
  },
  {
    path: '/lowcode',
    name: 'lowcode',
    title: '低代码',
    module: '低代码中台',
    icon: 'Grid',
    requiredPermission: 'tenant:lowcode:app:design',
  },
  {
    path: '/ai',
    name: 'ai',
    title: 'AI 中台',
    module: 'AI 中台',
    icon: 'Cpu',
    requiredPermission: 'platform:ai:model:manage',
  },
  {
    path: '/gateway',
    name: 'gateway',
    title: '网关治理',
    module: '网关',
    icon: 'Guide',
    requiredPermission: 'platform:gateway:route:manage',
  },
]
