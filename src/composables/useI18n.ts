import { ref, computed } from 'vue'

/**
 * 国际化 Composable
 */

export type Locale = 'zh-CN' | 'en-US'

export interface LocaleConfig {
  locale: Locale
  label: string
  flag: string
}

export const locales: LocaleConfig[] = [
  { locale: 'zh-CN', label: '简体中文', flag: '🇨🇳' },
  { locale: 'en-US', label: 'English', flag: '🇺🇸' }
]

// 翻译资源
const messages: Record<Locale, Record<string, any>> = {
  'zh-CN': {
    // 通用
    common: {
      save: '保存',
      cancel: '取消',
      delete: '删除',
      edit: '编辑',
      create: '创建',
      search: '搜索',
      loading: '加载中...',
      noData: '暂无数据',
      confirm: '确认',
      success: '成功',
      error: '错误',
      warning: '警告',
      info: '提示'
    },
    // 导航
    nav: {
      dashboard: '仪表盘',
      missions: '任务',
      history: '历史',
      market: '市场',
      templates: '模板',
      settings: '设置'
    },
    // 任务
    mission: {
      title: '任务标题',
      description: '任务描述',
      status: '状态',
      createdAt: '创建时间',
      updatedAt: '更新时间',
      pending: '待处理',
      running: '运行中',
      completed: '已完成',
      failed: '失败',
      paused: '已暂停',
      create: '创建任务',
      edit: '编辑任务',
      delete: '删除任务',
      start: '开始任务',
      pause: '暂停任务',
      cancel: '取消任务',
      retry: '重试'
    },
    // Agent
    agent: {
      name: '名称',
      description: '描述',
      version: '版本',
      status: '状态',
      install: '安装',
      uninstall: '卸载',
      enable: '启用',
      disable: '禁用',
      evolve: '进化',
      execute: '执行'
    },
    // 错误
    error: {
      networkError: '网络错误，请检查您的网络连接',
      serverError: '服务器错误，请稍后重试',
      unauthorized: '未授权，请重新登录',
      forbidden: '没有权限访问此资源',
      notFound: '资源不存在',
      unknown: '未知错误'
    },
    // 表单验证
    validation: {
      required: '此字段为必填项',
      email: '请输入有效的邮箱地址',
      minLength: '至少需要 {min} 个字符',
      maxLength: '最多只能输入 {max} 个字符',
      pattern: '格式不正确'
    }
  },
  'en-US': {
    // Common
    common: {
      save: 'Save',
      cancel: 'Cancel',
      delete: 'Delete',
      edit: 'Edit',
      create: 'Create',
      search: 'Search',
      loading: 'Loading...',
      noData: 'No Data',
      confirm: 'Confirm',
      success: 'Success',
      error: 'Error',
      warning: 'Warning',
      info: 'Info'
    },
    // Navigation
    nav: {
      dashboard: 'Dashboard',
      missions: 'Missions',
      history: 'History',
      market: 'Market',
      templates: 'Templates',
      settings: 'Settings'
    },
    // Mission
    mission: {
      title: 'Mission Title',
      description: 'Mission Description',
      status: 'Status',
      createdAt: 'Created At',
      updatedAt: 'Updated At',
      pending: 'Pending',
      running: 'Running',
      completed: 'Completed',
      failed: 'Failed',
      paused: 'Paused',
      create: 'Create Mission',
      edit: 'Edit Mission',
      delete: 'Delete Mission',
      start: 'Start Mission',
      pause: 'Pause Mission',
      cancel: 'Cancel Mission',
      retry: 'Retry'
    },
    // Agent
    agent: {
      name: 'Name',
      description: 'Description',
      version: 'Version',
      status: 'Status',
      install: 'Install',
      uninstall: 'Uninstall',
      enable: 'Enable',
      disable: 'Disable',
      evolve: 'Evolve',
      execute: 'Execute'
    },
    // Error
    error: {
      networkError: 'Network error, please check your connection',
      serverError: 'Server error, please try again later',
      unauthorized: 'Unauthorized, please login again',
      forbidden: 'Access denied',
      notFound: 'Resource not found',
      unknown: 'Unknown error'
    },
    // Validation
    validation: {
      required: 'This field is required',
      email: 'Please enter a valid email address',
      minLength: 'At least {min} characters required',
      maxLength: 'Maximum {max} characters allowed',
      pattern: 'Invalid format'
    }
  }
}

/**
 * 国际化 Composable
 */
export function useI18n() {
  const currentLocale = ref<Locale>('zh-CN')

  // 从本地存储恢复语言设置
  function restoreLocale() {
    const stored = localStorage.getItem('locale')
    if (stored && (stored === 'zh-CN' || stored === 'en-US')) {
      currentLocale.value = stored
    }
  }

  // 保存语言设置
  function saveLocale() {
    localStorage.setItem('locale', currentLocale.value)
  }

  // 切换语言
  function setLocale(locale: Locale) {
    currentLocale.value = locale
    saveLocale()
    // 更新 document 语言
    document.documentElement.lang = locale
  }

  // 获取翻译
  function t(key: string, params?: Record<string, string | number>): string {
    const keys = key.split('.')
    let value: any = messages[currentLocale.value]

    for (const k of keys) {
      if (value && typeof value === 'object' && k in value) {
        value = value[k]
      } else {
        // 如果找不到翻译，返回 key
        return key
      }
    }

    if (typeof value !== 'string') {
      return key
    }

    // 替换参数
    if (params) {
      Object.entries(params).forEach(([paramKey, paramValue]) => {
        value = value.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), String(paramValue))
      })
    }

    return value
  }

  // 计算属性版本
  const locale = computed(() => currentLocale.value)

  return {
    locale,
    currentLocale,
    locales,
    restoreLocale,
    setLocale,
    t
  }
}

// 导出单例
export const i18n = useI18n()
