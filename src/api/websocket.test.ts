import { describe, it, expect, beforeEach, vi } from 'vitest'

// ===== Mock STOMP Client 与 SockJS =====

class MockClient {
  constructor(public _options: Record<string, unknown>) {}
  onConnect: ((_frame: unknown) => void) | null = null
  onWebSocketClose: ((_event: unknown) => void) | null = null
  onStompError: ((_frame: { headers: Record<string, string> }) => void) | null = null
  activate = vi.fn()
  deactivate = vi.fn()
  publish = vi.fn()
  subscribe = vi.fn((_destination: string, _cb: unknown) => {
    return { unsubscribe: vi.fn() }
  })
}

let mockClient: MockClient | null = null

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(options: Record<string, unknown>) {
      mockClient = new MockClient(options)
      // 同步透传生命周期回调，方便测试手动触发
      return mockClient
    }
  }
}))

vi.mock('sockjs-client', () => ({
  __esModule: true,
  default: vi.fn((url: string) => ({ url }))
}))

import { WebSocketManager } from '@/api/websocket'

describe('WebSocketManager', () => {
  let manager: WebSocketManager

  beforeEach(() => {
    mockClient = null
    manager = new WebSocketManager({
      url: 'ws://localhost:8080/ws',
      reconnect: true,
      reconnectInterval: 3000,
      maxReconnectAttempts: 5,
      heartbeatInterval: 30000
    })
  })

  describe('URL 规范化', () => {
    it('should normalize ws:// to http:// for SockJS', () => {
      expect((manager as any).url).toBe('http://localhost:8080/ws')
    })

    it('should normalize wss:// to https://', () => {
      const m2 = new WebSocketManager({ url: 'wss://example.com/ws' })
      expect((m2 as any).url).toBe('https://example.com/ws')
    })
  })

  describe('getStatus', () => {
    it('should report disconnected initially', () => {
      expect(manager.getStatus()).toBe('disconnected')
    })
  })

  describe('connect', () => {
    it('should resolve on successful connection and set subscriptions', async () => {
      const promise = manager.connect('mission-1')
      // 触发 STOMP onConnect
      expect(mockClient).not.toBeNull()
      mockClient!.onConnect!({})

      await promise

      expect(manager.getStatus()).toBe('connected')
      // 全局订阅 + 任务订阅
      expect(mockClient!.subscribe).toHaveBeenCalledWith('/topic/events', expect.any(Function))
      expect(mockClient!.subscribe).toHaveBeenCalledWith('/topic/missions/mission-1', expect.any(Function))
    })

    it('should reject on STOMP error while connecting', async () => {
      const promise = manager.connect()
      mockClient!.onStompError!({ headers: { message: 'Broker unavailable' } })

      await expect(promise).rejects.toThrow()
      expect(manager.getStatus()).toBe('disconnected')
    })

    it('should resolve immediately if already connected', async () => {
      const p1 = manager.connect()
      mockClient!.onConnect!({})
      await p1

      await expect(manager.connect()).resolves.toBeUndefined()
    })
  })

  describe('send', () => {
    it('should publish to /app/ destination when connected', async () => {
      const p = manager.connect()
      mockClient!.onConnect!({})
      await p

      manager.send({ type: 'ping' })

      expect(mockClient!.publish).toHaveBeenCalledWith(
        expect.objectContaining({ destination: '/app/ping' })
      )
    })

    it('should queue message when not connected', () => {
      manager.send({ type: 'log' })
      expect((manager as any).messageQueue).toHaveLength(1)
    })
  })

  describe('on/off 事件订阅', () => {
    it('should register handlers and deliver matching events', async () => {
      const handler = vi.fn()
      manager.on('mission_started', handler)

      const p = manager.connect()
      mockClient!.onConnect!({})
      await p

      // 通过全局订阅回调模拟收到事件
      const globalCb = mockClient!.subscribe.mock.calls[0][1] as (_msg: { body: string }) => void
      globalCb({ body: JSON.stringify({ type: 'mission_started', missionId: 'm-1' }) })

      expect(handler).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'mission_started', missionId: 'm-1' })
      )

      // 未订阅的事件不应触发
      handler.mockClear()
      globalCb({ body: JSON.stringify({ type: 'log' }) })
      expect(handler).not.toHaveBeenCalled()
    })

    it('should support wildcard * handler', async () => {
      const handler = vi.fn()
      manager.on('*', handler)

      const p = manager.connect()
      mockClient!.onConnect!({})
      await p

      const globalCb = mockClient!.subscribe.mock.calls[0][1] as (_msg: { body: string }) => void
      globalCb({ body: JSON.stringify({ type: 'log' }) })

      expect(handler).toHaveBeenCalled()
    })

    it('off should remove handler', async () => {
      const handler = vi.fn()
      manager.on('log', handler)
      manager.off('log', handler)

      const p = manager.connect()
      mockClient!.onConnect!({})
      await p

      const globalCb = mockClient!.subscribe.mock.calls[0][1] as (_msg: { body: string }) => void
      globalCb({ body: JSON.stringify({ type: 'log' }) })

      expect(handler).not.toHaveBeenCalled()
    })
  })

  describe('disconnect', () => {
    it('should deactivate client and reset connection state', async () => {
      const p = manager.connect()
      mockClient!.onConnect!({})
      await p

      manager.disconnect()

      expect(mockClient!.deactivate).toHaveBeenCalled()
      expect(manager.getStatus()).toBe('disconnected')
    })
  })

  describe('getStats', () => {
    it('should return stats snapshot', async () => {
      const p = manager.connect()
      mockClient!.onConnect!({})
      await p

      const stats = manager.getStats()
      expect(stats.isConnected).toBe(true)
      expect(stats.subscriptionsCount).toBe(1) // 仅全局订阅（无 missionId）
    })
  })
})
