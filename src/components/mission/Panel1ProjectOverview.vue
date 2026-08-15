<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NGrid, NGi, NCard, NStatistic, NProgress, NTag, NIcon } from 'naive-ui'
import { 
  PlayOutline, CheckmarkCircleOutline, CloseCircleOutline, 
  TimeOutline, SyncOutline, AlertCircleOutline 
} from '@vicons/ionicons5'
import { missionControlApi } from '@/api/axios'

interface Props {
  projectId: string
}
const props = defineProps<Props>()

const data = ref<any>(null)
const loading = ref(true)

async function refresh() {
  loading.value = true
  try {
    data.value = await missionControlApi.overview(props.projectId)
  } finally {
    loading.value = false
  }
}

onMounted(() => refresh())

const stats = computed(() => data.value || {})
const successRate = computed(() => Math.round((stats.value.successRate || 0) * 100))
</script>

<template>
  <div v-if="loading" class="loading-wrap"><NSpin size="large" /></div>
  <div v-else>
    <NGrid :cols="4" :x-gap="16" :y-gap="16">
      <NGi>
        <NCard embedded>
          <NStatistic label="总任务数" :value="stats.totalTasks ?? 0" />
        </NCard>
      </NGi>
      <NGi>
        <NCard embedded>
          <NStatistic label="已完成" :value="stats.completed ?? 0">
            <template #prefix><NIcon :component="CheckmarkCircleOutline" color="#22c55e" /></template>
          </NStatistic>
        </NCard>
      </NGi>
      <NGi>
        <NCard embedded>
          <NStatistic label="失败" :value="stats.failed ?? 0">
            <template #prefix><NIcon :component="CloseCircleOutline" color="#ef4444" /></template>
          </NStatistic>
        </NCard>
      </NGi>
      <NGi>
        <NCard embedded>
          <NStatistic label="进行中" :value="stats.pending ?? 0">
            <template #prefix><NIcon :component="PlayOutline" color="#6366f1" /></template>
          </NStatistic>
        </NCard>
      </NGi>
    </NGrid>

    <NGrid :cols="2" :x-gap="16" :y-gap="16" style="margin-top: 16px">
      <NGi>
        <NCard embedded title="成功率">
          <NProgress
            :percentage="successRate"
            :status="successRate >= 80 ? 'success' : successRate >= 50 ? 'warning' : 'error'"
            :show-text="true"
            type="line"
            :height="12"
            style="margin-top: 8px"
          />
          <NText depth="3" style="font-size: 12px; margin-top: 8px; display: block">
            基于 {{ stats.totalTasks ?? 0 }} 次任务
          </NText>
        </NCard>
      </NGi>
      <NGi>
        <NCard embedded title="平均耗时">
          <NStatistic 
            :value="stats.avgDurationMs ? Math.round(stats.avgDurationMs / 1000) + 's' : '—'" 
            label="Task Duration"
          />
          <NText depth="3" style="font-size: 12px; margin-top: 8px; display: block">
            从任务提交到完成
          </NText>
        </NCard>
      </NGi>
    </NGrid>

    <div style="margin-top: 16px">
      <NCard embedded title="控制模式">
        <NSpace>
          <NTag :color="{ color: stats.controlMode === 'AUTOMATED' ? '#22c55e22' : '#3a3a5c', borderColor: stats.controlMode === 'AUTOMATED' ? '#22c55e' : '#3a3a5c', textColor: stats.controlMode === 'AUTOMATED' ? '#22c55e' : '#94a3b8' }" border-type="solid">
            {{ stats.controlMode ?? 'SUPERVISED' }}
          </NTag>
          <NText depth="3" style="font-size: 13px">
            {{ stats.controlMode === 'AUTOMATED' ? 'Agent 自动执行，无需人工确认' 
              : stats.controlMode === 'SUPERVISED' ? '关键操作需人工审批' 
              : '所有操作需人工确认' }}
          </NText>
        </NSpace>
      </NCard>
    </div>
  </div>
</template>

<style scoped>
.loading-wrap { display: flex; justify-content: center; padding: 60px; }
</style>
