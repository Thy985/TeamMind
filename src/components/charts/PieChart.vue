<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  data: Array<{
    label: string
    value: number
    color?: string
  }>
  height?: number
  showLegend?: boolean
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  height: 300,
  showLegend: true
})

// 计算总量
const total = computed(() => props.data.reduce((sum, item) => sum + item.value, 0))

// 计算百分比
const chartData = computed(() =>
  props.data.map(item => ({
    ...item,
    percentage: total.value > 0 ? (item.value / total.value * 100).toFixed(1) : 0
  }))
)

// 默认颜色
const colors = [
  'var(--color-primary)',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#3b82f6',
  'var(--color-purple)',
  '#ec4899',
  '#06b6d4'
]

// 扇形路径计算
function getArcPath(startAngle: number, endAngle: number, radius: number) {
  const start = polarToCartesian(0, 0, radius, endAngle)
  const end = polarToCartesian(0, 0, radius, startAngle)
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1'

  return [
    'M', 0, 0,
    'L', start.x, start.y,
    'A', radius, radius, 0, largeArcFlag, 0, end.x, end.y,
    'Z'
  ].join(' ')
}

function polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
  const angleInRadians = (angleInDegrees - 90) * Math.PI / 180.0
  return {
    x: centerX + radius * Math.cos(angleInRadians),
    y: centerY + radius * Math.sin(angleInRadians)
  }
}

// 计算每个扇形的角度范围
function getSliceRange(item: typeof chartData.value[0], index: number) {
  const percentage = parseFloat(String(item.percentage))
  const startAngle = chartData.value.slice(0, index).reduce((sum, d) => sum + parseFloat(String(d.percentage)), 0)
  const endAngle = startAngle + percentage
  return { startAngle: startAngle * 3.6, endAngle: endAngle * 3.6 }
}
</script>

<template>
  <div class="pie-chart">
    <h3 v-if="title" class="chart-title">{{ title }}</h3>

    <div class="chart-container">
      <!-- 图表 -->
      <svg :height="height" :viewBox="`-200 -200 400 400`">
        <g v-for="(item, index) in chartData" :key="item.label">
          <path
            :d="getArcPath(getSliceRange(item, index).startAngle, getSliceRange(item, index).endAngle, 150)"
            :fill="item.color || colors[index % colors.length]"
            class="pie-slice"
          >
            <title>{{ item.label }}: {{ item.value }} ({{ item.percentage }}%)</title>
          </path>
        </g>
      </svg>

      <!-- 中心文字 -->
      <div class="center-text">
        <div class="total-label">总计</div>
        <div class="total-value">{{ total }}</div>
      </div>
    </div>

    <!-- 图例 -->
    <div v-if="showLegend" class="legend">
      <div v-for="(item, index) in chartData" :key="item.label" class="legend-item">
        <span class="legend-color" :style="{ background: item.color || colors[index % colors.length] }"></span>
        <span class="legend-label">{{ item.label }}</span>
        <span class="legend-value">{{ item.value }} ({{ item.percentage }}%)</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pie-chart {
  width: 100%;
}

.chart-title {
  margin: 0 0 16px 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.chart-container {
  position: relative;
  display: flex;
  justify-content: center;
}

svg {
  max-width: 100%;
}

.pie-slice {
  transition: transform 0.2s ease;
  cursor: pointer;
}

.pie-slice:hover {
  transform: scale(1.02);
}

.center-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
}

.total-label {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.total-value {
  font-size: var(--font-size-3xl);
  font-weight: bold;
  color: var(--color-bg-primary);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
  justify-content: center;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.legend-label {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.legend-value {
  font-size: var(--font-size-base);
  font-weight: 500;
}
</style>
