import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Agent, InstalledAgent } from '@/types'
import { agentApi } from '@/api/axios'

export const useAgentMarketStore = defineStore('agentMarket', () => {
  // ==================== State ====================
  const agents = ref<Agent[]>([])
  const installedAgents = ref<InstalledAgent[]>([])
  const selectedAgent = ref<Agent | null>(null)

  // ==================== Computed ====================
  const enabledAgents = computed(() =>
    installedAgents.value.filter(agent => agent.enabled)
  )

  const disabledAgents = computed(() =>
    installedAgents.value.filter(agent => !agent.enabled)
  )

  const agentStats = computed(() => ({
    total: agents.value.length,
    installed: installedAgents.value.length,
    enabled: enabledAgents.value.length,
    disabled: disabledAgents.value.length
  }))

  const averageRating = computed(() => {
    if (agents.value.length === 0) return 0
    const sum = agents.value.reduce((acc, agent) => acc + (agent.rating || 0), 0)
    return Math.round((sum / agents.value.length) * 10) / 10
  })

  // ==================== Actions ====================

  /**
   * 获取所有 Agent
   */
  async function fetchAgents() {
    try {
      const response = await agentApi.list()
      if (response.success && response.data) {
        agents.value = Array.isArray(response.data) ? response.data : []
      }
    } catch (error) {
      console.error('Failed to fetch agents:', error)
      throw error
    }
  }

  /**
   * 获取已安装的 Agent
   */
  async function fetchInstalledAgents() {
    try {
      const response = await agentApi.installed()
      if (response.success && response.data) {
        installedAgents.value = Array.isArray(response.data) ? response.data : []
      }
    } catch (error) {
      console.error('Failed to fetch installed agents:', error)
      throw error
    }
  }

  /**
   * 获取单个 Agent 详情
   */
  async function fetchAgent(id: string) {
    try {
      const response = await agentApi.get(id)
      if (response.success && response.data) {
        selectedAgent.value = response.data as Agent
      }
    } catch (error) {
      console.error('Failed to fetch agent:', error)
      throw error
    }
  }

  /**
   * 创建 Agent
   */
  async function createAgent(data: {
    name: string
    description?: string
    prompt?: string
    permissions?: string[]
  }) {
    try {
      const response = await agentApi.create(data)
      if (response.success && response.data) {
        await fetchInstalledAgents()
        return response.data
      }
    } catch (error) {
      console.error('Failed to create agent:', error)
      throw error
    }
  }

  /**
   * 安装 Agent
   */
  async function installAgent(id: string) {
    try {
      const response = await agentApi.install(id)
      if (response.success) {
        await fetchInstalledAgents()
      }
    } catch (error) {
      console.error('Failed to install agent:', error)
      throw error
    }
  }

  /**
   * 卸载 Agent
   */
  async function uninstallAgent(id: string) {
    try {
      const response = await agentApi.uninstall(id)
      if (response.success) {
        installedAgents.value = installedAgents.value.filter(a => a.id !== id)
      }
    } catch (error) {
      console.error('Failed to uninstall agent:', error)
      throw error
    }
  }

  /**
   * 切换 Agent 启用状态
   */
  async function toggleAgent(id: string, enabled: boolean) {
    try {
      const response = await agentApi.toggle(id, enabled)
      if (response.success) {
        const agent = installedAgents.value.find(a => a.id === id)
        if (agent) {
          agent.enabled = enabled
        }
      }
    } catch (error) {
      console.error('Failed to toggle agent:', error)
      throw error
    }
  }

  /**
   * Agent 进化
   */
  async function evolveAgent(
    id: string,
    data: {
      type: string
      reason?: string
      context?: Record<string, unknown>
      automatic?: boolean
    }
  ) {
    try {
      const response = await agentApi.evolve(id, data)
      if (response.success) {
        await fetchAgent(id)
      }
    } catch (error) {
      console.error('Failed to evolve agent:', error)
      throw error
    }
  }

  /**
   * 获取 Agent 进化历史
   */
  async function fetchEvolutionHistory(id: string) {
    try {
      const response = await agentApi.evolutionHistory(id)
      if (response.success && response.data) {
        return response.data
      }
    } catch (error) {
      console.error('Failed to fetch evolution history:', error)
      throw error
    }
  }

  /**
   * 回滚 Agent 版本
   */
  async function rollbackAgent(agentId: string, recordId: number) {
    try {
      const response = await agentApi.rollback(agentId, recordId)
      if (response.success) {
        await fetchAgent(agentId)
      }
    } catch (error) {
      console.error('Failed to rollback agent:', error)
      throw error
    }
  }

  /**
   * 执行 Agent
   */
  async function executeAgent(
    id: string,
    data: {
      prompt: string
      missionId?: string
      input?: Record<string, unknown>
    }
  ) {
    try {
      const response = await agentApi.execute(id, data)
      if (response.success && response.data) {
        return response.data
      }
    } catch (error) {
      console.error('Failed to execute agent:', error)
      throw error
    }
  }

  /**
   * 设置选中的 Agent
   */
  function setSelectedAgent(agent: Agent | null) {
    selectedAgent.value = agent
  }

  /**
   * 清空选中的 Agent
   */
  function clearSelectedAgent() {
    selectedAgent.value = null
  }

  return {
    // State
    agents,
    installedAgents,
    selectedAgent,

    // Computed
    enabledAgents,
    disabledAgents,
    agentStats,
    averageRating,

    // Actions
    fetchAgents,
    fetchInstalledAgents,
    fetchAgent,
    createAgent,
    installAgent,
    uninstallAgent,
    toggleAgent,
    evolveAgent,
    fetchEvolutionHistory,
    rollbackAgent,
    executeAgent,
    setSelectedAgent,
    clearSelectedAgent
  }
})
