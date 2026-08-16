<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NGrid, NGi, NCard, NButton, NSpace, NText, NTag, NIcon, NSpin, NDivider } from 'naive-ui'
import { PlayOutline, PauseOutline, RefreshOutline, CheckmarkCircleOutline, AlertCircleOutline, TimeOutline } from '@vicons/ionicons5'
import ReadinessBadge from './ReadinessBadge.vue'
import AgentActivityPanel from './AgentActivityPanel.vue'
import EvidencePanel from './EvidencePanel.vue'
import PolicyLogPanel from './PolicyLogPanel.vue'

interface Props {
  taskId?: string
  objective?: string
}

const props = withDefaults(defineProps<Props>(), {
  taskId: 'task-1',
  objective: 'Implement authentication module with JWT'
})

// ─── Simulated data (would come from API in production) ───
const loading = ref(false)
const needsApproval = ref(false)

const currentAgent = ref('codex')
const currentStep = ref('implement')
const readinessState = ref('READY')
const agentVersion = ref('0.144.5')
const providerEndpoint = ref('127.0.0.1:57321')
const configStatus = ref('OK')

const handoffHistory = ref([
  { from: 'codex', to: 'claude-code', reason: 'review', time: '10:32:15' },
  { from: 'claude-code', to: 'codex', reason: 'fix', time: '10:35:42' }
])

const artifacts = ref([
  { id: 'art-1', type: 'CODE_DIFF', summary: 'Implemented auth controller with JWT', filesChanged: 3, linesAdded: 142, createdAt: '2026-01-15T10:30:00Z' },
  { id: 'art-2', type: 'REVIEW_FINDINGS', summary: '2 findings: 1 critical, 1 high', filesChanged: 0, linesAdded: 0, createdAt: '2026-01-15T10:33:00Z' }
])

const findings = ref([
  { id: 'f-1', severity: 'CRITICAL', description: 'Hardcoded secret in config.toml', resolved: false },
  { id: 'f-2', severity: 'HIGH', description: 'Missing input validation on /auth endpoint', resolved: false },
  { id: 'f-3', severity: 'MEDIUM', description: 'Token expiry not enforced in tests', resolved: true }
])

const approvalRequests = ref([
  { id: 'apr-1', requestedBy: 'claude-code', reason: 'Need approval to write to src/main/', granted: false, timestamp: '2026-01-15T10:34:00Z' }
])

const routingDecision = ref({
  plugin: 'codex',
  capability: 'implementation',
  score: 0.85,
  readiness: 'READY',
  reason: 'Best match for implementation capability'
})

// ─── Control actions ───
async function handleApprove() {
  needsApproval.value = false
  console.log('Approved')
}

async function handleDeny() {
  needsApproval.value = false
  console.log('Denied')
}

async function handleRetry() {
  loading.value = true
  setTimeout(() => { loading.value = false }, 1000)
}

// ─── Computed ───
const elapsed = computed(() => '00:05:42')
const stepProgress = computed(() => {
  const steps = ['implement', 'review', 'verify']
  const idx = steps.indexOf(currentStep.value)
  return Math.round(((idx + 1) / steps.length) * 100)
})
</script>

<template>
  <div class="task-detail">
    <!-- ── Header: 6 questions summary ── -->
    <NCard class="task-header" :bordered="false">
      <div class="header-left">
        <div class="task-title">
          <NText strong style="font-size:16px;">{{ objective }}</NText>
          <NTag size="small" :type="needsApproval ? 'warning' : 'success'">
            {{ needsApproval ? 'NEEDS_APPROVAL' : 'EXECUTING' }}
          </NTag>
        </div>
        <div class="task-meta">
          <NIcon :component="TimeOutline" size="14" depth="3" />
          <NText depth="3" style="font-size:12px;">{{ elapsed }} elapsed</NText>
          <span class="divider">|</span>
          <NText depth="3" style="font-size:12px;">Step {{ stepProgress }}%</NText>
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

      <!-- Panel 1: Agent Readiness (top-left) -->
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

      <!-- Panel 2: Routing Decision (top-center) -->
      <NGi :span="8">
        <NCard size="small" class="routing-card">
          <template #header>
            <NText strong style="font-size:12px;">Why this agent?</NText>
          </template>
          <div class="routing-info">
            <div class="routing-row">
              <NText depth="3" style="font-size:11px;">Capability</NText>
              <NTag size="tiny" type="info">{{ routingDecision.capability }}</NTag>
            </div>
            <div class="routing-row">
              <NText depth="3" style="font-size:11px;">Score</NText>
              <NText strong style="font-size:14px;color:#6366f1;">{{ routingDecision.score }}</NText>
            </div>
            <div class="routing-row">
              <NText depth="3" style="font-size:11px;">Readiness</NText>
              <NTag size="tiny" :color="readinessState === 'READY' ? '#22c55e' : '#f59e0b'">
                {{ routingDecision.readiness }}
              </NTag>
            </div>
            <div class="routing-reason">
              <NText depth="3" style="font-size:11px;">{{ routingDecision.reason }}</NText>
            </div>
          </div>
        </NCard>
      </NGi>

      <!-- Panel 3: Step Progress (top-right) -->
      <NGi :span="8">
        <NCard size="small">
          <template #header>
            <NText strong style="font-size:12px;">Pipeline Progress</NText>
          </template>
          <div class="progress-steps">
            <div
              v-for="(step, idx) in ['implement', 'review', 'verify']"
              :key="step"
              class="step-item"
              :class="{ active: step === currentStep, completed: ['implement', 'review'].includes(step) && step !== currentStep }"
            >
              <div class="step-dot">
                <NIcon
                  v-if="['implement', 'review'].includes(step) && step !== currentStep"
                  :component="CheckmarkCircleOutline"
                  size="12"
                  color="#22c55e"
                />
                <NIcon
                  v-else
                  :component="AlertCircleOutline"
                  size="12"
                  color="#6366f1"
                />
              </div>
              <NText style="font-size:11px;text-transform:capitalize;">{{ step }}</NText>
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
            <div v-for="(evt, idx) in [...handoffHistory, {time:'10:30:00',msg:'Task started'}].reverse()" :key="idx" class="event-item">
              <NText depth="3" style="font-size:11px;">{{ evt.time }}</NText>
              <NText style="font-size:11px;">{{ evt.msg || `${evt.from} → ${evt.to} (${evt.reason})` }}</NText>
            </div>
          </div>
        </NCard>
      </NGi>

    </NGrid>
  </div>
</template>

<style scoped>
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
  font-size: 11px;
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
  background: #6366f1;
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
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
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
  font-size: 11px;
}
</style>
