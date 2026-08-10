/**
 * 数据可视化工具
 */

export interface ChartData {
  label: string
  value: number
  color?: string
}

export interface LineChartData {
  date: string
  value: number
  [key: string]: any
}

/**
 * 生成趋势数据
 */
export function generateTrendData(
  baseValue: number,
  days: number,
  volatility: number = 0.1
): LineChartData[] {
  const data: LineChartData[] = []
  let currentValue = baseValue

  for (let i = 0; i < days; i++) {
    const date = new Date()
    date.setDate(date.getDate() - (days - i - 1))

    // 随机波动
    const change = currentValue * volatility * (Math.random() - 0.5) * 2
    currentValue = Math.max(0, currentValue + change)

    data.push({
      date: date.toISOString().split('T')[0],
      value: Math.round(currentValue)
    })
  }

  return data
}

/**
 * 格式化数字
 */
export function formatNumber(num: number, decimals: number = 0): string {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toFixed(decimals)
}

/**
 * 格式化文件大小
 */
export function formatBytes(bytes: number, decimals: number = 2): string {
  if (bytes === 0) return '0 Bytes'

  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i]
}

/**
 * 格式化时间
 */
export function formatDuration(ms: number): string {
  if (ms < 1000) {
    return ms + 'ms'
  }
  if (ms < 60000) {
    return (ms / 1000).toFixed(1) + 's'
  }
  if (ms < 3600000) {
    return Math.floor(ms / 60000) + 'm'
  }
  return Math.floor(ms / 3600000) + 'h'
}

/**
 * 计算百分比
 */
export function calculatePercentage(value: number, total: number, decimals: number = 1): string {
  if (total === 0) return '0%'
  return ((value / total) * 100).toFixed(decimals) + '%'
}

/**
 * 生成随机颜色
 */
export function generateRandomColor(): string {
  const letters = '0123456789ABCDEF'
  let color = '#'
  for (let i = 0; i < 6; i++) {
    color += letters[Math.floor(Math.random() * 16)]
  }
  return color
}

/**
 * 预定义颜色方案
 */
export const colorSchemes = {
  default: ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#3b82f6', '#8b5cf6', '#ec4899', '#06b6d4'],
  pastel: ['#c4b5fd', '#6ee7b7', '#fcd34d', '#fca5a5', '#93c5fd', '#c4b5fd', '#f9a8d4', '#67e8f9'],
  dark: ['#4f46e5', '#059669', '#d97706', '#dc2626', '#2563eb', '#7c3aed', '#db2777', '#0891b2'],
  vibrant: ['#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#ffeaa7', '#dfe6e9', '#fd79a8', '#a29bfe']
}

/**
 * 获取颜色方案
 */
export function getColorScheme(name: keyof typeof colorSchemes = 'default'): string[] {
  return colorSchemes[name]
}
