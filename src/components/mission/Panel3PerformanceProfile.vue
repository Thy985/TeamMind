<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { NCard, NTag, NIcon, NSpace, NText, NSpin, NEmpty, NDataTable, type TableColumn } from 'naive-ui'
import { TrendingUpOutline, TrendingDownOutline, RemoveOutline } from '@vicons/ionicons5'
import { missionControlApi } from '@/api/axios'

interface Props {
  projectId: string
}
const props = defineProps<Props>()

const profile = ref<any>(null)
const loading = ref(true)

const trendColors: Record<string, string> = {
  IMPROVING: 'var(--color-success)',
  DECLINING: 'var(--color-error)',
  STABLE: 'var(--color-text-secondary)'
}

const trendIcons: Record<string, any> = {
  IMPROVING: TrendingUpOutline,
  DECLINING: TrendingDownOutline,
  STABLE: RemoveOutline
}

async function refresh() {
  loading.value = true
  try {
    profile.value = await missionControlApi.profile(props.projectId)
  } finally {
    loading.value = false
  }
}

onMounted(() => refresh())

// Flatten records for table
const flattenedRecords = ref<any[]>([])

function buildTableData() {
  const data: any[] = []
  const byRole = profile.value?.byRole || {}
  for (const [role, records] of Object.entries(byRole)) {
    for (const r of (records as any[])) {
      data.push({
        role,
        pluginId: r.pluginId,
        taskType: r.taskTypeId || '—',
        successRate: Math.round((r.successRate ?? 0) * 100) + '%',
        sampleSize: r.sampleSize ?? 0,
        avgDuration: r.avgDurationMs ? Math.round(r.avgDurationMs / 1000) + 's' : '—',
        avgIterations: r.avgIterations ?? '—'
      })
    }
  }
  flattenedRecords.value = data
}

function watchProfile() {
  if (profile.value) buildTableData()
}

// Use a watcher approach via onMounted re-fetch
let watchFn: any
const stopWatch = () => { try { watchFn && watchFn() } catch {} }

watch(() => profile.value, (val) => {
  if (val) buildTableData()
}, { immediate: true })
</script>

<template>
  <div v-if="loading" class="loading-wrap"><NSpin size="large" /></div>
  <div v-else>
    <!-- Trend Summary -->
    <NCard embedded class="trend-card" v-if="profile?.trend">
      <template #header>
        <NSpace>
          <NIcon :component="trendIcons[profile.trend.overallStatus] ?? RemoveOutline" 
                 :color="trendColors[profile.trend.overallStatus] ?? 'var(--color-text-secondary)'" />
          <span>整体趋势</span>
          <NTag :color="{ color: trendColors[profile.trend.overallStatus] + '22', 
                           borderColor: trendColors[profile.trend.overallStatus] ?? 'var(--color-border)',
                           textColor: trendColors[profile.trend.overallStatus] ?? 'var(--color-text-secondary)' }"
                border-type="solid" size="small">
            {{ profile.trend.overallStatus }}
          </NTag>
        </NSpace>
      </template>
      <NSpace :size="24">
        <div>
          <NText depth="2" style="font-size: var(--font-size-xs)">↑ 改善中</NText>
          <div style="font-size: var(--font-size-2xl); font-weight: 700; color: var(--color-success)">{{ profile.trend.improving }}</div>
        </div>
        <div>
          <NText depth="2" style="font-size: var(--font-size-xs)">↓ 衰退中</NText>
          <div style="font-size: var(--font-size-2xl); font-weight: 700; color: var(--color-error)">{{ profile.trend.declining }}</div>
        </div>
        <div>
          <NText depth="2" style="font-size: var(--font-size-xs)">— 稳定</NText>
          <div style="font-size: var(--font-size-2xl); font-weight: 700; color: var(--color-text-secondary)">{{ profile.trend.stable }}</div>
        </div>
        <div>
          <NText depth="2" style="font-size: var(--font-size-xs)">总记录</NText>
          <div style="font-size: var(--font-size-2xl); font-weight: 700; color: var(--color-primary)">{{ profile.trend.totalRecords }}</div>
        </div>
      </NSpace>
    </NCard>

    <!-- Performance Table -->
    <NCard embedded title="性能档案明细" style="margin-top: 16px" v-if="flattenedRecords.length > 0">
      <NDataTable
        :columns="[
          { title: '角色', key: 'role' },
          { title: 'Plugin', key: 'pluginId' },
          { title: '任务类型', key: 'taskType' },
          { title: '成功率', key: 'successRate' },
          { title: '样本数', key: 'sampleSize' },
          { title: '平均耗时', key: 'avgDuration' },
          { title: '平均迭代', key: 'avgIterations' }
        ]"
        :data="flattenedRecords"
        :pagination="false"
        striped
        size="small"
      />
    </NCard>
    <NEmpty v-else description="暂无性能数据" style="padding: 60px 0" />
  </div>
</template>

<style scoped>
.loading-wrap { display: flex; justify-content: center; padding: 60px; }
.trend-card { background: var(--color-bg-panel); border-color: var(--color-border); }
</style>
