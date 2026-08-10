import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

/**
 * 性能监控 Composable
 */

export interface PerformanceMetric {
  name: string
  value: number
  unit: string
  timestamp: number
  category: 'network' | 'render' | 'memory' | 'cpu'
}

export interface PerformanceStats {
  avgResponseTime: number
  avgRenderTime: number
  memoryUsage: number
  cpuUsage: number
  errorRate: number
  requestCount: number
  errorCount: number
}

export function usePerformanceMonitor() {
  const metrics = ref<PerformanceMetric[]>([])
  const isMonitoring = ref(false)
  const startTime = ref<number>(0)

  // 统计数据
  const stats = computed<PerformanceStats>(() => {
    const networkMetrics = metrics.value.filter(m => m.category === 'network')
    const renderMetrics = metrics.value.filter(m => m.category === 'render')
    const memoryMetrics = metrics.value.filter(m => m.category === 'memory')
    const cpuMetrics = metrics.value.filter(m => m.category === 'cpu')

    const avgResponseTime = networkMetrics.length > 0
      ? networkMetrics.reduce((sum, m) => sum + m.value, 0) / networkMetrics.length
      : 0

    const avgRenderTime = renderMetrics.length > 0
      ? renderMetrics.reduce((sum, m) => sum + m.value, 0) / renderMetrics.length
      : 0

    const memoryUsage = memoryMetrics.length > 0
      ? memoryMetrics[memoryMetrics.length - 1].value
      : 0

    const cpuUsage = cpuMetrics.length > 0
      ? cpuMetrics[cpuMetrics.length - 1].value
      : 0

    const errorCount = metrics.value.filter(m => m.name === 'error').length
    const requestCount = networkMetrics.length
    const errorRate = requestCount > 0 ? (errorCount / requestCount) * 100 : 0

    return {
      avgResponseTime,
      avgRenderTime,
      memoryUsage,
      cpuUsage,
      errorRate,
      requestCount,
      errorCount
    }
  })

  /**
   * 开始监控
   */
  function startMonitoring() {
    isMonitoring.value = true
    startTime.value = Date.now()

    // 监控内存使用
    const memoryInterval = setInterval(() => {
      if ((performance as any).memory) {
        const memory = (performance as any).memory
        addMetric({
          name: 'memory_used',
          value: memory.usedJSHeapSize / 1024 / 1024, // MB
          unit: 'MB',
          category: 'memory'
        })
      }
    }, 5000)

    // 监控性能指标
    const performanceObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        if (entry.entryType === 'measure') {
          addMetric({
            name: entry.name,
            value: entry.duration,
            unit: 'ms',
            category: 'render'
          })
        }
      })
    })

    performanceObserver.observe({ entryTypes: ['measure', 'navigation', 'resource'] })

    // 清理函数
    onBeforeUnmount(() => {
      clearInterval(memoryInterval)
      performanceObserver.disconnect()
    })
  }

  /**
   * 停止监控
   */
  function stopMonitoring() {
    isMonitoring.value = false
  }

  /**
   * 添加指标
   */
  function addMetric(metric: Omit<PerformanceMetric, 'timestamp'>) {
    metrics.value.push({
      ...metric,
      timestamp: Date.now()
    })

    // 保持最近 1000 条记录
    if (metrics.value.length > 1000) {
      metrics.value = metrics.value.slice(-1000)
    }
  }

  /**
   * 记录 API 请求
   */
  function trackAPIRequest(url: string, duration: number, success: boolean) {
    addMetric({
      name: success ? 'api_success' : 'api_error',
      value: duration,
      unit: 'ms',
      category: 'network'
    })
  }

  /**
   * 记录渲染时间
   */
  function trackRenderTime(componentName: string, duration: number) {
    addMetric({
      name: `render_${componentName}`,
      value: duration,
      unit: 'ms',
      category: 'render'
    })
  }

  /**
   * 记录用户操作
   */
  function trackUserAction(action: string, duration?: number) {
    addMetric({
      name: `user_${action}`,
      value: duration || 0,
      unit: 'ms',
      category: 'cpu'
    })
  }

  /**
   * 清除所有指标
   */
  function clearMetrics() {
    metrics.value = []
  }

  /**
   * 导出性能报告
   */
  function exportReport(): string {
    const report = {
      startTime: startTime.value,
      endTime: Date.now(),
      duration: Date.now() - startTime.value,
      stats: stats.value,
      metrics: metrics.value
    }

    return JSON.stringify(report, null, 2)
  }

  /**
   * 获取性能建议
   */
  function getPerformanceRecommendations(): string[] {
    const recommendations: string[] = []
    const { avgResponseTime, avgRenderTime, memoryUsage, errorRate } = stats.value

    if (avgResponseTime > 1000) {
      recommendations.push('API 响应时间过长，建议优化后端性能或添加缓存')
    }

    if (avgRenderTime > 100) {
      recommendations.push('组件渲染时间过长，建议使用虚拟滚动或减少不必要的重渲染')
    }

    if (memoryUsage > 500) {
      recommendations.push('内存使用过高，建议检查内存泄漏或优化数据结构')
    }

    if (errorRate > 5) {
      recommendations.push('错误率过高，建议检查错误日志并修复问题')
    }

    return recommendations
  }

  return {
    metrics,
    stats,
    isMonitoring,
    startMonitoring,
    stopMonitoring,
    addMetric,
    trackAPIRequest,
    trackRenderTime,
    trackUserAction,
    clearMetrics,
    exportReport,
    getPerformanceRecommendations
  }
}
