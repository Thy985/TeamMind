<script setup lang="ts">
import { ref, computed } from 'vue'
import { NScrollbar, NInput, NButton, NIcon, NTag, NSpace, NEmpty, NSelect } from 'naive-ui'
import { SearchOutline, DownloadOutline, CheckmarkOutline } from '@vicons/ionicons5'
import type { LogEntry, LogType } from '@/types'

const props = defineProps<{
  logs: LogEntry[]
}>()

// State
const searchQuery = ref('')
const selectedType = ref<LogType | 'all'>('all')

// Log type options
const typeOptions = [
  { label: 'All', value: 'all' },
  { label: 'Task', value: 'task' },
  { label: 'Tool', value: 'tool' },
  { label: 'Discussion', value: 'discussion' },
  { label: 'Resolution', value: 'resolution' },
  { label: 'Warning', value: 'warning' },
  { label: 'Error', value: 'error' }
]

// Type config
const typeConfig: Record<LogType, { color: 'default' | 'info' | 'success' | 'warning' | 'error'; icon: string }> = {
  task: { color: 'info', icon: '📋' },
  tool: { color: 'default', icon: '🔧' },
  discussion: { color: 'info', icon: '💬' },
  resolution: { color: 'success', icon: '✅' },
  warning: { color: 'warning', icon: '⚠️' },
  error: { color: 'error', icon: '❌' }
}

// Filtered logs
const filteredLogs = computed(() => {
  let result = props.logs
  
  if (selectedType.value !== 'all') {
    result = result.filter(log => log.type === selectedType.value)
  }
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(log => 
      log.message.toLowerCase().includes(query) ||
      log.agentName?.toLowerCase().includes(query)
    )
  }
  
  return result
})

// Export logs
function handleExport() {
  const data = JSON.stringify(filteredLogs.value, null, 2)
  const blob = new Blob([data], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `teammind-logs-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

// Format timestamp
function formatTime(timestamp: string) {
  return new Date(timestamp).toLocaleTimeString()
}
</script>

<template>
  <div class="structured-console">
    <!-- Filters -->
    <div class="console-header">
      <NSpace>
        <NInput 
          v-model:value="searchQuery"
          placeholder="Search logs..."
          clearable
          size="small"
          style="width: 200px"
        >
          <template #prefix>
            <NIcon><SearchOutline /></NIcon>
          </template>
        </NInput>
        <NSelect 
          v-model:value="selectedType"
          :options="typeOptions"
          size="small"
          style="width: 120px"
        />
        <NButton size="small" @click="handleExport">
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          Export
        </NButton>
      </NSpace>
    </div>

    <!-- Logs -->
    <NScrollbar class="console-scroll">
      <div v-if="filteredLogs.length > 0" class="logs-container">
        <div 
          v-for="log in filteredLogs" 
          :key="log.id"
          class="log-entry"
          :class="`log-type-${log.type}`"
        >
          <span class="log-time">{{ formatTime(log.timestamp) }}</span>
          <span class="log-icon">{{ typeConfig[log.type].icon }}</span>
          <NTag 
            v-if="log.agentName"
            size="small"
            :bordered="false"
          >
            {{ log.agentName }}
          </NTag>
          <span class="log-message">{{ log.message }}</span>
        </div>
      </div>
      
      <NEmpty 
        v-else 
        description="暂无日志记录"
        style="padding: 40px 0"
      />
    </NScrollbar>
  </div>
</template>

<style scoped>
.structured-console {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.console-header {
  margin-bottom: var(--spacing-3);
  padding-bottom: var(--spacing-3);
  border-bottom: 1px solid var(--color-border-light);
}

.console-scroll {
  flex: 1;
  max-height: 400px;
}

.logs-container {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.log-entry {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2);
  border-radius: var(--radius-sm);
  margin-bottom: var(--spacing-1);
  background-color: var(--color-bg-tertiary);
}

.log-entry.log-type-error {
  background-color: var(--color-error-bg);
}

.log-entry.log-type-warning {
  background-color: var(--color-warning-bg);
}

.log-time {
  color: var(--color-text-tertiary);
  min-width: 80px;
}

.log-icon {
  font-size: var(--font-size-base);
}

.log-message {
  flex: 1;
  word-break: break-word;
}
</style>
