import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { WSEvent } from '@/types'
import { AppError } from '@/utils/errorHandler'

type EventHandler = (event: WSEvent) => void
type ConnectionHandler = () => void
type ErrorHandler = (error: Event) => void

interface WebSocketOptions {
  url?: string
  reconnect?: boolean
  reconnectInterval?: number
  maxReconnectAttempts?: number
  heartbeatInterval?: number
}

/**
 * STOMP over SockJS WebSocket Manager
 *
 * 与后端 WebSocketConfig (/ws + SockJS) 对齐：
 * - 连接 /ws（SockJS 端点）
 * - 订阅 /topic/events 获取全局事件
 * - 订阅 /topic/missions/{id} 获取任务专属事件
 * - 事件结构对齐后端 WSEventPublisher: { type, missionId, timestamp, payload }
 */
class WebSocketManager {
  private client: Client | null = null
  private url: string
  private reconnect: boolean
  private reconnectInterval: number
  private maxReconnectAttempts: number
  private heartbeatInterval: number
  private reconnectAttempts = 0
  private isConnecting = false
  private eventHandlers: Map<string, Set<EventHandler>> = new Map()
  private connectionHandlers: Set<ConnectionHandler> = new Set()
  private disconnectionHandlers: Set<ConnectionHandler> = new Set()
  private errorHandlers: Set<ErrorHandler> = new Set()
  private messageQueue: WSEvent[] = []
  private isConnected = false
  private subscriptions: StompSubscription[] = []
  private missionId: string | null = null

  constructor(options: WebSocketOptions = {}) {
    // SockJS 需要 HTTP(S) 协议端点，而非 ws://
    this.url = options.url || import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws'
    this.reconnect = options.reconnect ?? true
    this.reconnectInterval = options.reconnectInterval ?? 3000
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? 5
    this.heartbeatInterval = options.heartbeatInterval ?? 30000

    // 规范化 URL：将 ws:// 或 wss:// 转为 http:// 或 https://（SockJS 需要 HTTP 握手）
    this.url = this.url.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:')
  }

  connect(missionId?: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.isConnected) {
        resolve()
        return
      }

      if (this.isConnecting) {
        reject(new AppError('Connection in progress', 'WEBSOCKET_ERROR'))
        return
      }

      this.isConnecting = true
      if (missionId) {
        this.missionId = missionId
      }

      try {
        // 创建 SockJS 连接
        const socket = new SockJS(this.url)

        // 创建 STOMP Client
        this.client = new Client({
          webSocketFactory: () => socket,
          reconnectDelay: this.reconnect ? this.reconnectInterval : 0,
          heartbeatIncoming: this.heartbeatInterval,
          heartbeatOutgoing: this.heartbeatInterval,
          connectHeaders: {
            // 携带 JWT，供后端 WebSocket 鉴权
            Authorization: localStorage.getItem('token') ? `Bearer ${localStorage.getItem('token')}` : ''
          },
          debug: (str) => {
            if (import.meta.env.DEV) {
              console.debug('[STOMP]', str)
            }
          }
        })

        this.client.onConnect = (frame) => {
          console.log('[WebSocket] STOMP connected')
          this.isConnecting = false
          this.isConnected = true
          this.reconnectAttempts = 0
          this.setupSubscriptions()
          this.flushMessageQueue()
          this.connectionHandlers.forEach(handler => handler())
          resolve()
        }

        this.client.onWebSocketClose = (event) => {
          console.log('[WebSocket] Disconnected', event?.code, event?.reason)
          this.isConnecting = false
          this.isConnected = false
          this.disconnectionHandlers.forEach(handler => handler())
        }

        this.client.onStompError = (frame) => {
          console.error('[WebSocket] STOMP error:', frame.headers['message'])
          this.errorHandlers.forEach(handler => handler(new Event('stomp-error')))
          if (this.isConnecting) {
            this.isConnecting = false
            reject(new AppError('STOMP connection error: ' + frame.headers['message'], 'WEBSOCKET_ERROR'))
          }
        }

        this.client.activate()
      } catch (error) {
        this.isConnecting = false
        reject(error)
      }
    })
  }

  /**
   * 设置 STOMP 订阅
   * - /topic/events: 全局事件广播
   * - /topic/missions/{missionId}: 任务专属事件
   */
  private setupSubscriptions() {
    if (!this.client || !this.isConnected) return

    // 清理旧订阅
    this.subscriptions.forEach(sub => {
      try { sub.unsubscribe() } catch (_) { /* ignore */ }
    })
    this.subscriptions = []

    // 订阅全局事件
    const globalSub = this.client.subscribe('/topic/events', (message: IMessage) => {
      this.handleStompMessage(message)
    })
    this.subscriptions.push(globalSub)

    // 订阅任务专属事件
    if (this.missionId) {
      const missionSub = this.client.subscribe(`/topic/missions/${this.missionId}`, (message: IMessage) => {
        this.handleStompMessage(message)
      })
      this.subscriptions.push(missionSub)
    }
  }

  /**
   * 解析 STOMP 消息为 WSEvent
   */
  private handleStompMessage(message: IMessage) {
    try {
      const data = JSON.parse(message.body) as WSEvent
      this.handleEvent(data)
    } catch (e) {
      console.error('[WebSocket] Failed to parse STOMP message:', e)
    }
  }

  disconnect() {
    this.reconnect = false
    this.clearAllHandlers()
    if (this.client) {
      try {
        this.client.deactivate()
      } catch (_) { /* ignore */ }
      this.client = null
    }
    this.isConnected = false
    this.missionId = null
  }

  private handleEvent(event: WSEvent) {
    // 后端 WSEvent 结构: { type, missionId, timestamp, payload }
    const handlers = this.eventHandlers.get(event.type)
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(event)
        } catch (error) {
          console.error(`[WebSocket] Error in handler for ${event.type}:`, error)
        }
      })
    }

    // 也触发通用的 '*' 事件处理器
    const allHandlers = this.eventHandlers.get('*')
    if (allHandlers) {
      allHandlers.forEach(handler => {
        try {
          handler(event)
        } catch (error) {
          console.error('[WebSocket] Error in universal handler:', error)
        }
      })
    }
  }

  private flushMessageQueue() {
    while (this.messageQueue.length > 0 && this.isConnected && this.client) {
      const message = this.messageQueue.shift()
      if (message) {
        this.send(message)
      }
    }
  }

  send(data: WSEvent) {
    if (this.isConnected && this.client) {
      // 对齐后端 WebSocketController 的 @MessageMapping("/app/*")
      this.client.publish({
        destination: '/app/' + (data.type.startsWith('/') ? data.type.slice(1) : data.type),
        body: JSON.stringify(data)
      })
    } else {
      // 连接未建立时，将消息加入队列
      this.messageQueue.push(data)
      console.warn('[WebSocket] Connection not ready, message queued')
    }
  }

  on(eventType: WSEvent['type'] | '*', handler: EventHandler) {
    if (!this.eventHandlers.has(eventType)) {
      this.eventHandlers.set(eventType, new Set())
    }
    this.eventHandlers.get(eventType)!.add(handler)
  }

  off(eventType: WSEvent['type'] | '*', handler: EventHandler) {
    const handlers = this.eventHandlers.get(eventType)
    if (handlers) {
      handlers.delete(handler)
      if (handlers.size === 0) {
        this.eventHandlers.delete(eventType)
      }
    }
  }

  onConnect(handler: ConnectionHandler) {
    this.connectionHandlers.add(handler)
  }

  offConnect(handler: ConnectionHandler) {
    this.connectionHandlers.delete(handler)
  }

  onDisconnect(handler: ConnectionHandler) {
    this.disconnectionHandlers.add(handler)
  }

  offDisconnect(handler: ConnectionHandler) {
    this.disconnectionHandlers.delete(handler)
  }

  onError(handler: ErrorHandler) {
    this.errorHandlers.add(handler)
  }

  offError(handler: ErrorHandler) {
    this.errorHandlers.delete(handler)
  }

  /**
   * 清理所有事件处理器
   */
  private clearAllHandlers() {
    this.eventHandlers.clear()
    this.connectionHandlers.clear()
    this.disconnectionHandlers.clear()
    this.errorHandlers.clear()
  }

  /**
   * 获取连接状态
   */
  getStatus(): 'connected' | 'connecting' | 'disconnected' {
    if (this.isConnecting) return 'connecting'
    if (this.isConnected) return 'connected'
    return 'disconnected'
  }

  /**
   * 获取统计信息
   */
  getStats() {
    return {
      isConnected: this.isConnected,
      isConnecting: this.isConnecting,
      reconnectAttempts: this.reconnectAttempts,
      messageQueueLength: this.messageQueue.length,
      eventHandlersCount: this.eventHandlers.size,
      connectionHandlersCount: this.connectionHandlers.size,
      subscriptionsCount: this.subscriptions.length
    }
  }
}

// 创建单例实例
export const wsManager = new WebSocketManager()

export { WebSocketManager }
export type { WebSocketOptions }
