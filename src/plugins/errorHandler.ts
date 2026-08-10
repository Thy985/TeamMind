import { createApp, type App } from 'vue'
import { useMessage, useDialog, useNotification } from 'naive-ui'
import type { AppError } from '@/utils/errorHandler'

/**
 * 全局错误处理插件
 */
export function setupGlobalErrorHandler(app: App) {
  const message = useMessage()
  const dialog = useDialog()
  const notification = useNotification()

  // 未捕获的错误处理
  app.config.errorHandler = (err, instance, info) => {
    const error = err instanceof Error ? err : new Error(String(err))
    
    console.error('[Global Error]', {
      error: error.message,
      stack: error.stack,
      component: instance?.$options.name || 'Unknown',
      info
    })

    // 显示用户友好的错误提示
    notification.error({
      title: '发生错误',
      description: error.message || '未知错误，请稍后重试',
      duration: 5000
    })
  }

  // 未捕获的 Promise 拒绝处理
  window.addEventListener('unhandledrejection', (event) => {
    const error = event.reason instanceof Error ? event.reason : new Error(String(event.reason))

    console.error('[Unhandled Promise Rejection]', {
      error: error.message,
      stack: error.stack
    })

    // 阻止浏览器默认处理
    event.preventDefault()

    // 显示错误提示
    notification.error({
      title: '操作失败',
      description: error.message || '未知错误，请稍后重试',
      duration: 5000
    })
  })

  // 提供全局错误处理方法
  app.config.globalProperties.$handleError = (error: unknown, options?: { title?: string; duration?: number }) => {
    const appError = error instanceof Error ? error : new Error(String(error))
    const title = options?.title || '操作失败'
    const duration = options?.duration ?? 5000

    console.error('[Handled Error]', appError)

    notification.error({
      title,
      description: appError.message,
      duration
    })
  }

  // 提供全局成功提示方法
  app.config.globalProperties.$showSuccess = (message: string, options?: { duration?: number }) => {
    notification.success({
      title: '成功',
      description: message,
      duration: options?.duration ?? 3000
    })
  }

  // 提供全局警告提示方法
  app.config.globalProperties.$showWarning = (message: string, options?: { duration?: number }) => {
    notification.warning({
      title: '警告',
      description: message,
      duration: options?.duration ?? 4000
    })
  }

  // 提供全局确认对话框
  app.config.globalProperties.$confirm = (options: {
    title: string
    content: string
    positiveText?: string
    negativeText?: string
  }): Promise<boolean> => {
    return new Promise((resolve) => {
      dialog.warning({
        title: options.title,
        content: options.content,
        positiveText: options.positiveText || '确认',
        negativeText: options.negativeText || '取消',
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false)
      })
    })
  }
}

/**
 * 类型扩展 - 为 Vue 实例添加全局方法
 */
declare module '@vue/runtime-core' {
  interface ComponentCustomProperties {
    $handleError: (error: unknown, options?: { title?: string; duration?: number }) => void
    $showSuccess: (message: string, options?: { duration?: number }) => void
    $showWarning: (message: string, options?: { duration?: number }) => void
    $confirm: (options: {
      title: string
      content: string
      positiveText?: string
      negativeText?: string
    }) => Promise<boolean>
  }
}
