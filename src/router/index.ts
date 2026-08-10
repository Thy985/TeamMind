import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: {
      title: 'Dashboard',
      description: '快速启动任务、查看最近状态'
    }
  },
  {
    path: '/missions',
    children: [
      {
        path: '',
        redirect: '/'
      },
      {
        path: 'new',
        name: 'mission-new',
        component: () => import('@/pages/MissionDetailPage.vue'),
        meta: {
          title: '新建任务',
          hasCanvas: true
        }
      },
      {
        path: ':id',
        name: 'mission-detail',
        component: () => import('@/pages/MissionDetailPage.vue'),
        meta: {
          title: '任务详情',
          hasCanvas: true
        }
      }
    ]
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('@/pages/HistoryPage.vue'),
    meta: {
      title: '任务历史',
      description: '查看任务历史记录'
    }
  },
  {
    path: '/market',
    name: 'market',
    component: () => import('@/pages/MarketPage.vue'),
    meta: {
      title: 'Agent 市场',
      description: '浏览和安装 Agent'
    }
  },
  {
    path: '/templates',
    name: 'templates',
    component: () => import('@/pages/TemplatesPage.vue'),
    meta: {
      title: '团队模板',
      description: '管理和使用团队模板'
    }
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: {
      title: '设置',
      description: '应用配置'
    }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  }
})

// 路由守卫 - 更新页面标题
router.beforeEach((to, _from, next) => {
  const title = to.meta.title as string | undefined
  if (title) {
    document.title = `${title} | TeamMind`
  }
  next()
})

export default router
