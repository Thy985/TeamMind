<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NCard, NGrid, NGi, NStatistic, NButton, NIcon, NProgress } from 'naive-ui'
import { TrendingUpOutline, TrendingDownOutline, TimeOutline, FlashOutline, CheckmarkCircleOutline } from '@vicons/ionicons5'
import { useDataAnalytics } from '@/composables/useDataAnalytics'
import PieChart from '@/components/charts/PieChart.vue'

const {
  isLoading,
  fetchDashboardStats,
  fetchMissionTrend,
  fetchMissionStatusDistribution,
  fetchPerformanceInsights,
  exportReport
} = useDataAnalytics()

const stats = ref<any>(null)
const trendData = ref<any[]>([])
const distribution = ref<any[]>([])
const insights = ref<any[]>([])

// 加载数据
async function loadData() {
  stats.value = await fetchDashboardStats()
  trendData.value = await fetchMissionTrend(7)
  distribution.value = await fetchMissionStatusDistribution()
  insights.value = await fetchPerformanceInsights()
}

// 导出报告
async function handleExport() {
  const report = await exportReport('json')
  const blob = new Blob([report], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `analytics-report-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

// 计算趋势
function calculateTrend(): 'up' | 'down' | 'stable' {
  if (trendData.value.length < 2) return 'stable'
  const first = trendData.value[0].value
  const last = trendData.value[trendData.value.length - 1].value
  if (last > first * 1.1) return 'up'
  if (last < first * 0.9) return 'down'
  return 'stable'
}

const trend = computed(() => calculateTrend())

// 格式化数字
function formatNumber(num: number): string {
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

// 颜色
const trendColors = {
  up: '#10b981',
  down: '#ef4444',
  stable: 'var(--color-text-tertiary)'
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="analytics-dashboard">
    <div class="dashboard-header">
      <h2>数据分析</h2>
      <NButton @click="handleExport" :loading="isLoading">
        导出报告
      </NButton>
    </div>

    <!-- 核心指标 -->
    <NGrid :cols="4" :x-gap="16" :y-gap="16" class="stats-grid">
      <NGi>
        <NCard class="stat-card">
          <NStatistic label="总任务数">
            <template #default>
              {{ formatNumber(stats?.totalMissions || 0) }}
            </template>
            <template #suffix>
              <NIcon v-if="trend === 'up'" color="#10b981">
                <TrendingUpOutline />
              </NIcon>
              <NIcon v-else-if="trend === 'down'" color="#ef4444">
                <TrendingDownOutline />
              </NIcon>
            </template>
          </NStatistic>
        </NCard>
      </NGi>

      <NGi>
        <NCard class="stat-card">
          <NStatistic label="成功率">
            <template #default>
              {{ (stats?.successRate || 0).toFixed(1) }}%
            </template>
          </NStatistic>
          <NProgress
            type="line"
            :percentage="stats?.successRate || 0"
            :height="8"
            :color="(stats?.successRate || 0) >= 80 ? '#10b981' : '#f59e0b'"
          />
        </NCard>
      </NGi>

      <NGi>
        <NCard class="stat-card">
          <NStatistic label="运行中">
            <template #default>
              {{ stats?.runningMissions || 0 }}
            </template>
            <template #prefix>
              <NIcon color="#3b82f6">
                <FlashOutline />
              </NIcon>
            </template>
          </NStatistic>
        </NCard>
      </NGi>

      <NGi>
        <NCard class="stat-card">
          <NStatistic label="平均耗时">
            <template #default>
              {{ Math.round((stats?.avgDuration || 0) / 60) }} 分钟
            </template>
            <template #prefix>
              <NIcon color="var(--color-text-tertiary)">
                <TimeOutline />
              </NIcon>
            </template>
          </NStatistic>
        </NCard>
      </NGi>
    </NGrid>

    <!-- 图表区域 -->
    <NGrid :cols="2" :x-gap="16" :y-gap="16" class="charts-grid">
      <!-- 任务状态分布 -->
      <NGi>
        <NCard title="任务状态分布">
          <PieChart
            v-if="distribution.length > 0"
            :data="distribution.map(d => ({
              label: d.name,
              value: d.value,
              color: d.name === '已完成' ? '#10b981' : d.name === '运行中' ? '#3b82f6' : '#ef4444'
            }))"
            title="任务状态"
          />
          <div v-else class="empty-chart">
            暂无数据
          </div>
        </NCard>
      </NGi>

      <!-- 性能洞察 -->
      <NGi>
        <NCard title="性能洞察">
          <div class="insights-list">
            <div
              v-for="insight in insights"
              :key="insight.title"
              class="insight-item"
              :class="`insight-${insight.type}`"
            >
              <div class="insight-icon">
                <NIcon v-if="insight.type === 'success'" color="#10b981">
                  <CheckmarkCircleOutline />
                </NIcon>
                <NIcon v-else-if="insight.type === 'warning'" color="#f59e0b">
                  <TrendingDownOutline />
                </NIcon>
                <NIcon v-else color="#3b82f6">
                  <FlashOutline />
                </NIcon>
              </div>
              <div class="insight-content">
                <h4>{{ insight.title }}</h4>
                <p>{{ insight.description }}</p>
                <span v-if="insight.metric" class="insight-metric">
                  {{ insight.metric }}
                </span>
              </div>
            </div>
          </div>
        </NCard>
      </NGi>
    </NGrid>

    <!-- 趋势图表 -->
    <NCard title="7 天任务趋势" class="trend-chart">
      <div class="trend-bars">
        <div
          v-for="(item, index) in trendData"
          :key="index"
          class="trend-bar"
        >
          <div
            class="bar"
            :style="{ height: `${(item.value / Math.max(...trendData.map(d => d.value))) * 100}%` }"
          >
            <span class="bar-value">{{ item.value }}</span>
          </div>
          <span class="bar-label">{{ item.label }}</span>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.analytics-dashboard {
  padding: 24px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.dashboard-header h2 {
  margin: 0;
  font-size: var(--font-size-2xl);
}

.stats-grid {
  margin-bottom: 24px;
}

.stat-card {
  text-align: center;
}

.charts-grid {
  margin-bottom: 24px;
}

.empty-chart {
  text-align: center;
  padding: 48px;
  color: var(--color-text-tertiary);
}

.insights-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.insight-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.02);
}

.insight-success {
  background: rgba(16, 185, 129, 0.1);
}

.insight-warning {
  background: rgba(245, 158, 11, 0.1);
}

.insight-info {
  background: rgba(59, 130, 246, 0.1);
}

.insight-icon {
  font-size: var(--font-size-2xl);
}

.insight-content h4 {
  margin: 0 0 4px 0;
  font-size: var(--font-size-base);
  font-weight: 600;
}

.insight-content p {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.insight-metric {
  display: inline-block;
  margin-top: 8px;
  padding: 2px 8px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.trend-chart {
  margin-bottom: 24px;
}

.trend-bars {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  height: 200px;
  padding: 16px 0;
}

.trend-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.bar {
  width: 40px;
  background: linear-gradient(180deg, var(--color-primary) 0%, var(--color-primary-hover) 100%);
  border-radius: 4px 4px 0 0;
  display: flex;
  justify-content: center;
  min-height: 20px;
  transition: height 0.3s ease;
}

.bar-value {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: white;
  padding-top: 4px;
}

.bar-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
