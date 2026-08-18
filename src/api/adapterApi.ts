/**
 * Adapter-aware API — 在 Tauri 模式下通过 HostAdapter 路由请求
 *
 * 设计原则：
 * - Web 模式：直接使用 axios（向后兼容，零改动）
 * - Tauri 模式：通过 HostAdapter → tauri::invoke → Rust proxy → Spring Boot
 *
 * M2+ 阶段：当 Rust Runtime 原生实现某个能力后，
 * 对应的 API 函数可以直接调用 Rust 实现，不再经过 Spring Boot。
 */
import { getHostAdapter } from './hostAdapter'
import { api } from './axios'
import type { Mission, Task, Agent, KnowledgeEntry, Project } from '@/types'
import type { WSEvent } from '@/types'

// ─── 内部 helpers ──────────────────────────────────────────────────

async function call<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const adapter = getHostAdapter()
  const response = await adapter.request<T>(method, path, body)
  if (!response.success) throw new Error(response.error ?? `HTTP error ${path}`)
  return response.data as T
}

// ─── Projects ──────────────────────────────────────────────────────

export const projectApi = {
  list: () => call<Project[]>('GET', '/api/projects'),
  get: (id: string) => call<Project>('GET', `/api/projects/${id}`),
  create: (name: string) => call<Project>('POST', '/api/projects', { name }),
  delete: (id: string) => call<{ deleted: string }>('DELETE', `/api/projects/${id}`),
}

// ─── Tasks ─────────────────────────────────────────────────────────

export const taskApi = {
  list: (projectId: string) => call<Task[]>('GET', `/api/projects/${projectId}/tasks`),
  get: (taskId: string) => call<Task>('GET', `/api/tasks/${taskId}`),
  create: (projectId: string, objective: string) =>
    call<Task>('POST', `/api/projects/${projectId}/tasks`, { objective }),
  pause: (taskId: string) => call<void>('POST', `/api/tasks/${taskId}/pause`),
  resume: (taskId: string) => call<void>('POST', `/api/tasks/${taskId}/resume`),
  cancel: (taskId: string) => call<void>('POST', `/api/tasks/${taskId}/cancel`),
  retry: (taskId: string) => call<void>('POST', `/api/tasks/${taskId}/retry`),
  approve: (taskId: string, body: unknown) => call<void>('POST', `/api/tasks/${taskId}/approve`, body),
  events: (taskId: string, after?: number) =>
    call<WSEvent[]>('GET', after ? `/api/tasks/${taskId}/events?after=${after}` : `/api/tasks/${taskId}/events`),
  activity: (taskId: string) => call<unknown>('GET', `/api/tasks/${taskId}/activity`),
}

// ─── Mission Control ───────────────────────────────────────────────

export const mcApi = {
  overview: (projectId: string) => call<unknown>('GET', `/api/mission-control/project/${projectId}/overview`),
  running: (projectId: string) => call<unknown>('GET', `/api/mission-control/project/${projectId}/running`),
  history: (projectId: string, limit = 20) =>
    call<unknown>('GET', `/api/mission-control/project/${projectId}/history?limit=${limit}`),
  profile: (projectId: string) => call<unknown>('GET', `/api/mission-control/project/${projectId}/profile`),
  recommendation: (projectId: string) =>
    call<unknown>('GET', `/api/mission-control/project/${projectId}/recommendation`),
  drift: (projectId: string) => call<unknown>('GET', `/api/mission-control/project/${projectId}/drift`),
  recalculate: (projectId: string) => call<void>('POST', `/api/mission-control/project/${projectId}/recalculate`),
  controlMode: (projectId: string) =>
    call<unknown>('GET', `/api/mission-control/project/${projectId}/control-mode`),
  setControlMode: (projectId: string, mode: string) =>
    call<void>('POST', `/api/mission-control/project/${projectId}/control-mode`, { controlMode: mode }),
}

// ─── Agents ────────────────────────────────────────────────────────

export const agentApiV2 = {
  list: () => call<Agent[]>('GET', '/api/agents'),
  get: (id: string) => call<Agent>('GET', `/api/agents/${id}`),
  toggle: (id: string, enabled: boolean) =>
    call<unknown>('POST', `/api/agents/${id}/enabled`, { enabled }),
}

// ─── Knowledge ─────────────────────────────────────────────────────

export const knowledgeApiV2 = {
  save: (data: unknown) => call<KnowledgeEntry>('POST', '/api/knowledge', data),
  getByTask: (taskId: string) => call<KnowledgeEntry[]>('GET', `/api/knowledge/task/${taskId}`),
  getByProject: (projectId: string) => call<KnowledgeEntry[]>('GET', `/api/knowledge/project/${projectId}`),
  dismiss: (id: string) => call<void>('POST', `/api/knowledge/${id}/dismiss`),
  delete: (id: string) => call<void>('DELETE', `/api/knowledge/${id}`),
}

// ─── Health ────────────────────────────────────────────────────────

export const healthApi = {
  check: () => call<unknown>('GET', '/api/health'),
}
