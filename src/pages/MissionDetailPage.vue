<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NGrid, NGi, NCard, NButton, NSpace, NText, NIcon, NTooltip, NTabs, NTabPane, NSpin, NTag, useMessage } from 'naive-ui'
import { PlayOutline, PauseOutline, RefreshOutline, DownloadOutline, TrashOutline } from '@vicons/ionicons5'
import CollaborationCanvas from '@/components/canvas/CollaborationCanvas.vue'
import StructuredConsole from '@/components/common/StructuredConsole.vue'
import AgentDetailModal from '@/components/common/AgentDetailModal.vue'
import { useMissionStore } from '@/stores'
import { wsManager } from '@/api'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const missionStore = useMissionStore()

const missionId = computed(() => route.params.id as string)
const isNewMission = computed(() => route.name === 'mission-new')

// State
const selectedNodeId = ref<string | null>(null)
const showDetailModal = ref(false)
const isRunning = computed(() => missionStore.currentMission?.status === 'running')
const isLoading = ref(false)

// Current mission info
const missionTitle = computed(() => missionStore.currentMission?.title || 'New Mission')
const missionDescription = computed(() => missionStore.currentMission?.description || '')

// Handle node click
function handleNodeClick(nodeId: string) {
  selectedNodeId.value = nodeId
  showDetailModal.value = true
}

// Mission controls
async function handleStart() {
  if (!missionId.value) return
  isLoading.value = true
  try {
    await missionStore.startMission(missionId.value)
    message.success('Mission started')
  } catch (e) {
    message.error('Failed to start mission')
  } finally {
    isLoading.value = false
  }
}

async function handlePause() {
  if (!missionId.value) return
  isLoading.value = true
  try {
    await missionStore.pauseMission(missionId.value)
    message.success('Mission paused')
  } catch (e) {
    message.error('Failed to pause mission')
  } finally {
    isLoading.value = false
  }
}

async function handleRetry() {
  // Retry all failed nodes
  const failedNodes = missionStore.nodes.filter(n => n.data.status === 'error')
  if (failedNodes.length === 0) {
    message.info('No failed nodes to retry')
    return
  }
  
  isLoading.value = true
  try {
    for (const node of failedNodes) {
      await missionStore.retryNode(missionId.value, node.id)
    }
    message.success('Retrying failed nodes')
  } catch (e) {
    message.error('Failed to retry nodes')
  } finally {
    isLoading.value = false
  }
}

function handleExport() {
  const data = {
    mission: missionStore.currentMission,
    nodes: missionStore.nodes,
    edges: missionStore.edges,
    logs: missionStore.logs
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `mission-${missionId.value}.json`
  a.click()
  URL.revokeObjectURL(url)
  message.success('Mission exported')
}

async function handleDelete() {
  if (!missionId.value) return
  isLoading.value = true
  try {
    await missionStore.deleteMission(missionId.value)
    message.success('Mission deleted')
    router.push({ name: 'dashboard' })
  } catch (e) {
    message.error('Failed to delete mission')
  } finally {
    isLoading.value = false
  }
}

// Calculate duration
const duration = computed(() => {
  const mission = missionStore.currentMission
  if (!mission || !mission.createdAt) return '--:--:--'
  
  const start = new Date(mission.createdAt).getTime()
  const end = mission.status === 'completed' && mission.updatedAt 
    ? new Date(mission.updatedAt).getTime() 
    : Date.now()
  
  const diffMs = end - start
  const hours = Math.floor(diffMs / 3600000)
  const mins = Math.floor((diffMs % 3600000) / 60000)
  const secs = Math.floor((diffMs % 60000) / 1000)
  
  return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
})

// Load mission data
onMounted(async () => {
  if (!isNewMission.value && missionId.value) {
    await missionStore.fetchMission(missionId.value)
    wsManager.connect()
    wsManager.on('log', (event: any) => {
      missionStore.addLog({
        id: `log-${Date.now()}`,
        type: 'task',
        timestamp: event.timestamp || new Date().toISOString(),
        message: String(event.payload?.message || '')
      })
    })
  }
})

onUnmounted(() => {
  if (!isNewMission.value && missionId.value) {
    wsManager.disconnect()
  }
  missionStore.$reset()
})
</script>

<template>
  <div class="mission-detail-page">
    <!-- Header -->
    <div class="mission-header">
      <div class="mission-info">
        <h1>{{ isNewMission ? 'New Mission' : missionTitle }}</h1>
        <NText depth="3">{{ isNewMission ? 'Configure and start your mission' : missionDescription || 'Real-time collaboration view' }}</NText>
      </div>
      
      <NSpace>
        <NSpin :show="isLoading" size="small" />
        <NButton v-if="!isRunning" type="primary" @click="handleStart" :disabled="isLoading">
          <template #icon><NIcon><PlayOutline /></NIcon></template>
          Start
        </NButton>
        <NButton v-else @click="handlePause" :disabled="isLoading">
          <template #icon><NIcon><PauseOutline /></NIcon></template>
          Pause
        </NButton>
        <NTooltip>
          <template #trigger>
            <NButton @click="handleRetry" :disabled="isLoading">
              <template #icon><NIcon><RefreshOutline /></NIcon></template>
            </NButton>
          </template>
          Retry Failed Nodes
        </NTooltip>
        <NTooltip>
          <template #trigger>
            <NButton @click="handleExport" :disabled="isLoading">
              <template #icon><NIcon><DownloadOutline /></NIcon></template>
            </NButton>
          </template>
          Export JSON
        </NTooltip>
        <NTooltip v-if="!isNewMission">
          <template #trigger>
            <NButton type="error" @click="handleDelete" :disabled="isLoading">
              <template #icon><NIcon><TrashOutline /></NIcon></template>
            </NButton>
          </template>
          Delete Mission
        </NTooltip>
      </NSpace>
    </div>

    <!-- Main Content -->
    <NGrid :cols="24" :x-gap="16">
      <!-- Canvas -->
      <NGi :span="16">
        <NCard class="canvas-card" title="Collaboration Canvas">
          <CollaborationCanvas 
            :mission-id="missionId"
            @node-click="handleNodeClick"
          />
        </NCard>
      </NGi>

      <!-- Side Panel -->
      <NGi :span="8">
        <NCard class="side-panel">
          <NTabs type="line" animated>
            <NTabPane name="console" tab="Console">
              <StructuredConsole :logs="missionStore.logs" />
            </NTabPane>
            <NTabPane name="details" tab="Details">
              <div class="mission-details">
                <h4>Status</h4>
                <NTag 
                  :type="missionStore.missionStatus === 'completed' ? 'success' : 
                         missionStore.missionStatus === 'running' ? 'info' : 
                         missionStore.missionStatus === 'failed' ? 'error' : 'default'"
                >
                  {{ missionStore.missionStatus }}
                </NTag>
                
                <h4>Nodes</h4>
                <p>{{ missionStore.nodes.length }} agents</p>
                
                <h4>Duration</h4>
                <p>{{ duration }}</p>
                
                <h4 v-if="missionStore.currentMission?.createdAt">Created</h4>
                <p v-if="missionStore.currentMission?.createdAt">
                  {{ new Date(missionStore.currentMission.createdAt).toLocaleString() }}
                </p>
              </div>
            </NTabPane>
            <NTabPane name="resolution" tab="Resolutions">
              <div v-if="missionStore.logsByType.resolution.length === 0">
                <NText depth="3">No resolutions yet</NText>
              </div>
              <div v-else class="resolutions-list">
                <div 
                  v-for="(log, idx) in missionStore.logsByType.resolution" 
                  :key="idx"
                  class="resolution-item"
                >
                  <NText>{{ log.message }}</NText>
                </div>
              </div>
            </NTabPane>
          </NTabs>
        </NCard>
      </NGi>
    </NGrid>

    <!-- Agent Detail Modal -->
    <AgentDetailModal 
      v-model:show="showDetailModal"
      :node-id="selectedNodeId"
    />
  </div>
</template>

<style scoped>
.mission-detail-page {
  height: calc(100vh - var(--header-height) - var(--spacing-12));
  display: flex;
  flex-direction: column;
}

.mission-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-4);
}

.mission-info h1 {
  margin-bottom: var(--spacing-1);
}

.canvas-card {
  height: 100%;
}

.side-panel {
  height: 100%;
  overflow: auto;
}

.mission-details h4 {
  margin-top: var(--spacing-4);
  margin-bottom: var(--spacing-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.mission-details p {
  font-size: var(--font-size-lg);
}

.resolutions-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.resolution-item {
  padding: var(--spacing-2);
  background-color: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
}
</style>
