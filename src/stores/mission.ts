import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Mission, MissionNode, MissionEdge, LogEntry, MissionStatus } from '@/types'
import { missionApi } from '@/api/axios'
import { useAsyncOperation, usePaginatedAsyncOperation } from '@/composables/useAsyncOperation'
import { validatePaginatedResponse, MissionSchema } from '@/utils/validation'

export const useMissionStore = defineStore('mission', () => {
  // ==================== State ====================
  const missions = ref<Mission[]>([])
  const currentMission = ref<Mission | null>(null)
  const nodes = ref<MissionNode[]>([])
  const edges = ref<MissionEdge[]>([])
  const logs = ref<LogEntry[]>([])
  const totalCount = ref(0)

  // ==================== Computed ====================
  const missionStatus = computed<MissionStatus>(() => currentMission.value?.status ?? 'pending')

  const nodesByStatus = computed(() => {
    const result: Record<string, MissionNode[]> = {
      idle: [],
      running: [],
      success: [],
      error: [],
      waiting: []
    }
    nodes.value.forEach(node => {
      result[node.data.status].push(node)
    })
    return result
  })

  const logsByType = computed(() => {
    const result: Record<string, LogEntry[]> = {
      task: [],
      tool: [],
      discussion: [],
      resolution: [],
      warning: [],
      error: []
    }
    logs.value.forEach(log => {
      result[log.type].push(log)
    })
    return result
  })

  const stats = computed(() => {
    const total = missions.value.length
    const completed = missions.value.filter(m => m.status === 'completed').length
    const running = missions.value.filter(m => m.status === 'running').length
    const pending = missions.value.filter(m => m.status === 'pending').length
    const successRate = total > 0 ? Math.round((completed / total) * 100) : 0
    return { total, completed, running, pending, successRate }
  })

  // ==================== Actions ====================

  /**
   * 获取任务列表
   */
  async function fetchMissions(page = 1, pageSize = 20) {
    try {
      const response = await missionApi.list(page, pageSize)
      if (response.success && response.data) {
        missions.value = (response.data.items || []) as Mission[]
        totalCount.value = response.data.total || 0
      }
    } catch (error) {
      console.error('Failed to fetch missions:', error)
      throw error
    }
  }

  /**
   * 获取单个任务详情
   */
  async function fetchMission(id: string) {
    try {
      const response = await missionApi.get(id)
      if (response.success && response.data) {
        setMission(response.data as Mission)
      }
    } catch (error) {
      console.error('Failed to fetch mission:', error)
      throw error
    }
  }

  /**
   * 创建任务
   */
  async function createMission(data: {
    title: string
    description?: string
    agentIds?: string[]
    templateId?: string
  }) {
    try {
      const response = await missionApi.create(data)
      if (response.success && response.data) {
        // 获取新创建的mission详情
        await fetchMission(response.data.id)
        return response.data.id
      }
    } catch (error) {
      console.error('Failed to create mission:', error)
      throw error
    }
  }

  /**
   * 更新任务
   */
  async function updateMission(id: string, data: Partial<Mission>) {
    try {
      const response = await missionApi.update(id, data)
      if (response.success) {
        // 更新本地状态
        const mission = missions.value.find(m => m.id === id)
        if (mission) {
          Object.assign(mission, data)
        }
        if (currentMission.value?.id === id) {
          Object.assign(currentMission.value, data)
        }
      }
    } catch (error) {
      console.error('Failed to update mission:', error)
      throw error
    }
  }

  /**
   * 删除任务
   */
  async function deleteMission(id: string) {
    try {
      const response = await missionApi.delete(id)
      if (response.success) {
        missions.value = missions.value.filter(m => m.id !== id)
        if (currentMission.value?.id === id) {
          currentMission.value = null
        }
      }
    } catch (error) {
      console.error('Failed to delete mission:', error)
      throw error
    }
  }

  /**
   * 克隆任务
   */
  async function cloneMission(id: string) {
    try {
      const response = await missionApi.clone(id)
      if (response.success && response.data) {
        await fetchMission(response.data.id)
        return response.data.id
      }
    } catch (error) {
      console.error('Failed to clone mission:', error)
      throw error
    }
  }

  /**
   * 启动任务
   */
  async function startMission(id: string) {
    try {
      const response = await missionApi.start(id)
      if (response.success) {
        await fetchMission(id)
      }
    } catch (error) {
      console.error('Failed to start mission:', error)
      throw error
    }
  }

  /**
   * 暂停任务
   */
  async function pauseMission(id: string) {
    try {
      const response = await missionApi.pause(id)
      if (response.success) {
        await fetchMission(id)
      }
    } catch (error) {
      console.error('Failed to pause mission:', error)
      throw error
    }
  }

  /**
   * 恢复任务
   */
  async function resumeMission(id: string) {
    try {
      const response = await missionApi.resume(id)
      if (response.success) {
        await fetchMission(id)
      }
    } catch (error) {
      console.error('Failed to resume mission:', error)
      throw error
    }
  }

  /**
   * 取消任务
   */
  async function cancelMission(id: string) {
    try {
      const response = await missionApi.cancel(id)
      if (response.success) {
        await fetchMission(id)
      }
    } catch (error) {
      console.error('Failed to cancel mission:', error)
      throw error
    }
  }

  /**
   * 重试节点
   */
  async function retryNode(missionId: string, nodeId: string) {
    try {
      const response = await missionApi.retry(missionId, nodeId)
      if (response.success) {
        await fetchMission(missionId)
      }
    } catch (error) {
      console.error('Failed to retry node:', error)
      throw error
    }
  }

  /**
   * 跳过节点
   */
  async function skipNode(missionId: string, nodeId: string) {
    try {
      const response = await missionApi.skip(missionId, nodeId)
      if (response.success) {
        await fetchMission(missionId)
      }
    } catch (error) {
      console.error('Failed to skip node:', error)
      throw error
    }
  }

  /**
   * 设置当前任务
   */
  function setMission(mission: Mission) {
    currentMission.value = mission
    nodes.value = mission.nodes || []
    edges.value = mission.edges || []
    logs.value = mission.logs || []
  }

  /**
   * 清空当前任务
   */
  function clearCurrentMission() {
    currentMission.value = null
    nodes.value = []
    edges.value = []
    logs.value = []
  }

  /**
   * 添加日志
   */
  function addLog(log: LogEntry) {
    logs.value.push(log)
  }

  /**
   * 清空日志
   */
  function clearLogs() {
    logs.value = []
  }

  return {
    // State
    missions,
    currentMission,
    nodes,
    edges,
    logs,
    totalCount,

    // Computed
    missionStatus,
    nodesByStatus,
    logsByType,
    stats,

    // Actions
    fetchMissions,
    fetchMission,
    createMission,
    updateMission,
    deleteMission,
    cloneMission,
    startMission,
    pauseMission,
    resumeMission,
    cancelMission,
    retryNode,
    skipNode,
    setMission,
    clearCurrentMission,
    addLog,
    clearLogs
  }
})
