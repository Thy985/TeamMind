import { z } from 'zod'

/**
 * API 响应类型验证 Schema
 */

// ==================== 通用 Schema ====================

export const ApiResponseSchema = z.object({
  success: z.boolean(),
  data: z.unknown().optional(),
  message: z.string().optional(),
  code: z.number().optional()
})

export const PaginatedResponseSchema = z.object({
  success: z.boolean(),
  data: z.object({
    items: z.array(z.unknown()),
    total: z.number(),
    page: z.number().optional(),
    pageSize: z.number().optional()
  }).optional()
})

// ==================== Agent Schema ====================

export const AgentStatusSchema = z.enum(['idle', 'running', 'success', 'error', 'waiting'])

export const AgentTestReportSchema = z.object({
  passRate: z.number().min(0).max(100),
  totalTests: z.number().int().positive(),
  passedTests: z.number().int().nonnegative(),
  lastRunAt: z.string().datetime()
})

export const AgentSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1).max(100),
  description: z.string().max(500),
  icon: z.string().url(),
  version: z.string(),
  permissions: z.array(z.string()),
  status: AgentStatusSchema,
  author: z.string().optional(),
  downloadCount: z.number().int().nonnegative().optional(),
  rating: z.number().min(0).max(5).optional(),
  installed: z.boolean().optional(),
  enabled: z.boolean().optional(),
  evolutionVersion: z.number().int().nonnegative().optional(),
  evolutionScore: z.number().min(0).max(100).optional(),
  testReport: AgentTestReportSchema.optional()
})

export const InstalledAgentSchema = AgentSchema.extend({
  installedAt: z.string().datetime(),
  enabled: z.boolean()
})

// ==================== Mission Schema ====================

export const MissionStatusSchema = z.enum(['pending', 'running', 'completed', 'failed', 'paused'])

export const MissionNodeSchema = z.object({
  id: z.string(),
  type: z.enum(['agent', 'input', 'output', 'decision']),
  position: z.object({
    x: z.number(),
    y: z.number()
  }),
  data: z.object({
    agent: AgentSchema.optional(),
    label: z.string(),
    status: AgentStatusSchema,
    input: z.record(z.unknown()).optional(),
    output: z.record(z.unknown()).optional(),
    logs: z.array(z.unknown()).optional()
  })
})

export const MissionEdgeSchema = z.object({
  id: z.string(),
  source: z.string(),
  target: z.string(),
  type: z.enum(['dependency', 'negotiation']),
  animated: z.boolean().optional()
})

export const MissionResultSchema = z.object({
  success: z.boolean(),
  output: z.record(z.unknown()).optional(),
  error: z.string().optional(),
  metrics: z.object({
    duration: z.number().int().positive(),
    agentCount: z.number().int().positive(),
    tokenUsage: z.number().int().nonnegative().optional()
  }).optional()
})

export const MissionSchema = z.object({
  id: z.string().uuid(),
  title: z.string().min(1).max(200),
  description: z.string().max(1000).optional(),
  status: MissionStatusSchema,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  completedAt: z.string().datetime().optional(),
  nodes: z.array(MissionNodeSchema),
  edges: z.array(MissionEdgeSchema),
  logs: z.array(z.unknown()),
  result: MissionResultSchema.optional()
})

export const MissionHistorySchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  status: MissionStatusSchema,
  createdAt: z.string().datetime(),
  completedAt: z.string().datetime().optional(),
  duration: z.number().int().nonnegative().optional(),
  result: z.string().optional()
})

// ==================== Template Schema ====================

export const TemplateSchema = z.object({
  id: z.string().uuid(),
  name: z.string().min(1).max(100),
  description: z.string().max(500).optional(),
  icon: z.string().optional(),
  config: z.record(z.unknown()).optional(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
})

// ==================== Log Schema ====================

export const LogEntrySchema = z.object({
  id: z.string(),
  timestamp: z.string().datetime(),
  type: z.enum(['task', 'tool', 'discussion', 'resolution', 'warning', 'error']),
  source: z.string(),
  message: z.string(),
  data: z.record(z.unknown()).optional(),
  level: z.enum(['debug', 'info', 'warn', 'error']).optional()
})

// ==================== WebSocket Schema ====================

export const WSEventSchema = z.object({
  type: z.string(),
  timestamp: z.string().datetime().optional(),
  data: z.record(z.unknown()).optional(),
  requestId: z.string().optional()
})

// ==================== 类型导出 ====================

export type Agent = z.infer<typeof AgentSchema>
export type InstalledAgent = z.infer<typeof InstalledAgentSchema>
export type AgentStatus = z.infer<typeof AgentStatusSchema>
export type AgentTestReport = z.infer<typeof AgentTestReportSchema>

export type Mission = z.infer<typeof MissionSchema>
export type MissionNode = z.infer<typeof MissionNodeSchema>
export type MissionEdge = z.infer<typeof MissionEdgeSchema>
export type MissionStatus = z.infer<typeof MissionStatusSchema>
export type MissionResult = z.infer<typeof MissionResultSchema>
export type MissionHistory = z.infer<typeof MissionHistorySchema>

export type Template = z.infer<typeof TemplateSchema>

export type LogEntry = z.infer<typeof LogEntrySchema>

export type WSEvent = z.infer<typeof WSEventSchema>

// ==================== 验证函数 ====================

/**
 * 验证 API 响应
 */
export function validateApiResponse<T>(data: unknown, schema: z.ZodSchema<T>): T {
  const result = ApiResponseSchema.safeParse(data)
  if (!result.success) {
    throw new Error(`Invalid API response: ${result.error.message}`)
  }

  if (!result.data.success) {
    throw new Error(result.data.message || 'API request failed')
  }

  if (result.data.data === undefined) {
    throw new Error('API response data is missing')
  }

  return schema.parse(result.data.data)
}

/**
 * 验证分页响应
 */
export function validatePaginatedResponse<T>(
  data: unknown,
  itemSchema: z.ZodSchema<T>
): { items: T[]; total: number; page?: number; pageSize?: number } {
  const result = PaginatedResponseSchema.safeParse(data)
  if (!result.success) {
    throw new Error(`Invalid paginated response: ${result.error.message}`)
  }

  if (!result.data.success || !result.data.data) {
    throw new Error('Paginated response failed')
  }

  return {
    items: result.data.data.items.map((item: any) => itemSchema.parse(item)),
    total: result.data.data.total,
    page: result.data.data.page,
    pageSize: result.data.data.pageSize
  }
}

/**
 * 安全的类型检查
 */
export function isAgent(data: unknown): data is Agent {
  return AgentSchema.safeParse(data).success
}

export function isMission(data: unknown): data is Mission {
  return MissionSchema.safeParse(data).success
}

export function isTemplate(data: unknown): data is Template {
  return TemplateSchema.safeParse(data).success
}

export function isWSEvent(data: unknown): data is WSEvent {
  return WSEventSchema.safeParse(data).success
}
