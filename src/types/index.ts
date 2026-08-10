// TypeScript 类型定义

// ==================== Agent 相关 ====================

/** Agent 状态 */
export type AgentStatus = 'idle' | 'running' | 'success' | 'error' | 'waiting'

/** Agent 类型 */
export interface Agent {
  id: string
  name: string
  description: string
  icon: string
  version: string
  permissions: string[]
  status: AgentStatus
  author?: string
  downloadCount?: number
  rating?: number
  installed?: boolean
  enabled?: boolean
  evolutionVersion?: number
  evolutionScore?: number
  testReport?: AgentTestReport
}

/** Agent 测试报告 */
export interface AgentTestReport {
  passRate: number
  totalTests: number
  passedTests: number
  lastRunAt: string
}

/** 已安装的 Agent */
export interface InstalledAgent extends Agent {
  installedAt: string
  enabled: boolean
}

// ==================== Mission 相关 ====================

/** Mission 状态 */
export type MissionStatus = 'pending' | 'running' | 'completed' | 'failed' | 'paused'

/** Mission 节点（用于协作画布） */
export interface MissionNode {
  id: string
  type: 'agent' | 'input' | 'output' | 'decision'
  position: { x: number; y: number }
  data: {
    agent?: Agent
    label: string
    status: AgentStatus
    input?: Record<string, unknown>
    output?: Record<string, unknown>
    logs?: LogEntry[]
  }
}

/** Mission 连线 */
export interface MissionEdge {
  id: string
  source: string
  target: string
  type: 'dependency' | 'negotiation'
  animated?: boolean
}

/** Mission 详情 */
export interface Mission {
  id: string
  title: string
  description?: string
  status: MissionStatus
  createdAt: string
  updatedAt: string
  completedAt?: string
  nodes: MissionNode[]
  edges: MissionEdge[]
  logs: LogEntry[]
  result?: MissionResult
}

/** Mission 执行结果 */
export interface MissionResult {
  success: boolean
  output?: Record<string, unknown>
  error?: string
  metrics?: {
    duration: number
    agentCount: number
    tokenUsage?: number
  }
}

/** Mission 历史 */
export interface MissionHistory {
  id: string
  title: string
  status: MissionStatus
  createdAt: string
  completedAt?: string
  preview?: string
}

// ==================== 日志相关 ====================

/** 日志类型 */
export type LogType = 'task' | 'tool' | 'discussion' | 'resolution' | 'warning' | 'error'

/** 日志条目 */
export interface LogEntry {
  id: string
  type: LogType
  timestamp: string
  agentId?: string
  agentName?: string
  message: string
  details?: Record<string, unknown>
}

/** 决议 */
export interface Resolution {
  id: string
  topic: string
  options: ResolutionOption[]
  finalDecision?: string
  votes: Record<string, string>
  resolvedAt?: string
}

/** 决议选项 */
export interface ResolutionOption {
  id: string
  label: string
  description?: string
  votes: number
}

// ==================== 模板相关 ====================

/** 团队模板 */
export interface TeamTemplate {
  id: string
  name: string
  description: string
  icon: string
  category: string
  agents: string[]
  createdAt: string
  updatedAt: string
  isPublic: boolean
  usageCount: number
}

// ==================== 用户与设置 ====================

/** 用户信息 */
export interface User {
  id: string
  name: string
  email: string
  avatar?: string
  role: 'admin' | 'member' | 'viewer'
  createdAt: string
}

/** 模型配置 */
export interface ModelConfig {
  id: string
  name: string
  provider: string
  apiKey?: string
  baseUrl?: string
  isDefault: boolean
}

/** 应用设置 */
export interface AppSettings {
  theme: 'dark' | 'light'
  modelConfigs: ModelConfig[]
  defaultModelId?: string
  privacyMode: boolean
  autoSave: boolean
  language: string
}

// ==================== WebSocket 事件 ====================

/** WebSocket 事件类型（对齐后端 WSEvent 常量） */
export type WSEventType = 
  // 后端 WSEvent 标准事件
  | 'mission_started'
  | 'mission_completed'
  | 'mission_failed'
  | 'agent_spawned'
  | 'agent_status_update'
  | 'node_update'
  | 'log'
  | 'resolution_required'
  | 'resolution_resolved'
  | 'evolution_triggered'
  | 'evolution_completed'
  // 遗留/兼容事件
  | 'mission_updated'
  | 'ping'
  | 'pong'
  | 'collaboration:join'
  | 'collaboration:user-joined'
  | 'collaboration:user-left'
  | 'collaboration:cursor-move'
  | 'collaboration:operation'
  | 'mission:updated'
  | 'mission:completed'
  | 'mission:failed'

/**
 * WebSocket 事件（对齐后端 WSEvent 结构）
 * 后端 WSEventPublisher 发送的 JSON 结构：
 * {
 *   type: string,
 *   missionId?: string,
 *   timestamp?: string,
 *   payload?: Record<string, unknown>
 * }
 */
export interface WSEvent {
  type: WSEventType
  missionId?: string
  timestamp?: string
  /** 事件载荷（与后端 WSEvent.payload 对齐） */
  payload?: Record<string, unknown>
  /** 兼容旧代码：部分前端代码使用 data 字段 */
  data?: Record<string, unknown>
}

// ==================== API 响应 ====================

/** 通用 API 响应 */
export interface ApiResponse<T = unknown> {
  success: boolean
  data?: T
  error?: string
  message?: string
}

/** 分页响应 */
export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  hasMore: boolean
}
