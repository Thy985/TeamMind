<script setup lang="ts">
import { computed } from 'vue'
import { NModal, NTabs, NTabPane, NDescriptions, NDescriptionsItem, NButton, NSpace, NIcon, NCode, NText } from 'naive-ui'
import { RefreshOutline, PlaySkipForwardOutline } from '@vicons/ionicons5'
import { useMissionStore } from '@/stores'

const props = defineProps<{
  show: boolean
  nodeId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
}>()

const missionStore = useMissionStore()

// Get node data
const node = computed(() => {
  if (!props.nodeId) return null
  return missionStore.nodes.find(n => n.id === props.nodeId)
})

// Get node logs
const nodeLogs = computed(() => {
  if (!props.nodeId) return []
  return missionStore.logs.filter(log => log.agentId === props.nodeId)
})

// Actions
function handleRetry() {
  if (props.nodeId) {
    console.log('Retry node:', props.nodeId)
  }
}

function handleSkip() {
  if (props.nodeId) {
    console.log('Skip node:', props.nodeId)
  }
  emit('update:show', false)
}
</script>

<template>
  <NModal
    :show="show"
    @update:show="emit('update:show', $event)"
    preset="card"
    :title="node?.data.label || 'Agent Details'"
    style="width: 600px; max-width: 90vw"
  >
    <template v-if="node">
      <NTabs type="line" animated>
        <!-- Overview -->
        <NTabPane name="overview" tab="Overview">
          <NDescriptions label-placement="left" :column="1" bordered>
            <NDescriptionsItem label="Status">
              {{ node.data.status }}
            </NDescriptionsItem>
            <NDescriptionsItem label="Agent">
              {{ node.data.agent?.name || '-' }}
            </NDescriptionsItem>
            <NDescriptionsItem label="Position">
              x: {{ node.position.x.toFixed(0) }}, y: {{ node.position.y.toFixed(0) }}
            </NDescriptionsItem>
          </NDescriptions>
        </NTabPane>

        <!-- Input -->
        <NTabPane name="input" tab="Input">
          <NCode 
            :code="JSON.stringify(node.data.input || {}, null, 2)"
            language="json"
            :show-line-numbers="true"
          />
        </NTabPane>

        <!-- Output -->
        <NTabPane name="output" tab="Output">
          <NCode 
            :code="JSON.stringify(node.data.output || {}, null, 2)"
            language="json"
            :show-line-numbers="true"
          />
        </NTabPane>

        <!-- Logs -->
        <NTabPane name="logs" tab="Logs">
          <div v-if="nodeLogs.length > 0" class="logs-list">
            <div v-for="log in nodeLogs" :key="log.id" class="log-item">
              <NText depth="3">{{ new Date(log.timestamp).toLocaleTimeString() }}</NText>
              <span>{{ log.message }}</span>
            </div>
          </div>
          <NText v-else depth="3">No logs available</NText>
        </NTabPane>
      </NTabs>
    </template>

    <NText v-else depth="3">No node selected</NText>

    <!-- Actions -->
    <template #footer>
      <NSpace justify="end">
        <NButton @click="handleRetry">
          <template #icon><NIcon><RefreshOutline /></NIcon></template>
          Retry
        </NButton>
        <NButton type="warning" @click="handleSkip">
          <template #icon><NIcon><PlaySkipForwardOutline /></NIcon></template>
          Skip
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.logs-list {
  max-height: 300px;
  overflow-y: auto;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.log-item {
  display: flex;
  gap: var(--spacing-3);
  padding: var(--spacing-2);
  border-bottom: 1px solid var(--color-border-light);
}

.log-item:last-child {
  border-bottom: none;
}
</style>
