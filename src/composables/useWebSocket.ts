import { onMounted, onBeforeUnmount, ref } from 'vue'
import { wsManager } from '@/api/websocket'
import type { WSEvent, WSEventType } from '@/types'

/**
 * 安全的 WebSocket 事件监听 Composable
 * 自动处理事件监听器的注册和清理，防止内存泄漏
 */
export function useWebSocketListener(
  eventType: string | string[],
  handler: (event: WSEvent) => void,
  options?: { immediate?: boolean; missionId?: string }
) {
  const isListening = ref(false)

  const eventTypes = Array.isArray(eventType) ? eventType : [eventType]

  onMounted(() => {
    eventTypes.forEach(type => {
      wsManager.on(type as WSEventType | '*', handler)
    })
    isListening.value = true

    if (options?.immediate) {
      // 如果需要立即连接，携带 missionId 以订阅任务专属频道
      wsManager.connect(options.missionId).catch(console.error)
    }
  })

  onBeforeUnmount(() => {
    eventTypes.forEach(type => {
      wsManager.off(type as WSEventType | '*', handler)
    })
    isListening.value = false
  })

  return {
    isListening
  }
}

/**
 * WebSocket 连接状态监听
 */
export function useWebSocketStatus() {
  const status = ref<'connected' | 'connecting' | 'disconnected'>('disconnected')

  const updateStatus = () => {
    status.value = wsManager.getStatus()
  }

  const handleConnect = () => {
    status.value = 'connected'
  }

  const handleDisconnect = () => {
    status.value = 'disconnected'
  }

  onMounted(() => {
    updateStatus()
    wsManager.onConnect(handleConnect)
    wsManager.onDisconnect(handleDisconnect)
  })

  onBeforeUnmount(() => {
    wsManager.offConnect(handleConnect)
    wsManager.offDisconnect(handleDisconnect)
  })

  return {
    status,
    isConnected: () => status.value === 'connected',
    isConnecting: () => status.value === 'connecting'
  }
}

/**
 * WebSocket 错误处理
 */
export function useWebSocketError() {
  const error = ref<Error | null>(null)

  const handleError = (event: Event) => {
    error.value = new Error('WebSocket 连接错误')
    console.error('[WebSocket Error]', event)
  }

  const clearError = () => {
    error.value = null
  }

  onMounted(() => {
    wsManager.onError(handleError)
  })

  onBeforeUnmount(() => {
    wsManager.offError(handleError)
  })

  return {
    error,
    clearError,
    hasError: () => error.value !== null
  }
}

/**
 * 发送 WebSocket 消息
 */
export function useSendWebSocketMessage() {
  const send = (data: WSEvent) => {
    wsManager.send(data)
  }

  return { send }
}
