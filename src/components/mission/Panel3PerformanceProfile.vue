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
  IMPROVING: '#22c55e',
  DECLINING: '#ef4444',
  STABLE: '#94a3b8'
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
                 :color="trendColors[profile.trend.overallStatus] ?? '#94a3b8'" />
          <span>整体趋势</span>
          <NTag :color="{ color: trendColors[profile.trend.overallStatus] + '22', 
                           borderColor: trendColors[profile.trend.overallStatus] ?? '#3a3a5c',
                           textColor: trendColors[profile.trend.overallStatus] ?? '#94a3b8' }"
                border-type="solid" size="small">
            {{ profile.trend.overallStatus }}
          </NTag>
        </NSpace>
      </template>
      <NSpace :size="24">
        <div>
          <NText depth="2" style="font-size: 12px">↑ 改善中</NText>
          <div style="font-size: 24px; font-weight: 700; color: #22c55e">{{ profile.trend.improving }}</div>
        </div>
        <div>
          <NText depth="2" style="font-size: 12px">↓ 衰退中</NText>
          <div style="font-size: 24px; font-weight: 700; color: #ef4444">{{ profile.trend.declining }}</div>
        </div>
        <div>
          <NText depth="2" style="font-size: 12px">— 稳定</NText>
          <div style="font-size: 24px; font-weight: 700; color: #94a3b8">{{ profile.trend.stable }}</div>
        </div>
        <div>
          <NText depth="2" style="font-size: 12px">总记录</NText>
          <div style="font-size: 24px; font-weight: 700; color: #6366f1">{{ profile.trend.totalRecords }}</div>
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
.trend-card { background: #1e1e2e; border-color: #3a3a5c; }
</style>
