<script setup lang="ts">
import { computed, ref } from 'vue'
import { NCard, NGrid, NGi, NStatistic, NProgress, NButton, NIcon } from 'naive-ui'
import { TrendingUpOutline, SpeedometerOutline, HardwareChipOutline, AlertCircleOutline } from '@vicons/ionicons5'
import { usePerformanceMonitor } from '@/composables/usePerformanceMonitor'

const {
  stats,
  isMonitoring,
  startMonitoring,
  stopMonitoring,
  exportReport,
  getPerformanceRecommendations
} = usePerformanceMonitor()

const recommendations = computed(() => getPerformanceRecommendations())

// 性能评分
const performanceScore = computed(() => {
  const { avgResponseTime, avgRenderTime, memoryUsage, errorRate } = stats.value
  
  let score = 100
  
  // API 响应时间扣分
  if (avgResponseTime > 2000) score -= 30
  else if (avgResponseTime > 1000) score -= 20
  else if (avgResponseTime > 500) score -= 10
  
  // 渲染时间扣分
  if (avgRenderTime > 200) score -= 30
  else if (avgRenderTime > 100) score -= 20
  else if (avgRenderTime > 50) score -= 10
  
  // 内存使用扣分
  if (memoryUsage > 1000) score -= 30
  else if (memoryUsage > 500) score -= 20
  else if (memoryUsage > 300) score -= 10
  
  // 错误率扣分
  if (errorRate > 10) score -= 30
  else if (errorRate > 5) score -= 20
  else if (errorRate > 1) score -= 10
  
  return Math.max(0, score)
})

// 性能等级
const performanceGrade = computed(() => {
  const score = performanceScore.value
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
})

// 性能状态颜色
const performanceColor = computed(() => {
  const grade = performanceGrade.value
  if (grade === 'A') return '#10b981'
  if (grade === 'B') return '#3b82f6'
  if (grade === 'C') return '#f59e0b'
  if (grade === 'D') return '#ef4444'
  return '#dc2626'
})

// 开始监控
if (!isMonitoring.value) {
  startMonitoring()
}

// 导出报告
function handleExport() {
  const report = exportReport()
  const blob = new Blob([report], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `performance-report-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="performance-dashboard">
    <!-- 性能评分 -->
    <NCard class="score-card">
      <div class="score-container">
        <div class="score-circle" :style="{ borderColor: performanceColor }">
          <span class="score-grade">{{ performanceGrade }}</span>
          <span class="score-value">{{ performanceScore }}</span>
        </div>
        <div class="score-info">
          <h3>性能评分</h3>
          <p>综合性能表现</p>
        </div>
      </div>
    </NCard>

    <!-- 核心指标 -->
    <NGrid :cols="4" :x-gap="16" :y-gap="16" class="metrics-grid">
      <NGi>
        <NCard class="metric-card">
          <div class="metric-icon" style="background: rgba(99, 102, 241, 0.1)">
            <NIcon><SpeedometerOutline /></NIcon>
          </div>
          <div class="metric-content">
            <div class="metric-label">API 响应时间</div>
            <div class="metric-value">{{ stats.avgResponseTime.toFixed(0) }} ms</div>
          </div>
          <NProgress 
            type="line" 
            :percentage="Math.min(100, stats.avgResponseTime / 20)" 
            :show-indicator="false"
            :height="4"
            :color="stats.avgResponseTime > 1000 ? '#ef4444' : '#10b981'"
          />
        </NCard>
      </NGi>

      <NGi>
        <NCard class="metric-card">
          <div class="metric-icon" style="background: rgba(16, 185, 129, 0.1)">
            <NIcon><TrendingUpOutline /></NIcon>
          </div>
          <div class="metric-content">
            <div class="metric-label">渲染时间</div>
            <div class="metric-value">{{ stats.avgRenderTime.toFixed(0) }} ms</div>
          </div>
          <NProgress 
            type="line" 
            :percentage="Math.min(100, stats.avgRenderTime / 2)" 
            :show-indicator="false"
            :height="4"
            :color="stats.avgRenderTime > 100 ? '#ef4444' : '#10b981'"
          />
        </NCard>
      </NGi>

      <NGi>
        <NCard class="metric-card">
          <div class="metric-icon" style="background: rgba(245, 158, 11, 0.1)">
            <NIcon><HardwareChipOutline /></NIcon>
          </div>
          <div class="metric-content">
            <div class="metric-label">内存使用</div>
            <div class="metric-value">{{ stats.memoryUsage.toFixed(0) }} MB</div>
          </div>
          <NProgress 
            type="line" 
            :percentage="Math.min(100, stats.memoryUsage / 10)" 
            :show-indicator="false"
            :height="4"
            :color="stats.memoryUsage > 500 ? '#ef4444' : '#10b981'"
          />
        </NCard>
      </NGi>

      <NGi>
        <NCard class="metric-card">
          <div class="metric-icon" style="background: rgba(239, 68, 68, 0.1)">
            <NIcon><AlertCircleOutline /></NIcon>
          </div>
          <div class="metric-content">
            <div class="metric-label">错误率</div>
            <div class="metric-value">{{ stats.errorRate.toFixed(1) }}%</div>
          </div>
          <NProgress 
            type="line" 
            :percentage="stats.errorRate" 
            :show-indicator="false"
            :height="4"
            :color="stats.errorRate > 5 ? '#ef4444' : '#10b981'"
          />
        </NCard>
      </NGi>
    </NGrid>

    <!-- 性能建议 -->
    <NCard v-if="recommendations.length > 0" class="recommendations-card">
      <h3>性能优化建议</h3>
      <ul class="recommendations-list">
        <li v-for="(rec, index) in recommendations" :key="index">
          {{ rec }}
        </li>
      </ul>
    </NCard>

    <!-- 操作按钮 -->
    <div class="actions">
      <NButton @click="handleExport">
        导出报告
      </NButton>
      <NButton v-if="isMonitoring" type="warning" @click="stopMonitoring">
        停止监控
      </NButton>
      <NButton v-else type="primary" @click="startMonitoring">
        开始监控
      </NButton>
    </div>
  </div>
</template>

<style scoped>
.performance-dashboard {
  padding: 24px;
}

.score-card {
  margin-bottom: 24px;
}

.score-container {
  display: flex;
  align-items: center;
  gap: 24px;
}

.score-circle {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 6px solid;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}

.score-grade {
  font-size: 48px;
  font-weight: bold;
  line-height: 1;
}

.score-value {
  font-size: 14px;
  margin-top: 4px;
}

.score-info h3 {
  margin: 0 0 8px 0;
  font-size: 24px;
}

.score-info p {
  margin: 0;
  color: #6b7280;
}

.metrics-grid {
  margin-bottom: 24px;
}

.metric-card {
  padding: 16px;
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  font-size: 24px;
}

.metric-content {
  margin-bottom: 12px;
}

.metric-label {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 24px;
  font-weight: bold;
}

.recommendations-card {
  margin-bottom: 24px;
}

.recommendations-card h3 {
  margin: 0 0 16px 0;
}

.recommendations-list {
  margin: 0;
  padding-left: 20px;
}

.recommendations-list li {
  margin-bottom: 8px;
  color: #6b7280;
}

.actions {
  display: flex;
  gap: 12px;
}
</style>
