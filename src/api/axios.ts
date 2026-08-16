import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import type { ApiResponse, PaginatedResponse } from '@/types'
import { retryWithBackoff, classifyError, AppError, executeErrorRecovery } from '@/utils/errorHandler'
import { generateUUID } from '@/utils/common'

// 创建 Axios 实例
const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 - 添加请求 ID 和认证
apiClient.interceptors.request.use(
  (config) => {
    // 生成唯一的请求 ID
    const requestId = generateUUID()
    config.headers['X-Request-ID'] = requestId
    ;(config as any).metadata = { requestId, startTime: Date.now() }

    // 添加认证 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 记录请求（生产环境建议关闭；此处仅保留方法+URL，不打印敏感 payload）
    console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`, { requestId })

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 统一错误处理
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    const duration = Date.now() - ((response.config as any).metadata?.startTime || 0)
    console.log(`[API] Response ${response.status}`, {
      requestId: (response.config as any).headers?.['X-Request-ID'],
      duration: `${duration}ms`
    })
    return response
  },
  async (error) => {
    const requestId = (error.config as any)?.headers?.['X-Request-ID']
    const appError = classifyError(error)
    appError.requestId = requestId

    console.error('[API] Error', appError.toJSON())

    // 执行错误恢复策略
    await executeErrorRecovery(appError)

    return Promise.reject(appError)
  }
)

// 封装请求方法 - 集成重试机制
export const api = {
  async get<T = unknown>(
    url: string,
    config?: AxiosRequestConfig & { retryable?: boolean }
  ): Promise<ApiResponse<T>> {
    const { retryable = true, ...axiosConfig } = config || {}

    const fn = () => apiClient.get<ApiResponse<T>>(url, axiosConfig)

    const response = retryable
      ? await retryWithBackoff(fn, {
          maxRetries: 3,
          shouldRetry: (error) => {
            if (error instanceof AppError) {
              // 只重试网络错误和 5xx 错误
              return error.code.startsWith('NETWORK_') || error.code.startsWith('SERVER_')
            }
            return false
          }
        })
      : await fn()

    return response.data
  },

  async post<T = unknown>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig & { retryable?: boolean }
  ): Promise<ApiResponse<T>> {
    const { retryable = false, ...axiosConfig } = config || {}

    const fn = () => apiClient.post<ApiResponse<T>>(url, data, axiosConfig)

    const response = retryable
      ? await retryWithBackoff(fn, { maxRetries: 2 })
      : await fn()

    return response.data
  },

  async put<T = unknown>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig & { retryable?: boolean }
  ): Promise<ApiResponse<T>> {
    const { retryable = false, ...axiosConfig } = config || {}

    const fn = () => apiClient.put<ApiResponse<T>>(url, data, axiosConfig)

    const response = retryable
      ? await retryWithBackoff(fn, { maxRetries: 2 })
      : await fn()

    return response.data
  },

  async delete<T = unknown>(
    url: string,
    config?: AxiosRequestConfig & { retryable?: boolean }
  ): Promise<ApiResponse<T>> {
    const { retryable = false, ...axiosConfig } = config || {}

    const fn = () => apiClient.delete<ApiResponse<T>>(url, axiosConfig)

    const response = retryable
      ? await retryWithBackoff(fn, { maxRetries: 2 })
      : await fn()

    return response.data
  },

  async getPaginated<T = unknown>(
    url: string,
    page = 1,
    pageSize = 20,
    config?: AxiosRequestConfig & { retryable?: boolean }
  ): Promise<ApiResponse<PaginatedResponse<T>>> {
    const { retryable = true, ...axiosConfig } = config || {}

    const fn = () =>
      apiClient.get<ApiResponse<PaginatedResponse<T>>>(url, {
        ...axiosConfig,
        params: { page, pageSize, ...axiosConfig?.params }
      })

    const response = retryable
      ? await retryWithBackoff(fn, { maxRetries: 3 })
      : await fn()

    return response.data
  }
}

// ==================== Mission API ====================
export const missionApi = {
  create: (data: { title: string; description?: string; agentIds?: string[]; templateId?: string }) =>
    api.post<{ id: string }>('/missions', data),

  get: (id: string) => api.get(`/missions/${id}`),

  list: (page?: number, pageSize?: number) => api.getPaginated('/missions', page, pageSize),

  update: (id: string, data: Record<string, unknown>) => api.put(`/missions/${id}`, data),

  delete: (id: string) => api.delete(`/missions/${id}`),

  clone: (id: string) => api.post<{ id: string }>(`/missions/${id}/clone`),

  start: (id: string) => api.post(`/missions/${id}/start`, {}, { retryable: true }),

  pause: (id: string) => api.post(`/missions/${id}/pause`),

  resume: (id: string) => api.post(`/missions/${id}/resume`),

  cancel: (id: string) => api.post(`/missions/${id}/cancel`),

  runtime: (id: string) => api.get(`/missions/${id}/runtime`),

  stats: () => api.get('/missions/stats'),

  retry: (id: string, nodeId: string) => api.post(`/missions/${id}/nodes/${nodeId}/retry`),

  skip: (id: string, nodeId: string) => api.post(`/missions/${id}/nodes/${nodeId}/skip`)
}

// ==================== Agent API ====================
export const agentApi = {
  list: () => api.get('/agents'),

  installed: () => api.get('/agents/installed'),

  get: (id: string) => api.get(`/agents/${id}`),

  create: (data: { name: string; description?: string; prompt?: string; permissions?: string[] }) =>
    api.post('/agents', data),

  install: (id: string) => api.post(`/agents/${id}/install`, {}, { retryable: true }),

  uninstall: (id: string) => api.delete(`/agents/${id}`),

  toggle: (id: string, enabled: boolean) => api.put(`/agents/${id}/enabled`, { enabled }),

  evolve: (id: string, data: { type: string; reason?: string; context?: Record<string, unknown>; automatic?: boolean }) =>
    api.post(`/agents/${id}/evolve`, data),

  evolutionHistory: (id: string) => api.get(`/agents/${id}/evolution/history`),

  metrics: (id: string) => api.get(`/agents/${id}/metrics`),

  rate: (id: string, rating: number) => api.post(`/agents/${id}/rate`, { rating }),

  rollback: (agentId: string, recordId: number) =>
    api.post(`/agents/${agentId}/evolution/${recordId}/rollback`),

  execute: (id: string, data: { prompt: string; missionId?: string; input?: Record<string, unknown> }) =>
    api.post(`/agents/${id}/execute`, data, { retryable: true })
}

// ==================== Template API ====================
export const templateApi = {
  list: () => api.get('/templates'),

  get: (id: string) => api.get(`/templates/${id}`),

  create: (data: { name: string; description?: string; icon?: string; config?: Record<string, unknown> }) =>
    api.post('/templates', data),

  update: (id: string, data: Record<string, unknown>) => api.put(`/templates/${id}`, data),

  delete: (id: string) => api.delete(`/templates/${id}`),

  clone: (id: string) => api.post<{ id: string }>(`/templates/${id}/clone`)
}

// ==================== Mission Control API ====================
export const missionControlApi = {
  overview: (projectId: string) => api.get(`/mission-control/project/${projectId}/overview`),

  runningTasks: (projectId: string) => api.get(`/mission-control/project/${projectId}/running`),

  history: (projectId: string, limit = 20) =>
    api.get(`/mission-control/project/${projectId}/history?limit=${limit}`),

  profile: (projectId: string) => api.get(`/mission-control/project/${projectId}/profile`),

  recommendation: (projectId: string) => api.get(`/mission-control/project/${projectId}/recommendation`),

  driftAlerts: (projectId: string) => api.get(`/mission-control/project/${projectId}/drift`),

  recalculate: (projectId: string) => api.post(`/mission-control/project/${projectId}/recalculate`),

  controlMode: (projectId: string) => api.get(`/mission-control/project/${projectId}/control-mode`),

  setControlMode: (projectId: string, mode: string) =>
    api.put(`/mission-control/project/${projectId}/control-mode`, { controlMode: mode })
}

// ==================== TaskDetail API ====================
export const taskDetailApi = {
  // Full state snapshot
  getTask: (taskId: string) => api.get(`/tasks/${taskId}`),

  // Event chain (for initial load)
  getEvents: (taskId: string, after?: number) => {
    const params = after && after > 0 ? `?after=${after}` : ''
    return api.get(`/tasks/${taskId}/events${params}`)
  },

  // Execution Ledger / Activity summary
  getActivity: (taskId: string) => api.get(`/tasks/${taskId}/activity`),

  // Control actions
  pause: (taskId: string) => api.post(`/tasks/${taskId}/pause`),
  resume: (taskId: string) => api.post(`/tasks/${taskId}/resume`),
  cancel: (taskId: string) => api.post(`/tasks/${taskId}/cancel`),
  approve: (taskId: string, body: Record<string, unknown>) => api.post(`/tasks/${taskId}/approve`, body),
  retry: (taskId: string) => api.post(`/tasks/${taskId}/retry`)
}

// ==================== Auth API ====================
export const authApi = {
  login: (data: { username: string; password: string }) => api.post('/auth/login', data, { retryable: false }),

  me: () => api.get('/auth/me', { retryable: false })
}

// ==================== Knowledge API (Sprint 4) ====================
export const knowledgeApi = {
  save: (data: { type: string; title: string; description?: string; taskId?: string; projectId?: string; source?: string }) =>
    api.post('/knowledge', data),

  getByTask: (taskId: string) => api.get(`/knowledge/task/${taskId}`),

  getByProject: (projectId: string) => api.get(`/knowledge/project/${projectId}`),

  dismiss: (id: string) => api.post(`/knowledge/${id}/dismiss`),

  delete: (id: string) => api.delete(`/knowledge/${id}`)
}

export { AppError, classifyError, retryWithBackoff, executeErrorRecovery } from '@/utils/errorHandler'
