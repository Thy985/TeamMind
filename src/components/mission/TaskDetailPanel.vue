<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { NGrid, NGi, NCard, NButton, NSpace, NText, NTag, NIcon, NSpin, NDivider } from 'naive-ui'
import { PlayOutline, PauseOutline, RefreshOutline, CheckmarkCircleOutline, AlertCircleOutline, TimeOutline } from '@vicons/ionicons5'
import ReadinessBadge from './ReadinessBadge.vue'
import AgentActivityPanel from './AgentActivityPanel.vue'
import EvidencePanel from './EvidencePanel.vue'
import PolicyLogPanel from './PolicyLogPanel.vue'
import { taskDetailApi } from '@/api/axios'
import { wsManager } from '@/api'
import type { TaskDetailSnapshot, TaskStep, TaskArtifact, TaskEvidence, TaskApproval, TaskReadiness } from '@/types'

interface Props {
  taskId?: string
  objective?: string
}

const props = withDefaults(defineProps<Props>(), {
  taskId: 'task-1',
  objective: 'Implement authentication module with JWT'
})

// ─── State ──────────────────────────────────────────────────
const loading = ref(false)
const error = ref<string | null>(null)
const snapshot = ref<TaskDetailSnapshot | null>(null)
const snapshotVersion = ref(0)
const needsApproval = ref(false)

// ─── Load snapshot ──────────────────────────────────────────
async function loadSnapshot() {
  try {
    loading.value = true
    const res = await taskDetailApi.getTask(props.taskId)
    const data = (res as any).data || res
    snapshot.value = data
    snapshotVersion.value = data.snapshotVersion || 0
    // Derive needsApproval from pending approvals or state
    needsApproval.value =
      (data.pendingApprovals && data.pendingApprovals.length > 0) ||
      data.taskState === 'NEEDS_APPROVAL'
    error.value = null
  } catch (e) {
    error.value = String(e)
    console.error('Failed to load task detail:', e)
  } finally {
    loading.value = false
  }
}

// ─── Event replay for reconnect ─────────────────────────────
async function replayEvents(fromVersion: number) {
  if (fromVersion <= 0) return
  try {
    const res = await taskDetailApi.getEvents(props.taskId, fromVersion)
    const events = (res as any).data || res
    // Apply events to snapshot (simplified — in production use event sourcing service)
    console.log(`Replayed ${events.length} events`)
  } catch (e) {
    console.error('Event replay failed:', e)
  }
}

// ─── Control actions ────────────────────────────────────────
async function handleApprove() {
  try {
    await taskDetailApi.approve(props.taskId, { decision: 'approved' })
    needsApproval.value = false
    await loadSnapshot()
  } catch (e) {
    console.error('Approve failed:', e)
  }
}

async function handleDeny() {
  try {
    await taskDetailApi.approve(props.taskId, { decision: 'denied' })
    needsApproval.value = false
    await loadSnapshot()
  } catch (e) {
    console.error('Deny failed:', e)
  }
}

async function handleRetry() {
  try {
    loading.value = true
    await taskDetailApi.retry(props.taskId)
    await loadSnapshot()
  } finally {
    loading.value = false
  }
}

// ─── Derived data ───────────────────────────────────────────
const currentStep = computed(() => snapshot.value?.currentStep || 'implement')
const currentAgent = computed(() => snapshot.value?.agentId || 'codex')
const readinessState = computed(() => {
  const r = snapshot.value?.readiness?.[currentAgent.value] as TaskReadiness | undefined
  return r?.state || 'UNKNOWN'
})
const agentVersion = computed(() => {
  const r = snapshot.value?.readiness?.[currentAgent.value] as TaskReadiness | undefined
  return (r as any)?.version || (r as any)?.diagnosis || '—'
})
const providerEndpoint = computed(() => {
  const r = snapshot.value?.readiness?.[currentAgent.value] as TaskReadiness | undefined
  return (r as any)?.endpoint || '—'
})
const configStatus = computed(() => {
  const r = snapshot.value?.readiness?.[currentAgent.value] as TaskReadiness | undefined
  if (!r) return '—'
  return r.state === 'READY' ? 'OK' : r.state === 'DEGRADED' ? 'WARN' : 'FAIL'
})

const handoffHistory = computed(() => {
  const steps = snapshot.value?.steps || []
  return steps.slice(0, -1).map((s, i, arr) => ({
    from: arr[i]?.agentId || 'codex',
    to: arr[i + 1]?.agentId || 'claude-code',
    reason: arr[i + 1]?.stepName || 'review',
    time: arr[i + 1]?.completedAt || ''
  })).filter(h => h.from !== h.to)
})

const artifacts = computed<TaskArtifact[]>(() => snapshot.value?.artifacts || [])
const findings = computed(() => {
  // Derive from step output summaries and evidence
  const steps = snapshot.value?.steps || []
  return steps.flatMap(s => {
    const text = s.outputSummary || ''
    if (text.includes('CRITICAL') || text.includes('critical')) {
      return [{ id: s.id, severity: 'CRITICAL' as const, description: text, resolved: false }]
    }
    if (text.includes('HIGH') || text.includes('high')) {
      return [{ id: s.id, severity: 'HIGH' as const, description: text, resolved: false }]
    }
    return []
  }).slice(0, 5)
})

const approvalRequests = computed<TaskApproval[]>(() => snapshot.value?.pendingApprovals || [])

// ─── Pipeline progress ──────────────────────────────────────
const stepLabels = ['implement', 'review', 'verify']
const stepProgress = computed(() => {
  const idx = stepLabels.indexOf(currentStep.value)
  return idx >= 0 ? Math.round(((idx + 1) / stepLabels.length) * 100) : 0
})

const elapsed = computed(() => {
  if (!snapshot.value?.startedAt) return '--:--'
  const start = new Date(snapshot.value.startedAt).getTime()
  const now = Date.now()
  const diff = Math.floor((now - start) / 1000)
  const m = Math.floor(diff / 60)
  const s = diff % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

// ─── Routing decision (derived from snapshot) ───────────────
const routingDecision = computed(() => ({
  plugin: currentAgent.value,
  capability: snapshot.value?.currentStep || 'implementation',
  score: (snapshot.value as any)?.routingScore ?? '—',
  readiness: readinessState.value,
  reason: (snapshot.value as any)?.routingReason || `Routed to ${currentAgent.value} based on capability match + readiness gate`
}))

// ─── WebSocket integration ──────────────────────────────────

function connectWebSocket() {
  // Subscribe to task-specific events via proper wsManager
  wsManager.on('state_update', (event: any) => {
    const payload = event?.payload || event
    if (payload?.taskId === props.taskId || event?.missionId === props.taskId) {
      const snap = payload?.snapshot || payload
      if (snap && typeof snap === 'object') {
        snapshot.value = snap as TaskDetailSnapshot
        snapshotVersion.value = snap.snapshotVersion || 0
        needsApproval.value =
          (snap.pendingApprovals?.length || 0) > 0 ||
          snap.taskState === 'NEEDS_APPROVAL'
      }
    }
  })

  wsManager.on('log', (event: any) => {
    const payload = event?.payload || event
    if (event?.missionId === props.taskId || payload?.taskId === props.taskId) {
      // Log events handled by parent component or console for debugging
    }
  })

  wsManager.on('approval_required', (event: any) => {
    const payload = event?.payload || event
    if (payload?.taskId === props.taskId || event?.missionId === props.taskId) {
      needsApproval.value = true
      loadSnapshot()
    }
  })

  wsManager.on('pipeline_step_started', (event: any) => {
    const payload = event?.payload || event
    if (payload?.taskId === props.taskId || event?.missionId === props.taskId) {
      loadSnapshot()
    }
  })

  wsManager.on('pipeline_step_completed', (event: any) => {
    const payload = event?.payload || event
    if (payload?.taskId === props.taskId || event?.missionId === props.taskId) {
      loadSnapshot()
    }
  })
}

// ─── Lifecycle ──────────────────────────────────────────────
onMounted(async () => {
  await loadSnapshot()
  connectWebSocket()
  // Poll every 5s for live updates (WebSocket handles real-time)
  const timer = setInterval(loadSnapshot, 5000)
  onUnmounted(() => clearInterval(timer))
})

onUnmounted(() => {
  // wsManager handlers are cleaned up by disconnect in parent
})
</script>

<template>
  <NSpin :show="loading" size="large">
    <div v-if="error" class="task-error">
      <NIcon :component="AlertCircleOutline" color="#ef4444" />
      <NText style="color:#ef4444;">{{ error }}</NText>
    </div>

    <div v-else class="task-detail">
      <!-- ── Header ── -->
      <NCard class="task-header" :bordered="false">
        <div class="header-left">
          <div class="task-title">
            <NText strong style="font-size: var(--font-size-lg);">{{ objective }}</NText>
            <NTag size="small" :type="needsApproval ? 'warning' : 'success'">
              {{ snapshot?.taskState || 'UNKNOWN' }}
            </NTag>
          </div>
          <div class="task-meta">
            <NIcon :component="TimeOutline" size="14" depth="3" />
            <NText depth="3" style="font-size: var(--font-size-xs);">{{ elapsed }} elapsed</NText>
            <span class="divider">|</span>
            <NText depth="3" style="font-size: var(--font-size-xs);">Step {{ stepProgress }}%</NText>
            <span v-if="snapshot?.snapshotVersion" class="divider">|</span>
            <NText v-if="snapshot?.snapshotVersion" depth="3" style="font-size: var(--font-size-2xs);">
              v{{ snapshot.snapshotVersion }}
            </NText>
          </div>
        </div>

        <NSpace>
          <NButton
            v-if="needsApproval"
            type="primary"
            size="small"
            @click="handleApprove"
          >
            <template #icon><NIcon :component="CheckmarkCircleOutline" /></template>
            Approve
          </NButton>
          <NButton
            v-if="needsApproval"
            size="small"
            type="error"
            ghost
            @click="handleDeny"
          >
            Deny
          </NButton>
          <NButton size="small" @click="handleRetry" :loading="loading">
            <template #icon><NIcon :component="RefreshOutline" /></template>
            Retry
          </NButton>
        </NSpace>
      </NCard>

      <!-- ── 8-panel layout ── -->
      <NGrid :cols="24" :x-gap="12" :y-gap="12">

        <!-- Panel 1: Agent Readiness -->
        <NGi :span="8">
          <ReadinessBadge
            :agent-id="currentAgent"
            :agent-name="currentAgent === 'codex' ? 'Codex' : 'Claude Code'"
            :agent-version="agentVersion"
            :readiness-state="readinessState"
            :provider-endpoint="providerEndpoint"
            :config-status="configStatus"
          />
        </NGi>

        <!-- Panel 2: Routing Decision -->
        <NGi :span="8">
          <NCard size="small" class="routing-card">
            <template #header>
              <NText strong style="font-size: var(--font-size-xs);">Why this agent?</NText>
            </template>
            <div class="routing-info">
              <div class="routing-row">
                <NText depth="3" style="font-size: var(--font-size-2xs);">Capability</NText>
                <NTag size="tiny" type="info">{{ routingDecision.capability }}</NTag>
              </div>
              <div class="routing-row">
                <NText depth="3" style="font-size: var(--font-size-2xs);">Score</NText>
                <NText strong style="font-size: var(--font-size-base);color:var(--color-primary);">{{ routingDecision.score }}</NText>
              </div>
              <div class="routing-row">
                <NText depth="3" style="font-size: var(--font-size-2xs);">Readiness</NText>
                <NTag size="tiny" :color="readinessState === 'READY' ? '#22c55e' : '#f59e0b'">
                  {{ routingDecision.readiness }}
                </NTag>
              </div>
              <div class="routing-reason">
                <NText depth="3" style="font-size: var(--font-size-2xs);">{{ routingDecision.reason }}</NText>
              </div>
            </div>
          </NCard>
        </NGi>

        <!-- Panel 3: Step Progress -->
        <NGi :span="8">
          <NCard size="small">
            <template #header>
              <NText strong style="font-size: var(--font-size-xs);">Pipeline Progress</NText>
            </template>
            <div class="progress-steps">
              <div
                v-for="(step, idx) in stepLabels"
                :key="step"
                class="step-item"
                :class="{ active: step === currentStep, completed: stepLabels.indexOf(currentStep) > idx }"
              >
                <div class="step-dot">
                  <NIcon
                    v-if="stepLabels.indexOf(currentStep) > idx"
                    :component="CheckmarkCircleOutline"
                    size="12"
                    color="#22c55e"
                  />
                  <NIcon
                    v-else
                    :component="AlertCircleOutline"
                    size="12"
                    color="var(--color-primary)"
                  />
                </div>
                <NText style="font-size: var(--font-size-2xs);text-transform:capitalize;">{{ step }}</NText>
              </div>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: stepProgress + '%' }"></div>
            </div>
          </NCard>
        </NGi>

        <!-- Panel 4: Agent Activity -->
        <NGi :span="12">
          <AgentActivityPanel
            :current-agent="currentAgent"
            :current-step="currentStep"
            :handoff-history="handoffHistory"
          />
        </NGi>

        <!-- Panel 5: Artifacts -->
        <NGi :span="12">
          <EvidencePanel :artifacts="artifacts" />
        </NGi>

        <!-- Panel 6: Findings + Approval -->
        <NGi :span="16">
          <PolicyLogPanel
            :findings="findings"
            :approval-requests="approvalRequests"
            :needs-approval="needsApproval"
          />
        </NGi>

        <!-- Panel 7: Event Timeline -->
        <NGi :span="8">
          <NCard size="small" title="Recent Events">
            <div class="event-list">
              <div
                v-for="(evt, idx) in [...handoffHistory.map(h => ({time: h.time, msg: `${h.from} → ${h.to} (${h.reason})`})), {time:'--:--',msg:'Task started'}].reverse()"
                :key="idx"
                class="event-item"
              >
                <NText depth="3" style="font-size: var(--font-size-2xs);">{{ evt.time }}</NText>
                <NText style="font-size: var(--font-size-2xs);">{{ evt.msg }}</NText>
              </div>
              <div v-if="snapshot?.steps" class="event-item" v-for="s in snapshot.steps.slice(-3).reverse()" :key="s.id">
                <NText depth="3" style="font-size: var(--font-size-2xs);">{{ s.completedAt || s.startedAt || '--:--' }}</NText>
                <NText style="font-size: var(--font-size-2xs);">{{ s.stepName }} → {{ s.state }}</NText>
              </div>
            </div>
          </NCard>
        </NGi>

      </NGrid>
    </div>
  </NSpin>
</template>

<style scoped>
.task-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
}

.task-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.divider {
  color: var(--n-border-color);
}

.routing-card :deep(.n-card__header) {
  padding: 8px 12px;
}

.routing-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 0;
}

.routing-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.routing-reason {
  margin-top: 4px;
  padding: 6px 8px;
  background: var(--n-border-color);
  border-radius: var(--n-border-radius);
  font-size: var(--font-size-2xs);
}

.progress-steps {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.step-item.active .step-dot {
  background: var(--color-primary);
  border-radius: 50%;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-item.completed .step-dot {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.progress-bar {
  height: 4px;
  background: var(--n-border-color);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary), var(--color-purple));
  transition: width 0.3s ease;
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 160px;
  overflow-y: auto;
}

.event-item {
  display: flex;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid var(--n-border-color);
  font-size: var(--font-size-2xs);
}
</style>
