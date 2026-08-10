/**
 * 实时协作系统 - 多用户同步和实时通知
 */

import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { wsManager } from '@/api/websocket'
import type { WSEvent } from '@/types'

/**
 * 协作用户
 */
export interface CollaborationUser {
  id: string
  name: string
  avatar?: string
  color: string
  cursor?: { x: number; y: number }
  lastActive: number
}

/**
 * 实时协作状态
 */
export interface CollaborationState {
  isConnected: boolean
  users: CollaborationUser[]
  currentUser: CollaborationUser | null
  onlineCount: number
}

/**
 * 通知类型
 */
export type NotificationType = 'success' | 'warning' | 'error' | 'info'

/**
 * 通知项
 */
export interface Notification {
  id: string
  type: NotificationType
  title: string
  message: string
  timestamp: number
  read: boolean
  link?: string
}

/**
 * 实时协作 Composable
 */
export function useCollaboration() {
  const state = ref<CollaborationState>({
    isConnected: false,
    users: [],
    currentUser: null,
    onlineCount: 0
  })

  const notifications = ref<Notification[]>([])
  const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

  /**
   * 连接协作服务
   */
  async function connect(missionId: string) {
    try {
      await wsManager.connect()
      
      state.value.isConnected = true

      // 加入任务协作
      wsManager.send({
        type: 'collaboration:join',
        data: { missionId }
      })

      // 监听协作事件
      setupEventListeners()

    } catch (error) {
      console.error('Failed to connect collaboration:', error)
      state.value.isConnected = false
    }
  }

  /**
   * 断开连接
   */
  function disconnect() {
    state.value.isConnected = false
    state.value.users = []
    wsManager.disconnect()
  }

  /**
   * 设置事件监听
   */
  function setupEventListeners() {
    // 用户加入
    wsManager.on('collaboration:user-joined', (event: WSEvent) => {
      const user = event.data as unknown as CollaborationUser
      state.value.users.push(user)
      state.value.onlineCount = state.value.users.length

      // 添加通知
      addNotification({
        type: 'info',
        title: '用户加入',
        message: `${user.name} 加入了协作`,
        link: `/missions/${user.id}`
      })
    })

    // 用户离开
    wsManager.on('collaboration:user-left', (event: WSEvent) => {
      const { userId } = event.data as { userId: string }
      state.value.users = state.value.users.filter(u => u.id !== userId)
      state.value.onlineCount = state.value.users.length
    })

    // 光标移动
    wsManager.on('collaboration:cursor-move', (event: WSEvent) => {
      const { userId, cursor } = event.data as { userId: string; cursor: { x: number; y: number } }
      const user = state.value.users.find(u => u.id === userId)
      if (user) {
        user.cursor = cursor
      }
    })

    // 任务更新
    wsManager.on('mission:updated', (event: WSEvent) => {
      addNotification({
        type: 'info',
        title: '任务更新',
        message: '任务已被更新',
        link: `/missions/${event.data?.id}`
      })
    })

    // 任务完成
    wsManager.on('mission:completed', (event: WSEvent) => {
      addNotification({
        type: 'success',
        title: '🎉 任务完成',
        message: '任务执行成功完成',
        link: `/missions/${event.data?.id}`
      })
    })

    // 任务失败
    wsManager.on('mission:failed', (event: WSEvent) => {
      addNotification({
        type: 'error',
        title: '❌ 任务失败',
        message: String((event.data as any)?.error || '任务执行失败'),
        link: `/missions/${(event.data as any)?.id}`
      })
    })
  }

  /**
   * 发送光标位置
   */
  function sendCursorPosition(x: number, y: number) {
    if (!state.value.isConnected) return

    wsManager.send({
      type: 'collaboration:cursor-move',
      data: { cursor: { x, y } }
    })
  }

  /**
   * 发送操作
   */
  function sendOperation(operation: string, data: any) {
    if (!state.value.isConnected) return

    wsManager.send({
      type: 'collaboration:operation',
      data: { operation, data }
    })
  }

  /**
   * 添加通知
   */
  function addNotification(notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) {
    notifications.value.unshift({
      ...notification,
      id: `notif-${Date.now()}-${Math.random()}`,
      timestamp: Date.now(),
      read: false
    })

    // 保持最近 50 条通知
    if (notifications.value.length > 50) {
      notifications.value = notifications.value.slice(0, 50)
    }

    // 保存到本地存储
    saveNotifications()
  }

  /**
   * 标记通知为已读
   */
  function markAsRead(notificationId: string) {
    const notification = notifications.value.find(n => n.id === notificationId)
    if (notification) {
      notification.read = true
      saveNotifications()
    }
  }

  /**
   * 全部标记为已读
   */
  function markAllAsRead() {
    notifications.value.forEach(n => n.read = true)
    saveNotifications()
  }

  /**
   * 删除通知
   */
  function deleteNotification(notificationId: string) {
    notifications.value = notifications.value.filter(n => n.id !== notificationId)
    saveNotifications()
  }

  /**
   * 清空所有通知
   */
  function clearAllNotifications() {
    notifications.value = []
    saveNotifications()
  }

  /**
   * 保存通知到本地存储
   */
  function saveNotifications() {
    try {
      localStorage.setItem('notifications', JSON.stringify(notifications.value))
    } catch (error) {
      console.error('Failed to save notifications:', error)
    }
  }

  /**
   * 从本地存储加载通知
   */
  function loadNotifications() {
    try {
      const stored = localStorage.getItem('notifications')
      if (stored) {
        notifications.value = JSON.parse(stored)
      }
    } catch (error) {
      console.error('Failed to load notifications:', error)
    }
  }

  // 初始化
  onMounted(() => {
    loadNotifications()
  })

  return {
    state,
    notifications,
    unreadCount,
    connect,
    disconnect,
    sendCursorPosition,
    sendOperation,
    addNotification,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearAllNotifications
  }
}

/**
 * 通知 Composable
 */
export function useNotifications() {
  const { notifications, unreadCount, markAsRead, markAllAsRead, deleteNotification, clearAllNotifications } = useCollaboration()

  return {
    notifications,
    unreadCount,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearAllNotifications
  }
}
