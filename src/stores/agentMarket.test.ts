import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock API 层，避免真实网络调用（用 vi.hoisted 保证在 vi.mock 提升前初始化）
const { agentApiMock } = vi.hoisted(() => {
  return {
    agentApiMock: {
      list: vi.fn(),
      installed: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      install: vi.fn(),
      uninstall: vi.fn(),
      toggle: vi.fn(),
      evolve: vi.fn(),
      evolutionHistory: vi.fn(),
      rollback: vi.fn(),
      execute: vi.fn()
    }
  }
})

vi.mock('@/api/axios', () => ({
  agentApi: agentApiMock
}))

import { useAgentMarketStore } from '@/stores/agentMarket'
import type { Agent, InstalledAgent } from '@/types'

describe('AgentMarket Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const agent = (overrides: Partial<Agent> = {}): Agent => ({
    id: 'a-1',
    name: 'Reviewer',
    description: 'desc',
    icon: '🤖',
    version: '1.0.0',
    permissions: ['read:code'],
    status: 'idle',
    enabled: true,
    installed: true,
    ...overrides
  })

  describe('State & Computed', () => {
    it('should have initial state', () => {
      const store = useAgentMarketStore()
      expect(store.agents).toEqual([])
      expect(store.installedAgents).toEqual([])
      expect(store.selectedAgent).toBeNull()
    })

    it('should compute enabled/disabled agents from installed', () => {
      const store = useAgentMarketStore()
      store.installedAgents = [
        { ...agent({ id: '1' }), enabled: true } as InstalledAgent,
        { ...agent({ id: '2' }), enabled: false } as InstalledAgent,
        { ...agent({ id: '3' }), enabled: true } as InstalledAgent
      ]

      expect(store.enabledAgents).toHaveLength(2)
      expect(store.disabledAgents).toHaveLength(1)
    })

    it('should compute agent stats', () => {
      const store = useAgentMarketStore()
      store.agents = [agent(), agent(), agent()]
      store.installedAgents = [
        { ...agent({ id: '1' }), enabled: true } as InstalledAgent,
        { ...agent({ id: '2' }), enabled: false } as InstalledAgent
      ]

      expect(store.agentStats.total).toBe(3)
      expect(store.agentStats.installed).toBe(2)
      expect(store.agentStats.enabled).toBe(1)
      expect(store.agentStats.disabled).toBe(1)
    })

    it('should compute average rating across agents', () => {
      const store = useAgentMarketStore()
      store.agents = [agent({ rating: 4 }), agent({ rating: 5 }), agent({ rating: 3 })]

      expect(store.averageRating).toBe(4)
    })

    it('should return 0 average rating when no agents', () => {
      const store = useAgentMarketStore()
      expect(store.averageRating).toBe(0)
    })
  })

  describe('Actions', () => {
    it('fetchAgents should populate agents on success', async () => {
      const store = useAgentMarketStore()
      agentApiMock.list.mockResolvedValue({ success: true, data: [agent(), agent()] })

      await store.fetchAgents()

      expect(store.agents).toHaveLength(2)
    })

    it('fetchAgents should guard non-array data', async () => {
      const store = useAgentMarketStore()
      agentApiMock.list.mockResolvedValue({ success: true, data: null })

      await store.fetchAgents()

      expect(store.agents).toEqual([])
    })

    it('fetchInstalledAgents should populate installed agents', async () => {
      const store = useAgentMarketStore()
      agentApiMock.installed.mockResolvedValue({
        success: true,
        data: [{ ...agent({ id: 'i1' }), installedAt: '2024-01-01', enabled: true }]
      })

      await store.fetchInstalledAgents()

      expect(store.installedAgents).toHaveLength(1)
      expect(store.installedAgents[0].id).toBe('i1')
    })

    it('fetchAgent should set selectedAgent', async () => {
      const store = useAgentMarketStore()
      agentApiMock.get.mockResolvedValue({ success: true, data: agent({ id: 'a-9' }) })

      await store.fetchAgent('a-9')

      expect(store.selectedAgent?.id).toBe('a-9')
    })

    it('createAgent should create and refresh installed agents', async () => {
      const store = useAgentMarketStore()
      agentApiMock.create.mockResolvedValue({ success: true, data: agent({ id: 'new-1' }) })
      agentApiMock.installed.mockResolvedValue({ success: true, data: [agent({ id: 'new-1' })] })

      await store.createAgent({ name: 'New' })

      expect(agentApiMock.create).toHaveBeenCalled()
      expect(agentApiMock.installed).toHaveBeenCalled()
      expect(store.installedAgents).toHaveLength(1)
    })

    it('uninstallAgent should remove from installedAgents', async () => {
      const store = useAgentMarketStore()
      store.installedAgents = [
        { ...agent({ id: '1' }), installedAt: '', enabled: true } as InstalledAgent,
        { ...agent({ id: '2' }), installedAt: '', enabled: true } as InstalledAgent
      ]
      agentApiMock.uninstall.mockResolvedValue({ success: true })

      await store.uninstallAgent('1')

      expect(store.installedAgents.map(a => a.id)).toEqual(['2'])
    })

    it('toggleAgent should update enabled flag locally on success', async () => {
      const store = useAgentMarketStore()
      const a = { ...agent({ id: '1' }), enabled: true } as InstalledAgent
      store.installedAgents = [a]
      agentApiMock.toggle.mockResolvedValue({ success: true })

      await store.toggleAgent('1', false)

      expect(a.enabled).toBe(false)
    })

    it('setSelectedAgent / clearSelectedAgent should manage selection', () => {
      const store = useAgentMarketStore()
      store.setSelectedAgent(agent())
      expect(store.selectedAgent).not.toBeNull()

      store.clearSelectedAgent()
      expect(store.selectedAgent).toBeNull()
    })

    it('should propagate fetch errors', async () => {
      const store = useAgentMarketStore()
      agentApiMock.list.mockRejectedValue(new Error('network down'))

      await expect(store.fetchAgents()).rejects.toThrow('network down')
    })
  })
})
