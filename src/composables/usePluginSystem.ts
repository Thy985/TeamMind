/**
 * 插件系统 - 扩展性和模块化架构
 */

import { ref, computed } from 'vue'

/**
 * 插件接口
 */
export interface Plugin {
  id: string
  name: string
  version: string
  description: string
  author: string
  enabled: boolean
  installed: boolean
  settings?: Record<string, any>
  hooks?: PluginHooks
  resources?: PluginResources
}

export interface PluginHooks {
  beforeMount?: () => void | Promise<void>
  afterMount?: () => void | Promise<void>
  beforeUnmount?: () => void | Promise<void>
  afterUnmount?: () => void | Promise<void>
  onRouteChange?: (to: any, from: any) => void
  onError?: (error: Error) => void
}

export interface PluginResources {
  components?: Record<string, any>
  directives?: Record<string, any>
  composables?: Record<string, any>
  routes?: any[]
  store?: any
}

/**
 * 插件市场项
 */
export interface MarketplacePlugin {
  id: string
  name: string
  version: string
  description: string
  author: string
  icon: string
  downloads: number
  rating: number
  tags: string[]
  category: 'visualization' | 'automation' | 'integration' | 'utility' | 'ai'
  price: number
  isInstalled: boolean
  isEnabled: boolean
}

/**
 * 插件 Composable
 */
export function usePluginSystem() {
  const installedPlugins = ref<Plugin[]>([])
  const marketplacePlugins = ref<MarketplacePlugin[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // 计算属性
  const enabledPlugins = computed(() => installedPlugins.value.filter(p => p.enabled))
  const disabledPlugins = computed(() => installedPlugins.value.filter(p => !p.enabled))

  /**
   * 从本地存储加载插件
   */
  function loadInstalledPlugins() {
    const stored = localStorage.getItem('installedPlugins')
    if (stored) {
      try {
        installedPlugins.value = JSON.parse(stored)
      } catch (err) {
        console.error('Failed to load plugins:', err)
        installedPlugins.value = []
      }
    }
  }

  /**
   * 保存插件到本地存储
   */
  function saveInstalledPlugins() {
    localStorage.setItem('installedPlugins', JSON.stringify(installedPlugins.value))
  }

  /**
   * 安装插件
   */
  async function installPlugin(pluginId: string): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      // 模拟 API 调用
      await new Promise(resolve => setTimeout(resolve, 1000))

      // 查找市场中的插件
      const marketplacePlugin = marketplacePlugins.value.find(p => p.id === pluginId)
      if (!marketplacePlugin) {
        throw new Error('Plugin not found in marketplace')
      }

      // 创建插件实例
      const plugin: Plugin = {
        id: marketplacePlugin.id,
        name: marketplacePlugin.name,
        version: marketplacePlugin.version,
        description: marketplacePlugin.description,
        author: marketplacePlugin.author,
        enabled: false,
        installed: true,
        settings: {}
      }

      installedPlugins.value.push(plugin)
      saveInstalledPlugins()

      // 更新市场状态
      marketplacePlugin.isInstalled = true

      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : '安装失败'
      console.error('Failed to install plugin:', err)
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 卸载插件
   */
  async function uninstallPlugin(pluginId: string): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      // 模拟 API 调用
      await new Promise(resolve => setTimeout(resolve, 500))

      // 找到并移除插件
      const pluginIndex = installedPlugins.value.findIndex(p => p.id === pluginId)
      if (pluginIndex !== -1) {
        const plugin = installedPlugins.value[pluginIndex]

        // 执行卸载钩子
        if (plugin.hooks?.beforeUnmount) {
          await plugin.hooks.beforeUnmount()
        }

        installedPlugins.value.splice(pluginIndex, 1)
        saveInstalledPlugins()
      }

      // 更新市场状态
      const marketplacePlugin = marketplacePlugins.value.find(p => p.id === pluginId)
      if (marketplacePlugin) {
        marketplacePlugin.isInstalled = false
      }

      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : '卸载失败'
      console.error('Failed to uninstall plugin:', err)
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 启用插件
   */
  async function enablePlugin(pluginId: string): Promise<boolean> {
    try {
      const plugin = installedPlugins.value.find(p => p.id === pluginId)
      if (!plugin) {
        throw new Error('Plugin not found')
      }

      // 执行启用前钩子
      if (plugin.hooks?.beforeMount) {
        await plugin.hooks.beforeMount()
      }

      plugin.enabled = true
      saveInstalledPlugins()

      // 执行启用后钩子
      if (plugin.hooks?.afterMount) {
        await plugin.hooks.afterMount()
      }

      return true
    } catch (err) {
      console.error('Failed to enable plugin:', err)
      return false
    }
  }

  /**
   * 禁用插件
   */
  async function disablePlugin(pluginId: string): Promise<boolean> {
    try {
      const plugin = installedPlugins.value.find(p => p.id === pluginId)
      if (!plugin) {
        throw new Error('Plugin not found')
      }

      plugin.enabled = false
      saveInstalledPlugins()

      return true
    } catch (err) {
      console.error('Failed to disable plugin:', err)
      return false
    }
  }

  /**
   * 更新插件设置
   */
  function updatePluginSettings(pluginId: string, settings: Record<string, any>) {
    const plugin = installedPlugins.value.find(p => p.id === pluginId)
    if (plugin) {
      plugin.settings = { ...plugin.settings, ...settings }
      saveInstalledPlugins()
    }
  }

  /**
   * 获取插件设置
   */
  function getPluginSettings(pluginId: string): Record<string, any> {
    const plugin = installedPlugins.value.find(p => p.id === pluginId)
    return plugin?.settings || {}
  }

  /**
   * 获取市场插件列表
   */
  async function fetchMarketplacePlugins(): Promise<void> {
    isLoading.value = true

    try {
      // 模拟市场数据
      await new Promise(resolve => setTimeout(resolve, 500))

      marketplacePlugins.value = [
        {
          id: 'plugin-analytics',
          name: '高级数据分析',
          version: '1.2.0',
          description: '提供更详细的数据分析和可视化功能',
          author: 'TeamMind Team',
          icon: '📊',
          downloads: 1234,
          rating: 4.8,
          tags: ['analytics', 'charts', 'visualization'],
          category: 'visualization',
          price: 0,
          isInstalled: false,
          isEnabled: false
        },
        {
          id: 'plugin-slack',
          name: 'Slack 集成',
          version: '2.0.0',
          description: '将任务状态同步到 Slack 频道',
          author: 'Integration Team',
          icon: '💬',
          downloads: 856,
          rating: 4.5,
          tags: ['integration', 'slack', 'notification'],
          category: 'integration',
          price: 0,
          isInstalled: false,
          isEnabled: false
        },
        {
          id: 'plugin-github',
          name: 'GitHub 集成',
          version: '1.5.0',
          description: '与 GitHub 仓库同步，自动化代码审查流程',
          author: 'DevOps Team',
          icon: '🐙',
          downloads: 2341,
          rating: 4.9,
          tags: ['github', 'integration', 'automation'],
          category: 'integration',
          price: 0,
          isInstalled: false,
          isEnabled: false
        },
        {
          id: 'plugin-ai-advanced',
          name: '高级 AI 助手',
          version: '1.0.0',
          description: '更强大的 AI 推荐和智能分析功能',
          author: 'AI Lab',
          icon: '🤖',
          downloads: 3421,
          rating: 4.7,
          tags: ['ai', 'ml', 'intelligence'],
          category: 'ai',
          price: 9.99,
          isInstalled: false,
          isEnabled: false
        },
        {
          id: 'plugin-auto-task',
          name: '自动任务调度',
          version: '1.3.0',
          description: '定时自动执行任务，提高工作效率',
          author: 'Automation Team',
          icon: '⚡',
          downloads: 1567,
          rating: 4.6,
          tags: ['automation', 'scheduler', 'cron'],
          category: 'automation',
          price: 0,
          isInstalled: false,
          isEnabled: false
        },
        {
          id: 'plugin-export',
          name: '高级导出',
          version: '1.1.0',
          description: '支持导出为 PDF、Excel、Word 等格式',
          author: 'Tools Team',
          icon: '📄',
          downloads: 987,
          rating: 4.4,
          tags: ['export', 'pdf', 'excel'],
          category: 'utility',
          price: 0,
          isInstalled: false,
          isEnabled: false
        }
      ]
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取市场插件失败'
      console.error('Failed to fetch marketplace plugins:', err)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 搜索插件
   */
  function searchPlugins(query: string): MarketplacePlugin[] {
    const q = query.toLowerCase()
    return marketplacePlugins.value.filter(p =>
      p.name.toLowerCase().includes(q) ||
      p.description.toLowerCase().includes(q) ||
      p.tags.some(tag => tag.toLowerCase().includes(q))
    )
  }

  /**
   * 按分类筛选插件
   */
  function filterByCategory(category: MarketplacePlugin['category']): MarketplacePlugin[] {
    return marketplacePlugins.value.filter(p => p.category === category)
  }

  /**
   * 检查插件是否有更新
   */
  async function checkPluginUpdates(): Promise<Map<string, string>> {
    const updates = new Map<string, string>()

    for (const plugin of installedPlugins.value) {
      const marketplace = marketplacePlugins.value.find(p => p.id === plugin.id)
      if (marketplace && marketplace.version !== plugin.version) {
        updates.set(plugin.id, marketplace.version)
      }
    }

    return updates
  }

  // 初始化
  loadInstalledPlugins()

  return {
    installedPlugins,
    marketplacePlugins,
    enabledPlugins,
    disabledPlugins,
    isLoading,
    error,
    installPlugin,
    uninstallPlugin,
    enablePlugin,
    disablePlugin,
    updatePluginSettings,
    getPluginSettings,
    fetchMarketplacePlugins,
    searchPlugins,
    filterByCategory,
    checkPluginUpdates
  }
}

/**
 * 插件 Hooks 管理器
 */
export function usePluginHooks() {
  // 钩子回调：接受任意参数，可能同步返回或返回 Promise
  type HookCallback = (...args: any[]) => any
  const hooks = ref<Map<string, HookCallback>>(new Map())

  /**
   * 注册钩子
   */
  function registerHook(name: string, callback: HookCallback) {
    hooks.value.set(name, callback)
  }

  /**
   * 触发钩子
   */
  async function triggerHook(name: string, ...args: any[]) {
    const callback = hooks.value.get(name)
    if (callback) {
      try {
        const result = callback(...args)
        if (result instanceof Promise) {
          await result
        }
      } catch (error) {
        console.error(`Hook ${name} failed:`, error)
      }
    }
  }

  return {
    hooks,
    registerHook,
    triggerHook
  }
}
