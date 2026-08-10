import type { WSEvent } from '@/types'
import { retryWithBackoff, AppError } from '@/utils/errorHandler'

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

class WebSocketManager {
  private ws: WebSocket | null = null
  private url: string
  private reconnect: boolean
  private reconnectInterval: number
  private maxReconnectAttempts: number
  private reconnectAttempts = 0
  private heartbeatInterval: number
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private isConnecting = false
  private eventHandlers: Map<string, Set<EventHandler>> = new Map()
  private connectionHandlers: Set<ConnectionHandler> = new Set()
  private disconnectionHandlers: Set<ConnectionHandler> = new Set()
  private errorHandlers: Set<ErrorHandler> = new Set()
  private messageQueue: WSEvent[] = []
  private isConnected = false

  constructor(options: WebSocketOptions = {}) {
    this.url = options.url || import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws'
    this.reconnect = options.reconnect ?? true
    this.reconnectInterval = options.reconnectInterval ?? 3000
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? 5
    this.heartbeatInterval = options.heartbeatInterval ?? 30000
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        resolve()
        return
      }

      if (this.isConnecting) {
        reject(new Error('Connection in progress'))
        return
      }

      this.isConnecting = true

      try {
        this.ws = new WebSocket(this.url)

        this.ws.onopen = () => {
          console.log('[WebSocket] Connected')
          this.isConnecting = false
          this.isConnected = true
          this.reconnectAttempts = 0
          this.startHeartbeat()
          this.flushMessageQueue()
          this.connectionHandlers.forEach(handler => handler())
          resolve()
        }

        this.ws.onclose = (event) => {
          console.log('[WebSocket] Disconnected', event.code, event.reason)
          this.isConnecting = false
          this.isConnected = false
          this.stopHeartbeat()
          this.disconnectionHandlers.forEach(handler => handler())

          if (this.reconnect && this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++
            console.log(`[WebSocket] Reconnecting... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
            setTimeout(() => this.connect().catch(console.error), this.reconnectInterval)
          }
        }

        this.ws.onerror = (error) => {
          console.error('[WebSocket] Error:', error)
          this.isConnecting = false
          this.errorHandlers.forEach(handler => handler(error))
          reject(new AppError('WebSocket 连接失败', 'WEBSOCKET_ERROR'))
        }

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data) as WSEvent
            this.handleEvent(data)
          } catch (e) {
            console.error('[WebSocket] Failed to parse message:', e)
          }
        }
      } catch (error) {
        this.isConnecting = false
        reject(error)
      }
    })
  }

  disconnect() {
    this.reconnect = false
    this.stopHeartbeat()
    this.clearAllHandlers()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.isConnected = false
  }

  private startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send({ type: 'ping' })
      }
    }, this.heartbeatInterval)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private handleEvent(event: WSEvent) {
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
    while (this.messageQueue.length > 0 && this.ws?.readyState === WebSocket.OPEN) {
      const message = this.messageQueue.shift()
      if (message) {
        this.ws.send(JSON.stringify(message))
      }
    }
  }

  send(data: WSEvent) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
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
      // 如果没有处理器了，删除这个事件类型
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
      connectionHandlersCount: this.connectionHandlers.size
    }
  }
}

// 创建单例实例
export const wsManager = new WebSocketManager()

export { WebSocketManager }
export type { WebSocketOptions }
