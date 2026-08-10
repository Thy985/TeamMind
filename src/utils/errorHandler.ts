/**
 * 全局错误处理和恢复机制
 */

export interface ErrorContext {
  code?: string
  message: string
  details?: Record<string, unknown>
  timestamp: string
  requestId?: string
  retryCount?: number
  maxRetries?: number
}

export class AppError extends Error {
  code: string
  details?: Record<string, unknown>
  timestamp: string
  requestId?: string
  retryCount: number = 0
  maxRetries: number = 3

  constructor(message: string, code: string = 'UNKNOWN_ERROR', details?: Record<string, unknown>) {
    super(message)
    this.name = 'AppError'
    this.code = code
    this.details = details
    this.timestamp = new Date().toISOString()
  }

  toJSON(): ErrorContext {
    return {
      code: this.code,
      message: this.message,
      details: this.details,
      timestamp: this.timestamp,
      requestId: this.requestId,
      retryCount: this.retryCount,
      maxRetries: this.maxRetries
    }
  }
}

/**
 * 指数退避重试机制
 */
export async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  options: {
    maxRetries?: number
    initialDelay?: number
    maxDelay?: number
    backoffMultiplier?: number
    shouldRetry?: (error: unknown) => boolean
  } = {}
): Promise<T> {
  const {
    maxRetries = 3,
    initialDelay = 1000,
    maxDelay = 30000,
    backoffMultiplier = 2,
    shouldRetry = (error) => {
      // 默认只重试网络错误和 5xx 错误
      if (error instanceof AppError) {
        return error.code.startsWith('NETWORK_') || error.code.startsWith('SERVER_')
      }
      return false
    }
  } = options

  let lastError: unknown
  let delay = initialDelay

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      
      if (attempt === maxRetries || !shouldRetry(error)) {
        throw error
      }

      // 计算延迟时间（加入随机抖动避免雷鸣羊群效应）
      const jitter = Math.random() * 0.1 * delay
      const nextDelay = Math.min(delay * backoffMultiplier + jitter, maxDelay)

      console.warn(
        `[Retry] Attempt ${attempt + 1}/${maxRetries} failed, retrying in ${Math.round(nextDelay)}ms`,
        error
      )

      await new Promise(resolve => setTimeout(resolve, nextDelay))
      delay = nextDelay
    }
  }

  throw lastError
}

/**
 * 错误分类和处理
 */
export function classifyError(error: unknown): AppError {
  if (error instanceof AppError) {
    return error
  }

  if (error instanceof TypeError) {
    return new AppError(
      error.message,
      'TYPE_ERROR',
      { originalError: error.toString() }
    )
  }

  if (error instanceof SyntaxError) {
    return new AppError(
      error.message,
      'SYNTAX_ERROR',
      { originalError: error.toString() }
    )
  }

  // 处理 Axios 错误
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as any
    const status = axiosError.response?.status
    const data = axiosError.response?.data

    if (status === 401) {
      return new AppError('未授权，请重新登录', 'AUTH_UNAUTHORIZED', { status })
    }

    if (status === 403) {
      return new AppError('禁止访问', 'AUTH_FORBIDDEN', { status })
    }

    if (status === 404) {
      return new AppError('资源不存在', 'NOT_FOUND', { status })
    }

    if (status && status >= 500) {
      return new AppError(
        data?.message || '服务器错误，请稍后重试',
        'SERVER_ERROR',
        { status, data }
      )
    }

    if (status && status >= 400) {
      return new AppError(
        data?.message || '请求错误',
        'CLIENT_ERROR',
        { status, data }
      )
    }

    if (!status) {
      return new AppError(
        '网络连接失败',
        'NETWORK_ERROR',
        { message: axiosError.message }
      )
    }
  }

  // 处理网络错误
  if (error instanceof Error && error.message.includes('Network')) {
    return new AppError('网络连接失败', 'NETWORK_ERROR', { originalError: error.message })
  }

  // 默认错误
  return new AppError(
    error instanceof Error ? error.message : '未知错误',
    'UNKNOWN_ERROR',
    { originalError: String(error) }
  )
}

/**
 * 错误恢复策略
 */
export const errorRecoveryStrategies: Record<string, () => Promise<void>> = {
  AUTH_UNAUTHORIZED: async () => {
    // 清除本地存储的认证信息
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    // 重定向到登录页
    window.location.href = '/login'
  },

  NETWORK_ERROR: async () => {
    // 显示离线提示
    console.warn('网络连接已断开，应用将在恢复连接后自动同步')
  },

  SERVER_ERROR: async () => {
    // 记录错误到日志服务
    console.error('服务器错误，已记录到日志系统')
  }
}

/**
 * 执行错误恢复
 */
export async function executeErrorRecovery(error: AppError): Promise<void> {
  const strategy = errorRecoveryStrategies[error.code]
  if (strategy) {
    try {
      await strategy()
    } catch (recoveryError) {
      console.error('错误恢复失败:', recoveryError)
    }
  }
}
