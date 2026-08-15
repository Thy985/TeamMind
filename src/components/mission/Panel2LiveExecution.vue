<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NList, NListItem, NTag, NIcon, NSpace, NText, NButton, NSpin } from 'naive-ui'
import { 
  PlayOutline, CheckmarkCircleOutline, CloseCircleOutline, 
  TimeOutline, RefreshOutline, AlertCircleOutline
} from '@vicons/ionicons5'
import { missionControlApi } from '@/api/axios'

interface Props {
  projectId: string
}
const props = defineProps<Props>()

const runningTasks = ref<any[]>([])
const historyTasks = ref<any[]>([])
const loading = ref(true)

const stateColors: Record<string, { color: string; bg: string }> = {
  ORCHESTRATING: { color: '#6366f1', bg: '#6366f122' },
  EXECUTING: { color: '#f59e0b', bg: '#f59e0b22' },
  VERIFYING: { color: '#8b5cf6', bg: '#8b5cf622' },
  NEEDS_APPROVAL: { color: '#ef4444', bg: '#ef444422' },
  DONE: { color: '#22c55e', bg: '#22c55e22' },
  FAILED: { color: '#ef4444', bg: '#ef444422' },
  CANCELLED: { color: '#64748b', bg: '#64748b22' }
}

async function refresh() {
  loading.value = true
  try {
    const [running, history] = await Promise.all([
      missionControlApi.runningTasks(props.projectId),
      missionControlApi.history(props.projectId, 10)
    ])
    runningTasks.value = running || []
    historyTasks.value = history || []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refresh()
  // Auto-refresh every 5 seconds for live updates
  const interval = setInterval(refresh, 5000)
  return () => clearInterval(interval)
})

const formatDuration = (ms: number | null) => {
  if (!ms) return '—'
  if (ms < 60000) return `${Math.round(ms / 1000)}s`
  return `${Math.round(ms / 60000)}m${Math.round((ms % 60000) / 1000)}s`
}
</script>

<template>
  <div v-if="loading" class="loading-wrap"><NSpin size="large" /></div>
  <div v-else class="panels">
    <!-- Left: Running Tasks -->
    <div class="panel">
      <div class="panel-header">
        <NSpace>
          <NIcon :component="PlayOutline" color="#6366f1" />
          <span>正在执行 ({{ runningTasks.length }})</span>
        </NSpace>
        <NButton size="tiny" @click="refresh"><template #icon><NIcon :component="RefreshOutline" /></template></NButton>
      </div>
      <div class="panel-body">
        <NList v-if="runningTasks.length > 0">
          <NListItem v-for="task in runningTasks" :key="task.id">
            <template #prefix>
              <NTag 
                :color="stateColors[task.state]?.bg ?? '#3a3a5c'" 
                :border-color="stateColors[task.state]?.color ?? '#3a3a5c'"
                :text-color="stateColors[task.state]?.color ?? '#94a3b8'"
                size="small"
              >
                {{ task.state }}
              </NTag>
            </template>
            <div class="task-info">
              <NText depth="2" style="font-size: 13px">{{ task.objective ?? 'Untitled' }}</NText>
              <NSpace size="small" style="margin-top: 4px">
                <NText depth="3" style="font-size: 11px">
                  <NIcon :component="TimeOutline" size="12" />
                  {{ task.currentAgentId ?? '?' }} · {{ task.currentRole ?? '?' }}
                </NText>
                <NText depth="3" style="font-size: 11px">重试 {{ task.retryCount ?? 0 }} 次</NText>
              </NSpace>
            </div>
          </NListItem>
        </NList>
        <NEmpty v-else description="当前没有执行中的任务" style="padding: 40px 0" />
      </div>
    </div>

    <!-- Right: Recent History -->
    <div class="panel">
      <div class="panel-header">
        <NSpace>
          <NIcon :component="TimeOutline" color="#94a3b8" />
          <span>最近执行记录</span>
        </NSpace>
      </div>
      <div class="panel-body">
        <NList v-if="historyTasks.length > 0">
          <NListItem v-for="task in historyTasks.slice(0, 8)" :key="task.id">
            <template #prefix>
              <NIcon 
                :component="task.state === 'DONE' ? CheckmarkCircleOutline : CloseCircleOutline" 
                :color="task.state === 'DONE' ? '#22c55e' : '#ef4444'" 
              />
            </template>
            <div class="task-info">
              <NText depth="2" style="font-size: 13px">{{ task.objective ?? 'Untitled' }}</NText>
              <NSpace size="small" style="margin-top: 4px">
                <NTag size="tiny" :color="stateColors[task.state]?.bg ?? '#3a3a5c'" :text-color="stateColors[task.state]?.color ?? '#94a3b8'">
                  {{ task.state }}
                </NTag>
                <NText depth="3" style="font-size: 11px">
                  {{ task.currentAgentId ?? '?' }}
                </NText>
                <NText depth="3" style="font-size: 11px">
                  {{ formatDuration(task.durationMs) }}
                </NText>
              </NSpace>
            </div>
          </NListItem>
        </NList>
        <NEmpty v-else description="暂无执行记录" style="padding: 40px 0" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading-wrap { display: flex; justify-content: center; padding: 60px; }

.panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  height: 100%;
}

.panel {
  display: flex;
  flex-direction: column;
  background: #1e1e2e;
  border-radius: 12px;
  border: 1px solid #3a3a5c;
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: #252538;
  border-bottom: 1px solid #3a3a5c;
  font-weight: 600;
  font-size: 14px;
  color: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.task-info {
  flex: 1;
  min-width: 0;
}

:deep(.n-list-item) {
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 4px;
}

:deep(.n-list-item:hover) {
  background: #252538;
}
</style>
