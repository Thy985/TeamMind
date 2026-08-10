import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useMissionStore } from '@/stores/mission'

describe('Mission Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('State', () => {
    it('should have initial state', () => {
      const store = useMissionStore()
      expect(store.missions).toEqual([])
      expect(store.currentMission).toBeNull()
      expect(store.nodes).toEqual([])
      expect(store.edges).toEqual([])
      expect(store.logs).toEqual([])
    })
  })

  describe('Computed', () => {
    it('should calculate stats correctly', () => {
      const store = useMissionStore()
      
      store.missions = [
        { id: '1', status: 'completed' } as any,
        { id: '2', status: 'completed' } as any,
        { id: '3', status: 'running' } as any,
        { id: '4', status: 'pending' } as any
      ]

      expect(store.stats.total).toBe(4)
      expect(store.stats.completed).toBe(2)
      expect(store.stats.running).toBe(1)
      expect(store.stats.pending).toBe(1)
      expect(store.stats.successRate).toBe(50)
    })

    it('should group nodes by status', () => {
      const store = useMissionStore()
      
      store.nodes = [
        { id: '1', data: { status: 'running' } } as any,
        { id: '2', data: { status: 'running' } } as any,
        { id: '3', data: { status: 'success' } } as any
      ]

      expect(store.nodesByStatus.running).toHaveLength(2)
      expect(store.nodesByStatus.success).toHaveLength(1)
      expect(store.nodesByStatus.idle).toHaveLength(0)
    })

    it('should group logs by type', () => {
      const store = useMissionStore()
      
      store.logs = [
        { id: '1', type: 'task' } as any,
        { id: '2', type: 'task' } as any,
        { id: '3', type: 'error' } as any
      ]

      expect(store.logsByType.task).toHaveLength(2)
      expect(store.logsByType.error).toHaveLength(1)
    })
  })

  describe('Actions', () => {
    it('should set mission correctly', () => {
      const store = useMissionStore()
      const mission = {
        id: '1',
        title: 'Test Mission',
        nodes: [{ id: 'n1' }],
        edges: [{ id: 'e1' }],
        logs: [{ id: 'l1' }]
      } as any

      store.setMission(mission)

      expect(store.currentMission).toEqual(mission)
      expect(store.nodes).toEqual([{ id: 'n1' }])
      expect(store.edges).toEqual([{ id: 'e1' }])
      expect(store.logs).toEqual([{ id: 'l1' }])
    })

    it('should clear current mission', () => {
      const store = useMissionStore()
      store.currentMission = { id: '1' } as any
      store.nodes = [{ id: 'n1' }] as any
      store.edges = [{ id: 'e1' }] as any
      store.logs = [{ id: 'l1' }] as any

      store.clearCurrentMission()

      expect(store.currentMission).toBeNull()
      expect(store.nodes).toEqual([])
      expect(store.edges).toEqual([])
      expect(store.logs).toEqual([])
    })

    it('should add log', () => {
      const store = useMissionStore()
      const log = { id: '1', type: 'task', message: 'Test' } as any

      store.addLog(log)

      expect(store.logs).toHaveLength(1)
      expect(store.logs[0]).toEqual(log)
    })

    it('should clear logs', () => {
      const store = useMissionStore()
      store.logs = [{ id: '1' }, { id: '2' }] as any

      store.clearLogs()

      expect(store.logs).toEqual([])
    })
  })
})
