<script setup lang="ts">
import { ref, computed } from 'vue'
import { NCard, NGrid, NGi, NTag, NIcon, NText, NSpace, NSpin, NEmpty, NButton, useMessage } from 'naive-ui'
import { 
  PlayOutline, CheckmarkCircleOutline, CloseCircleOutline,
  TimeOutline, RefreshOutline, AlertCircleOutline, FolderOutline
} from '@vicons/ionicons5'
import { missionControlApi } from '@/api/axios'
import type { MissionHistory } from '@/types'

interface Props {
  projectId: string
}
const props = defineProps<Props>()

const emit = defineEmits<{
  'task-selected': [taskId: string]
}>()

const runningTasks = ref<MissionHistory[]>([])
const historyTasks = ref<MissionHistory[]>([])
const loading = ref(true)
const message = useMessage()

const stateColors: Record<string, { color: string; bg: string }> = {
  ORCHESTRATING: { color: 'var(--color-primary)', bg: 'var(--color-primary)22' },
  EXECUTING: { color: 'var(--color-warning)', bg: 'var(--color-warning)22' },
  VERIFYING: { color: 'var(--color-purple)', bg: 'var(--color-purple)22' },
  NEEDS_APPROVAL: { color: 'var(--color-error)', bg: 'var(--color-error)22' },
  DONE: { color: 'var(--color-success)', bg: 'var(--color-success)22' },
  FAILED: { color: 'var(--color-error)', bg: 'var(--color-error)22' },
  CANCELLED: { color: 'var(--color-text-tertiary)', bg: 'var(--color-text-tertiary)22' }
}

async function refresh() {
  loading.value = true
  try {
    const [running, history] = await Promise.all([
      missionControlApi.runningTasks(props.projectId),
      missionControlApi.history(props.projectId, 10)
    ])
    runningTasks.value = (running as any) || []
    historyTasks.value = (history as any) || []
  } catch (e) {
    console.error('Failed to load tasks:', e)
    message.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

function selectTask(taskId: string) {
  emit('task-selected', taskId)
}

function formatDuration(ms: number | null) {
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
          <NIcon :component="PlayOutline" color="var(--color-primary)" />
          <span>正在执行 ({{ runningTasks.length }})</span>
        </NSpace>
        <NButton size="tiny" @click="refresh"><template #icon><NIcon :component="RefreshOutline" /></template></NButton>
      </div>
      <div class="panel-body">
        <NList v-if="runningTasks.length > 0">
          <NListItem v-for="task in runningTasks" :key="task.id" class="task-item" @click="selectTask(task.id)">
            <template #prefix>
              <NTag
                :color="{ color: stateColors[task.state]?.bg ?? 'var(--color-border)', borderColor: stateColors[task.state]?.color ?? 'var(--color-border)', textColor: stateColors[task.state]?.color ?? 'var(--color-text-secondary)' }"
                size="small"
              >
                {{ task.state ?? task.status }}
              </NTag>
            </template>
            <div class="task-info">
              <NText depth="2" style="font-size: var(--font-size-sm)">{{ task.title ?? 'Untitled' }}</NText>
              <NSpace size="small" style="margin-top: 4px">
                <NText depth="3" style="font-size: var(--font-size-2xs)">
                  <NIcon :component="TimeOutline" size="12" />
                  {{ formatDuration((task as any).durationMs) }}
                </NText>
              </NSpace>
            </div>
            <NIcon :component="PlayOutline" color="var(--color-primary)" size="16" style="flex-shrink:0" />
          </NListItem>
        </NList>
        <NEmpty v-else description="当前没有执行中的任务" style="padding: 40px 0" />
      </div>
    </div>

    <!-- Right: Recent History -->
    <div class="panel">
      <div class="panel-header">
        <NSpace>
          <NIcon :component="TimeOutline" color="var(--color-text-secondary)" />
          <span>最近执行记录</span>
        </NSpace>
      </div>
      <div class="panel-body">
        <NList v-if="historyTasks.length > 0">
          <NListItem v-for="task in historyTasks.slice(0, 8)" :key="task.id" class="task-item" @click="selectTask(task.id)">
            <template #prefix>
              <NIcon
                :component="task.state === 'DONE' || task.status === 'completed' ? CheckmarkCircleOutline : CloseCircleOutline"
                :color="task.state === 'DONE' || task.status === 'completed' ? 'var(--color-success)' : 'var(--color-error)'"
              />
            </template>
            <div class="task-info">
              <NText depth="2" style="font-size: var(--font-size-sm)">{{ task.title ?? 'Untitled' }}</NText>
              <NSpace size="small" style="margin-top: 4px">
                <NTag size="tiny" :color="stateColors[task.state]?.bg ?? 'var(--color-border)'" :text-color="stateColors[task.state]?.color ?? 'var(--color-text-secondary)'">
                  {{ task.state ?? task.status }}
                </NTag>
                <NText depth="3" style="font-size: var(--font-size-2xs)">{{ formatDuration((task as any).durationMs) }}</NText>
              </NSpace>
            </div>
            <NIcon :component="PlayOutline" color="var(--color-primary)" size="16" style="flex-shrink:0" />
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
  background: var(--color-bg-panel);
  border-radius: 12px;
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: var(--color-bg-hover);
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.task-item {
  cursor: pointer;
  transition: background 0.15s;
}
.task-item:hover {
  background: var(--color-bg-hover);
}

.task-info {
  flex: 1;
  min-width: 0;
}
</style>
