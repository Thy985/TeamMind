<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NGrid, NGi, NCard, NButton, NSpace, NText, NIcon, NTooltip, NTabs, NTabPane, NSpin, NTag, useMessage } from 'naive-ui'
import { PlayOutline, PauseOutline, RefreshOutline, DownloadOutline, TrashOutline, CloseOutline, DocumentTextOutline, AlertCircleOutline } from '@vicons/ionicons5'
import CollaborationCanvas from '@/components/canvas/CollaborationCanvas.vue'
import StructuredConsole from '@/components/common/StructuredConsole.vue'
import AgentDetailModal from '@/components/common/AgentDetailModal.vue'
import ActivityLedgerPanel from '@/components/mission/ActivityLedgerPanel.vue'
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
const activeTab = ref('canvas')
const pipelineStepName = ref<string | null>(null)
const pipelineStepAgent = ref<string | null>(null)
const showApprovalBanner = ref(false)
const isRunning = computed(() => missionStore.currentMission?.status === 'running' || missionStore.currentMission?.status === 'paused')
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

async function handleResume() {
  if (!missionId.value) return
  isLoading.value = true
  try {
    await missionStore.resumeMission(missionId.value)
    message.success('Mission resumed')
  } catch (e) {
    message.error('Failed to resume mission')
  } finally {
    isLoading.value = false
  }
}

async function handleCancel() {
  if (!missionId.value) return
  isLoading.value = true
  try {
    await missionStore.cancelMission(missionId.value)
    message.success('Mission cancelled')
  } catch (e) {
    message.error('Failed to cancel mission')
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
    // 携带 missionId 连接，自动订阅 /topic/events 与 /topic/missions/{id}
    wsManager.connect(missionId.value)

    // 实时日志事件
    wsManager.on('log', (event: any) => {
      const payload = event.payload || event.data || {}
      missionStore.addLog({
        id: `log-${Date.now()}`,
        type: (payload.type as any) || 'task',
        timestamp: event.timestamp || new Date().toISOString(),
        agentId: payload.agentId,
        message: String(payload.message || '')
      })
    })

    // 节点状态更新事件 —— 驱动实时画布
    wsManager.on('node_update', (event: any) => {
      const payload = event.payload || event.data || {}
      const nodeId = String(payload.nodeId || '')
      const status = String((payload.data as any)?.status || payload.status || '')
      if (!nodeId || !status) return
      const node = missionStore.nodes.find(n => n.id === nodeId)
      if (node) {
        node.data.status = status as any
        missionStore.currentMission!.updatedAt = event.timestamp || new Date().toISOString()
      }
    })

    // 任务完成事件
    wsManager.on('mission_completed', () => {
      missionStore.fetchMission(missionId.value!)
    })

    // 任务失败事件
    wsManager.on('mission_failed', () => {
      missionStore.fetchMission(missionId.value!)
    })

    // Agent 状态更新事件
    wsManager.on('agent_status_update', (event: any) => {
      const payload = event.payload || event.data || {}
      const agentId = String(payload.agentId || '')
      const status = String(payload.status || '')
      const node = missionStore.nodes.find(n => n.data.agent?.id === agentId)
      if (node && status) {
        node.data.status = status as any
      }
    })

    // Pipeline 步骤开始 — 显示当前步骤进度
    wsManager.on('pipeline_step_started', (event: any) => {
      const payload = event.payload || event.data || {}
      pipelineStepName.value = payload.stepName || null
      pipelineStepAgent.value = payload.agentId || null
      missionStore.addLog({
        id: `log-${Date.now()}`,
        type: 'task',
        timestamp: event.timestamp || new Date().toISOString(),
        agentId: payload.agentId,
        message: `Step started: ${payload.stepName || 'unknown'}`
      })
    })

    // Pipeline 步骤完成 — 刷新 mission 数据
    wsManager.on('pipeline_step_completed', (event: any) => {
      const payload = event.payload || event.data || {}
      pipelineStepName.value = null
      pipelineStepAgent.value = null
      missionStore.addLog({
        id: `log-${Date.now()}`,
        type: payload.success ? 'task' : 'warning',
        timestamp: event.timestamp || new Date().toISOString(),
        agentId: payload.agentId,
        message: `Step ${payload.success ? 'completed' : 'failed'}: ${payload.stepName || 'unknown'}`
      })
      // Refresh mission data to reflect updated state
      missionStore.fetchMission(missionId.value!)
    })

    // 审批请求 — 显示审批横幅
    wsManager.on('approval_required', (event: any) => {
      const payload = event.payload || event.data || {}
      if (payload.taskId === missionId.value || event.missionId === missionId.value) {
        showApprovalBanner.value = true
        missionStore.addLog({
          id: `log-${Date.now()}`,
          type: 'resolution',
          timestamp: event.timestamp || new Date().toISOString(),
          message: `Approval required: ${payload.approval?.question || 'pending action'}`
        })
      }
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
        <NButton v-if="missionStore.currentMission?.status === 'paused'" type="primary" @click="handleResume" :disabled="isLoading">
          <template #icon><NIcon><PlayOutline /></NIcon></template>
          Resume
        </NButton>
        <NButton v-else-if="isRunning" @click="handlePause" :disabled="isLoading">
          <template #icon><NIcon><PauseOutline /></NIcon></template>
          Pause
        </NButton>
        <NButton v-else type="primary" @click="handleStart" :disabled="isLoading">
          <template #icon><NIcon><PlayOutline /></NIcon></template>
          Start
        </NButton>
        <NTooltip v-if="isRunning">
          <template #trigger>
            <NButton type="error" ghost @click="handleCancel" :disabled="isLoading">
              <template #icon><NIcon><CloseOutline /></NIcon></template>
            </NButton>
          </template>
          Cancel Mission
        </NTooltip>
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

    <!-- Pipeline Step Banner -->
    <div v-if="pipelineStepName" class="pipeline-banner">
      <NSpin size="small" />
      <NText depth="2" style="font-size: var(--font-size-sm);">
        {{ pipelineStepAgent }} executing: <strong>{{ pipelineStepName }}</strong>
      </NText>
    </div>

    <!-- Approval Banner -->
    <div v-if="showApprovalBanner" class="approval-banner">
      <NIcon :component="AlertCircleOutline" color="var(--color-warning)" />
      <NText style="font-size: var(--font-size-sm);">Approval required — check Task Detail for pending actions</NText>
      <NButton size="tiny" @click="showApprovalBanner = false">Dismiss</NButton>
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

    <!-- Execution Ledger Section -->
    <NCard class="ledger-card" v-if="!isNewMission">
      <template #header>
        <NSpace align="center">
          <NIcon :component="DocumentTextOutline" color="var(--color-primary)" />
          <NText strong>Execution Ledger</NText>
          <NText depth="3" style="font-size: var(--font-size-xs);">Activity summary from runtime events</NText>
        </NSpace>
      </template>
      <ActivityLedgerPanel :task-id="missionId" />
    </NCard>

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

.pipeline-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--color-primary)22;
  border: 1px solid var(--color-primary)44;
  border-radius: 8px;
  margin-bottom: var(--spacing-3);
}

.approval-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--color-warning)22;
  border: 1px solid var(--color-warning)44;
  border-radius: 8px;
  margin-bottom: var(--spacing-3);
}

.ledger-card {
  margin-top: var(--spacing-4);
}

.ledger-card :deep(.n-card__content) {
  padding: 16px;
}
</style>
