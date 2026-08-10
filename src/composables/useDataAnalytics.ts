/**
 * 数据分析系统 - 任务统计和趋势分析
 */

import { ref, computed } from 'vue'
import { missionApi, agentApi } from '@/api/axios'
import { generateTrendData, formatNumber } from '@/utils/chartUtils'

/**
 * 统计数据
 */
export interface DashboardStats {
  totalMissions: number
  completedMissions: number
  runningMissions: number
  failedMissions: number
  successRate: number
  avgDuration: number
  totalAgents: number
  activeAgents: number
}

/**
 * 趋势数据点
 */
export interface TrendDataPoint {
  date: string
  value: number
  label?: string
}

/**
 * 分类统计
 */
export interface CategoryStats {
  name: string
  value: number
  percentage: number
  trend: 'up' | 'down' | 'stable'
}

/**
 * 数据分析 Composable
 */
export function useDataAnalytics() {
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const lastUpdated = ref<Date | null>(null)

  /**
   * 获取仪表盘统计数据
   */
  async function fetchDashboardStats(): Promise<DashboardStats | null> {
    isLoading.value = true
    error.value = null

    try {
      const response = await missionApi.stats()
      
      if (response.success && response.data) {
        const data = response.data as any
        
        return {
          totalMissions: data.total || 0,
          completedMissions: data.completed || 0,
          runningMissions: data.running || 0,
          failedMissions: data.failed || 0,
          successRate: data.successRate || 0,
          avgDuration: data.avgDuration || 0,
          totalAgents: data.totalAgents || 0,
          activeAgents: data.activeAgents || 0
        }
      }

      return null
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取统计数据失败'
      console.error('Failed to fetch dashboard stats:', err)
      return null
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取任务趋势数据 (最近 7 天)
   */
  async function fetchMissionTrend(days: number = 7): Promise<TrendDataPoint[]> {
    try {
      // 模拟数据 - 实际应该调用 API
      const baseValue = 10
      const trendData = generateTrendData(baseValue, days, 0.3)
      
      return trendData.map(d => ({
        date: d.date,
        value: d.value,
        label: formatDateLabel(d.date)
      }))
    } catch (err) {
      console.error('Failed to fetch mission trend:', err)
      return []
    }
  }

  /**
   * 获取任务状态分布
   */
  async function fetchMissionStatusDistribution(): Promise<CategoryStats[]> {
    try {
      const stats = await fetchDashboardStats()
      
      if (!stats) return []

      const total = stats.totalMissions || 1

      return [
        {
          name: '已完成',
          value: stats.completedMissions,
          percentage: (stats.completedMissions / total) * 100,
          trend: 'up'
        },
        {
          name: '运行中',
          value: stats.runningMissions,
          percentage: (stats.runningMissions / total) * 100,
          trend: stats.runningMissions > 0 ? 'up' : 'stable'
        },
        {
          name: '失败',
          value: stats.failedMissions,
          percentage: (stats.failedMissions / total) * 100,
          trend: stats.failedMissions > 0 ? 'down' : 'stable'
        }
      ]
    } catch (err) {
      console.error('Failed to fetch status distribution:', err)
      return []
    }
  }

  /**
   * 获取 Agent 性能排名
   */
  async function fetchAgentPerformance(): Promise<Array<{
    id: string
    name: string
    successRate: number
    totalTasks: number
    avgDuration: number
  }>> {
    try {
      // 模拟数据
      return [
        {
          id: 'agent-1',
          name: 'Code Assistant',
          successRate: 95,
          totalTasks: 156,
          avgDuration: 45
        },
        {
          id: 'agent-2',
          name: 'Data Analyst',
          successRate: 89,
          totalTasks: 98,
          avgDuration: 30
        },
        {
          id: 'agent-3',
          name: 'Content Writer',
          successRate: 92,
          totalTasks: 134,
          avgDuration: 25
        },
        {
          id: 'agent-4',
          name: 'Researcher',
          successRate: 85,
          totalTasks: 67,
          avgDuration: 60
        },
        {
          id: 'agent-5',
          name: 'QA Engineer',
          successRate: 97,
          totalTasks: 203,
          avgDuration: 15
        }
      ]
    } catch (err) {
      console.error('Failed to fetch agent performance:', err)
      return []
    }
  }

  /**
   * 获取成功率趋势
   */
  async function fetchSuccessRateTrend(days: number = 7): Promise<TrendDataPoint[]> {
    try {
      // 模拟数据 - 成功率趋势
      const data: TrendDataPoint[] = []
      let currentRate = 75

      for (let i = 0; i < days; i++) {
        const date = new Date()
        date.setDate(date.getDate() - (days - i - 1))
        
        // 随机波动
        currentRate = Math.min(100, Math.max(50, currentRate + (Math.random() - 0.5) * 20))

        data.push({
          date: date.toISOString().split('T')[0],
          value: Math.round(currentRate),
          label: formatDateLabel(date.toISOString().split('T')[0])
        })
      }

      return data
    } catch (err) {
      console.error('Failed to fetch success rate trend:', err)
      return []
    }
  }

  /**
   * 获取每日任务创建数量
   */
  async function fetchDailyCreationTrend(days: number = 30): Promise<TrendDataPoint[]> {
    try {
      const baseValue = 5
      const trendData = generateTrendData(baseValue, days, 0.4)
      
      return trendData.map(d => ({
        date: d.date,
        value: d.value,
        label: formatDateLabel(d.date)
      }))
    } catch (err) {
      console.error('Failed to fetch daily creation trend:', err)
      return []
    }
  }

  /**
   * 获取性能洞察
   */
  async function fetchPerformanceInsights(): Promise<Array<{
    type: 'success' | 'warning' | 'info'
    title: string
    description: string
    metric?: string
  }>> {
      const insights: Array<{
        type: 'success' | 'warning' | 'info'
        title: string
        description: string
        metric?: string
      }> = []

    try {
      const stats = await fetchDashboardStats()
      
      if (stats) {
        // 成功率洞察
        if (stats.successRate >= 90) {
          insights.push({
            type: 'success',
            title: '🎉 优秀的工作效率',
            description: '您的任务成功率超过 90%',
            metric: `${stats.successRate}%`
          })
        } else if (stats.successRate < 70) {
          insights.push({
            type: 'warning',
            title: '📈 需要提升成功率',
            description: '建议简化任务流程或使用模板',
            metric: `${stats.successRate}%`
          })
        }

        // 运行中任务
        if (stats.runningMissions > 3) {
          insights.push({
            type: 'info',
            title: '⚡ 有多个任务正在运行',
            description: '可以并行处理多个任务提高效率',
            metric: `${stats.runningMissions} 个`
          })
        }

        // 失败任务
        if (stats.failedMissions > 5) {
          insights.push({
            type: 'warning',
            title: '⚠️ 有任务执行失败',
            description: '检查失败原因并尝试重试',
            metric: `${stats.failedMissions} 个`
          })
        }
      }

      // 添加一些固定的洞察
      insights.push({
        type: 'info',
        title: '💡 高效工作时段',
        description: '上午 9-11 点是您的工作黄金时间'
      })

    } catch (err) {
      console.error('Failed to fetch insights:', err)
    }

    return insights
  }

  /**
   * 格式化日期标签
   */
  function formatDateLabel(dateStr: string): string {
    const date = new Date(dateStr)
    const today = new Date()
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)

    if (date.toDateString() === today.toDateString()) {
      return '今天'
    }
    if (date.toDateString() === yesterday.toDateString()) {
      return '昨天'
    }

    const month = date.getMonth() + 1
    const day = date.getDate()
    return `${month}/${day}`
  }

  /**
   * 导出数据报告
   */
  async function exportReport(format: 'json' | 'csv' = 'json'): Promise<string> {
    const stats = await fetchDashboardStats()
    const trend = await fetchMissionTrend()
    const distribution = await fetchMissionStatusDistribution()
    const agents = await fetchAgentPerformance()

    const report = {
      generatedAt: new Date().toISOString(),
      stats,
      trend,
      distribution,
      topAgents: agents.slice(0, 5)
    }

    if (format === 'json') {
      return JSON.stringify(report, null, 2)
    }

    // CSV 格式
    const headers = ['指标', '数值']
    const rows = [
      ['总任务数', stats?.totalMissions || 0],
      ['已完成', stats?.completedMissions || 0],
      ['成功率', `${stats?.successRate || 0}%`],
      ['总 Agent 数', stats?.totalAgents || 0]
    ]

    return [headers.join(','), ...rows.map(r => r.join(','))].join('\n')
  }

  return {
    isLoading,
    error,
    lastUpdated,
    fetchDashboardStats,
    fetchMissionTrend,
    fetchMissionStatusDistribution,
    fetchAgentPerformance,
    fetchSuccessRateTrend,
    fetchDailyCreationTrend,
    fetchPerformanceInsights,
    exportReport
  }
}
