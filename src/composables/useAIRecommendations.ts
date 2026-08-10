/**
 * AI 智能助手 - 智能推荐和建议系统
 */

import { ref, computed } from 'vue'
import { missionApi, agentApi, templateApi } from '@/api/axios'

/**
 * 推荐项
 */
export interface Recommendation {
  id: string
  type: 'mission' | 'agent' | 'template' | 'optimization'
  title: string
  description: string
  reason: string
  score: number
  actions?: Array<{
    label: string
    onClick: () => void
  }>
  data?: any
}

/**
 * AI 推荐 Composable
 */
export function useAIRecommendations() {
  const recommendations = ref<Recommendation[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  /**
   * 获取智能推荐
   */
  async function fetchRecommendations() {
    isLoading.value = true
    error.value = null

    try {
      // 并行获取数据
      const [missions, agents, stats] = await Promise.all([
        missionApi.list(1, 10),
        agentApi.list(),
        missionApi.stats()
      ])

      const recs: Recommendation[] = []

      // 基于任务历史的推荐
      if (missions.success && missions.data?.items) {
        const recentMissions = missions.data.items

        // 推荐 1: 高效工作流程
        const successRate = (stats.data as any)?.successRate || 0
        if (successRate > 80) {
          recs.push({
            id: 'rec-1',
            type: 'optimization',
            title: '🎯 高效工作流程',
            description: `您的任务成功率达到 ${successRate}%`,
            reason: '继续保持当前的工作方式',
            score: 95,
            actions: []
          })
        } else if (successRate < 50) {
          recs.push({
            id: 'rec-2',
            type: 'optimization',
            title: '📈 需要优化工作流程',
            description: `当前任务成功率仅为 ${successRate}%`,
            reason: '建议使用模板或简化任务步骤',
            score: 30,
            actions: [
              { label: '查看模板', onClick: () => {} }
            ]
          })
        }

        // 推荐 2: 热门 Agent
        if (agents.success && Array.isArray(agents.data)) {
          const popularAgents = (agents.data as any[])
            .filter((a: any) => a.downloadCount > 1000)
            .slice(0, 3)

          if (popularAgents.length > 0) {
            recs.push({
              id: 'rec-3',
              type: 'agent',
              title: '🔥 热门 Agent',
              description: `发现 ${popularAgents.length} 个热门 Agent`,
              reason: '这些 Agent 被广泛使用，效果很好',
              score: 85,
              data: popularAgents
            })
          }
        }
      }

      // 智能任务创建建议
      recs.push({
        id: 'rec-4',
        type: 'mission',
        title: '✨ 快速开始',
        description: '使用 AI 辅助创建任务',
        reason: 'AI 会根据您的需求推荐最佳方案',
        score: 90,
        actions: [
          { label: 'AI 创建', onClick: () => {} }
        ]
      })

      // 时间优化建议
      const hour = new Date().getHours()
      if (hour >= 9 && hour <= 11) {
        recs.push({
          id: 'rec-5',
          type: 'optimization',
          title: '☀️ 最佳工作时间',
          description: '现在是您的工作黄金时间',
          reason: '上午时段工作效率最高',
          score: 88
        })
      } else if (hour >= 14 && hour <= 16) {
        recs.push({
          id: 'rec-6',
          type: 'optimization',
          title: '🌤️ 下午工作时段',
          description: '建议处理复杂任务',
          reason: '下午时段适合深度思考',
          score: 75
        })
      }

      recommendations.value = recs.sort((a, b) => b.score - a.score)

    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取推荐失败'
      console.error('Failed to fetch recommendations:', err)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * AI 辅助任务创建
   */
  async function generateMissionSuggestion(prompt: string): Promise<{
    title: string
    description: string
    suggestedAgents: string[]
    estimatedTime: string
    steps: string[]
  } | null> {
    try {
      // 模拟 AI 分析
      const words = prompt.toLowerCase().split(/\s+/)
      
      // 关键词分析
      const keywords = {
        code: ['code', 'coding', 'program', 'develop', 'build', 'debug'],
        design: ['design', 'ui', 'ux', 'mockup', 'wireframe', 'layout'],
        research: ['research', 'analyze', 'study', 'investigate', 'report'],
        content: ['write', 'content', 'blog', 'article', 'copy', '文案'],
        data: ['data', 'database', 'sql', 'migration', 'backup']
      }

      let category = 'general'
      for (const [cat, words] of Object.entries(keywords)) {
        if (words.some(w => prompt.toLowerCase().includes(w))) {
          category = cat
          break
        }
      }

      // 生成建议
      const suggestions: Record<string, any> = {
        code: {
          title: `代码开发: ${prompt.slice(0, 30)}`,
          description: `开发 ${prompt} 相关功能，包括代码编写、测试和部署`,
          suggestedAgents: ['code-assistant', 'qa-engineer'],
          estimatedTime: '2-4 小时',
          steps: ['需求分析', '代码编写', '单元测试', '代码审查', '部署上线']
        },
        design: {
          title: `设计: ${prompt.slice(0, 30)}`,
          description: `完成 ${prompt} 的 UI/UX 设计和评审`,
          suggestedAgents: ['designer', 'ux-reviewer'],
          estimatedTime: '1-3 小时',
          steps: ['需求理解', '原型设计', '视觉设计', '设计评审', '交付物整理']
        },
        research: {
          title: `调研: ${prompt.slice(0, 30)}`,
          description: `对 ${prompt} 进行深入调研和分析`,
          suggestedAgents: ['researcher', 'analyst'],
          estimatedTime: '1-2 小时',
          steps: ['资料收集', '数据分析', '结论总结', '报告撰写']
        },
        content: {
          title: `内容创作: ${prompt.slice(0, 30)}`,
          description: `创作关于 ${prompt} 的内容`,
          suggestedAgents: ['copywriter', 'editor'],
          estimatedTime: '30 分钟 - 1 小时',
          steps: ['主题规划', '内容撰写', '编辑校对', '发布']
        },
        data: {
          title: `数据处理: ${prompt.slice(0, 30)}`,
          description: `处理和优化 ${prompt} 相关数据`,
          suggestedAgents: ['data-engineer', 'dba'],
          estimatedTime: '1-2 小时',
          steps: ['数据提取', '数据清洗', '数据分析', '结果导出']
        }
      }

      return suggestions[category] || {
        title: `任务: ${prompt.slice(0, 30)}`,
        description: prompt,
        suggestedAgents: ['assistant'],
        estimatedTime: '1-2 小时',
        steps: ['计划', '执行', '检查', '完成']
      }

    } catch (err) {
      console.error('Failed to generate suggestion:', err)
      return null
    }
  }

  /**
   * 智能错误诊断
   */
  async function diagnoseError(errorInfo: string): Promise<{
    possibleCauses: string[]
    solutions: string[]
    relatedDocs: Array<{ title: string; url: string }>
  }> {
    // 模拟 AI 诊断
    const errorPatterns = [
      {
        pattern: /network|connection|timeout/i,
        causes: ['网络连接不稳定', '服务器响应超时', '防火墙阻止'],
        solutions: [
          '检查网络连接',
          '重试请求',
          '联系管理员'
        ],
        docs: [
          { title: '网络故障排查', url: '/docs/network-troubleshooting' },
          { title: 'API 超时处理', url: '/docs/api-timeout' }
        ]
      },
      {
        pattern: /permission|unauthorized|forbidden/i,
        causes: ['权限不足', '登录状态过期', 'Token 无效'],
        solutions: [
          '重新登录',
          '申请更高权限',
          '检查 API Key'
        ],
        docs: [
          { title: '权限管理指南', url: '/docs/permissions' },
          { title: '认证说明', url: '/docs/authentication' }
        ]
      },
      {
        pattern: /database|sql|query/i,
        causes: ['数据库连接失败', 'SQL 语法错误', '数据不存在'],
        solutions: [
          '检查数据库配置',
          '验证 SQL 语句',
          '确认数据是否存在'
        ],
        docs: [
          { title: '数据库配置', url: '/docs/database-config' },
          { title: '常见 SQL 错误', url: '/docs/sql-errors' }
        ]
      }
    ]

    for (const ep of errorPatterns) {
      if (ep.pattern.test(errorInfo)) {
        return {
          possibleCauses: ep.causes,
          solutions: ep.solutions,
          relatedDocs: ep.docs
        }
      }
    }

    return {
      possibleCauses: ['未知错误'],
      solutions: ['查看错误日志', '联系技术支持'],
      relatedDocs: [
        { title: '常见问题', url: '/docs/faq' }
      ]
    }
  }

  /**
   * 智能任务优化建议
   */
  function suggestOptimizations(mission: any): Recommendation[] {
    const suggestions: Recommendation[] = []

    if (mission.nodes?.length > 5) {
      suggestions.push({
        id: `opt-${Date.now()}-1`,
        type: 'optimization',
        title: '🔄 简化任务流程',
        description: '任务包含过多步骤',
        reason: '将复杂任务拆分为多个子任务可以提高成功率',
        score: 85,
        actions: [
          { label: '查看详情', onClick: () => {} }
        ]
      })
    }

    if (!mission.agentIds || mission.agentIds.length === 0) {
      suggestions.push({
        id: `opt-${Date.now()}-2`,
        type: 'agent',
        title: '🤖 添加 Agent',
        description: '任务没有分配 Agent',
        reason: '使用 Agent 可以自动化执行任务步骤',
        score: 90,
        actions: [
          { label: '选择 Agent', onClick: () => {} }
        ]
      })
    }

    if (mission.description && mission.description.length < 20) {
      suggestions.push({
        id: `opt-${Date.now()}-3`,
        type: 'optimization',
        title: '📝 完善任务描述',
        description: '任务描述过于简略',
        reason: '详细的描述有助于 AI 更好地理解任务需求',
        score: 75,
        actions: [
          { label: '编辑描述', onClick: () => {} }
        ]
      })
    }

    return suggestions
  }

  return {
    recommendations,
    isLoading,
    error,
    fetchRecommendations,
    generateMissionSuggestion,
    diagnoseError,
    suggestOptimizations
  }
}
