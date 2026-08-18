/**
 * Host Adapter — 统一前端与 Runtime 的通信接口
 *
 * 支持两种模式：
 * - Web 模式：通过 HTTP REST + WebSocket 与 Spring Boot 通信
 * - Tauri 模式：通过 tauri::invoke 与 Rust Runtime 通信
 *
 * Vue 组件只依赖此接口，不感知底层是 Web 还是桌面。
 */

// ─── 类型定义 ────────────────────────────────────────────────────

export interface HostAdapter {
  /** 当前模式 */
  readonly mode: 'web' | 'tauri'

  /** 发送 REST 请求 */
  request<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<ApiResponse<T>>

  /** 订阅实时事件 */
  subscribe<T>(event: string, handler: (data: T) => void): () => void

  /** 连接状态 */
  isConnected(): boolean

  /** 获取后端地址（调试用） */
  getBackendUrl(): string
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: string
  message?: string
}

// ─── 工厂函数 ────────────────────────────────────────────────────

let _adapter: HostAdapter | null = null

/**
 * 初始化 Host Adapter
 * - 在 Tauri 环境中：自动检测并使用 Tauri adapter
 * - 在浏览器中：使用 Web adapter（HTTP + WebSocket）
 */
export function initHostAdapter(): HostAdapter {
  if (_adapter) return _adapter

  // 检测是否在 Tauri 环境中
  if (isTauriEnv()) {
    _adapter = new TauriHostAdapter()
    console.log('[HostAdapter] Initialized: Tauri mode')
  } else {
    _adapter = new WebHostAdapter()
    console.log('[HostAdapter] Initialized: Web mode')
  }

  return _adapter
}

/**
 * 强制指定模式（测试/调试用）
 */
export function forceHostAdapter(mode: 'web' | 'tauri'): void {
  if (mode === 'web') {
    _adapter = new WebHostAdapter()
  } else {
    _adapter = new TauriHostAdapter()
  }
  console.log(`[HostAdapter] Forced mode: ${mode}`)
}

/**
 * 获取当前 adapter（必须在 initHostAdapter() 之后调用）
 */
export function getHostAdapter(): HostAdapter {
  if (!_adapter) {
    _adapter = isTauriEnv() ? new TauriHostAdapter() : new WebHostAdapter()
  }
  return _adapter
}

// ─── 环境检测 ────────────────────────────────────────────────────

function isTauriEnv(): boolean {
  // Tauri 2.x: window.__TAURI__ 存在
  return typeof (window as any).__TAURI__ !== 'undefined'
}

// ─── Web Adapter（现有实现）───────────────────────────────────────

class WebHostAdapter implements HostAdapter {
  readonly mode = 'web' as const

  async request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    body?: unknown
  ): Promise<ApiResponse<T>> {
    const url = `${import.meta.env.VITE_API_BASE_URL || '/api'}${path}`
    const options: RequestInit = {
      method,
      headers: { 'Content-Type': 'application/json' },
    }
    if (body && (method === 'POST' || method === 'PUT')) {
      options.body = JSON.stringify(body)
    }

    const resp = await fetch(url, options)
    const json = await resp.json() as ApiResponse<T>
    return json
  }

  subscribe<T>(_event: string, _handler: (data: T) => void): () => void {
    // TODO M4: 在 Rust Runtime 阶段替换为 Tauri event listener
    // Web 模式下继续使用现有的 wsManager
    console.warn('[WebHostAdapter] subscribe() not yet implemented for web mode, use wsManager directly')
    return () => {}
  }

  isConnected(): boolean {
    // 简单检测：最近一次 API 请求是否成功
    return true
  }

  getBackendUrl(): string {
    return import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  }
}

// ─── Tauri Adapter ───────────────────────────────────────────────

class TauriHostAdapter implements HostAdapter {
  readonly mode = 'tauri' as const
  private _connected = false

  async request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    body?: unknown
  ): Promise<ApiResponse<T>> {
    try {
      const { invoke } = await import('@tauri-apps/api/core')

      if (method === 'GET') {
        const result = await invoke<ApiResponse<T>>('runtime_stream', {
          path,
          params: null,
        })
        this._connected = true
        return result as ApiResponse<T>
      }

      const result = await invoke<ApiResponse<T>>('runtime_invoke', {
        path,
        body: body ?? null,
      })
      this._connected = true
      return result as ApiResponse<T>
    } catch (err) {
      this._connected = false
      throw new Error(`Tauri invoke failed: ${err}`)
    }
  }

  subscribe<T>(event: string, handler: (data: T) => void): () => void {
    // TODO M4: Tauri event subscription (M1 placeholder)
    console.warn(`[TauriHostAdapter] subscribe("${event}") — not yet connected to event stream`)
    return () => {}
  }

  isConnected(): boolean {
    return this._connected
  }

  getBackendUrl(): string {
    return 'tauri://localhost'
  }
}

export { WebHostAdapter, TauriHostAdapter }
