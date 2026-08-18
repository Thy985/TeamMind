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

export function initHostAdapter(): HostAdapter {
  if (_adapter) return _adapter
  _adapter = isTauriEnv() ? new TauriHostAdapter() : new WebHostAdapter()
  console.log(`[HostAdapter] ${_adapter.mode} mode`)
  return _adapter
}

export function forceHostAdapter(mode: 'web' | 'tauri'): void {
  _adapter = mode === 'web' ? new WebHostAdapter() : new TauriHostAdapter()
  console.log(`[HostAdapter] forced: ${mode}`)
}

export function getHostAdapter(): HostAdapter {
  if (!_adapter) _adapter = isTauriEnv() ? new TauriHostAdapter() : new WebHostAdapter()
  return _adapter
}

function isTauriEnv(): boolean {
  return typeof (window as any).__TAURI__ !== 'undefined'
}

// ─── Web Adapter ─────────────────────────────────────────────────

class WebHostAdapter implements HostAdapter {
  readonly mode = 'web' as const

  async request<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<ApiResponse<T>> {
    const base = (import.meta as any).env?.VITE_API_BASE_URL || '/api'
    const url = `${base}${path}`
    const opts: RequestInit = { method, headers: { 'Content-Type': 'application/json' } }
    if (body && (method === 'POST' || method === 'PUT')) opts.body = JSON.stringify(body)
    const resp = await fetch(url, opts)
    return resp.json() as Promise<ApiResponse<T>>
  }

  subscribe<T>(_event: string, _handler: (data: T) => void): () => void {
    console.warn('[WebHostAdapter] subscribe() — use wsManager directly')
    return () => {}
  }

  isConnected(): boolean { return true }
  getBackendUrl(): string { return (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080' }
}

// ─── Tauri Adapter ───────────────────────────────────────────────

class TauriHostAdapter implements HostAdapter {
  readonly mode = 'tauri' as const
  private _connected = false
  // Lazy-loaded Tauri invoke — avoids bundling in web mode
  private _invoke: ((cmd: string, args: unknown) => Promise<unknown>) | null = null

  private async getInvoke(): Promise<(cmd: string, args: unknown) => Promise<unknown>> {
    if (this._invoke) return this._invoke
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const mod = await import('@tauri-apps/api/core' as any)
    this._invoke = (mod as any).invoke
    return this._invoke!
  }

  async request<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<ApiResponse<T>> {
    try {
      const invoke = await this.getInvoke()
      const result = await invoke(method === 'GET' ? 'runtime_stream' : 'runtime_invoke', {
        path,
        ...(method === 'GET' ? { params: null } : { body: body ?? null }),
      }) as ApiResponse<T>
      this._connected = true
      return result
    } catch (err) {
      this._connected = false
      throw new Error(`Tauri invoke failed: ${err}`)
    }
  }

  subscribe<T>(_event: string, _handler: (data: T) => void): () => void {
    console.warn(`[TauriHostAdapter] subscribe("${_event}") — M4: wire to Tauri event stream`)
    return () => {}
  }

  isConnected(): boolean { return this._connected }
  getBackendUrl(): string { return 'tauri://localhost' }
}

export { WebHostAdapter, TauriHostAdapter }
