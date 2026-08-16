<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NCard, NEmpty, NSpin, NTag, NIcon, NText, NSpace, NButton, NCollapse, NCollapseItem, useMessage } from 'naive-ui'
import {
  TerminalOutline, PricetagOutline, AlertCircleOutline, CheckmarkCircleOutline,
  CogOutline, CodeOutline, DocumentTextOutline, BulbOutline, CloseCircleOutline
} from '@vicons/ionicons5'
import { taskDetailApi } from '@/api/axios'

interface Props {
  taskId: string
}
const props = defineProps<Props>()

const loading = ref(false)
const error = ref<string | null>(null)
const activity = ref<any>(null)
const message = useMessage()

// ─── Command folding state ─────────────────────────────────
const commandsExpanded = ref(false)

// "Important" commands: non-zero exit code or long duration (>10s)
const importantCommands = computed(() => {
  const cmds = activity.value?.commandsExecuted || []
  return cmds.filter((c: any) => c.exitCode !== 0 || (c.durationMs && c.durationMs > 10000))
})

const totalCommands = computed(() => activity.value?.commandsExecuted?.length ?? 0)
const totalFiles = computed(() => activity.value?.filesChanged?.length ?? 0)
const totalIncidents = computed(() => activity.value?.incidents?.length ?? 0)
const totalDeps = computed(() => activity.value?.dependenciesChanged?.length ?? 0)
const totalVerifications = computed(() => activity.value?.verifications?.length ?? 0)
const totalEnvChanges = computed(() => activity.value?.environmentChanges?.length ?? 0)
const totalDecisions = computed(() => activity.value?.agentDecisions?.length ?? 0)

// ─── Knowledge Candidate state ─────────────────────────────
interface KnowledgeCandidate {
  id: string
  type: 'ADR' | 'LESSON'
  title: string
  description: string
  dismissed: boolean
  saved: boolean
}

const knowledgeCandidates = ref<KnowledgeCandidate[]>([])

function generateKnowledgeCandidates() {
  const candidates: KnowledgeCandidate[] = []
  const acts = activity.value
  if (!acts) return

  // Incident resolved → potential lesson
  for (const inc of (acts.incidents || [])) {
    if (inc.resolved) {
      candidates.push({
        id: `kc-incident-${inc.type}`,
        type: 'LESSON',
        title: `Lesson: ${inc.type} resolved by ${inc.resolvedBy || 'agent'}`,
        description: inc.description || inc.type,
        dismissed: false,
        saved: false
      })
    }
  }

  // Dependency added → potential ADR
  const deps = acts.dependenciesChanged || []
  if (deps.length > 0) {
    const depNames = deps.map((d: any) => d.name).filter(Boolean).join(', ')
    candidates.push({
      id: 'kc-deps',
      type: 'ADR',
      title: `ADR: Dependency decision (${deps.length} change${deps.length > 1 ? 's' : ''})`,
      description: `Added/removed: ${depNames}`,
      dismissed: false,
      saved: false
    })
  }

  // Agent decision → potential ADR
  const decisions = acts.agentDecisions || []
  for (const d of decisions) {
    if (d.content && d.type === 'DECISION_MADE') {
      candidates.push({
        id: `kc-decision-${d.type}`,
        type: 'ADR',
        title: `ADR: ${d.content}`,
        description: d.content,
        dismissed: false,
        saved: false
      })
    }
  }

  // Verification failure → potential lesson
  const verifs = acts.verifications || []
  for (const v of verifs) {
    if (v.failed > 0) {
      candidates.push({
        id: `kc-verif-${v.type}`,
        type: 'LESSON',
        title: `Lesson: ${v.failed} test failure${v.failed > 1 ? 's' : ''} encountered`,
        description: `${v.type}: ${v.passed} passed, ${v.failed} failed`,
        dismissed: false,
        saved: false
      })
    }
  }

  knowledgeCandidates.value = candidates
}

function saveCandidate(c: KnowledgeCandidate) {
  c.saved = true
  message.success(`${c.type === 'ADR' ? 'ADR' : 'Lesson'} saved: ${c.title.substring(0, 40)}...`)
}

function dismissCandidate(c: KnowledgeCandidate) {
  c.dismissed = true
}

const activeCandidates = computed(() => knowledgeCandidates.value.filter(c => !c.dismissed && !c.saved))

// ─── Data loading ──────────────────────────────────────────
async function loadActivity() {
  if (!props.taskId) return
  try {
    loading.value = true
    const res = await taskDetailApi.getActivity(props.taskId)
    activity.value = (res as any).data || res
    error.value = null
    generateKnowledgeCandidates()
  } catch (e) {
    error.value = String(e)
    console.error('Failed to load activity:', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadActivity)
</script>

<template>
  <div v-if="!taskId" class="empty-hint">
    <NEmpty description="未选择任务，请在「实时执行」面板选择一个任务" />
  </div>
  <NSpin v-else :show="loading">
    <div v-if="error" class="error-box">
      <NIcon :component="AlertCircleOutline" color="#ef4444" />
      <NText style="color:#ef4444;">{{ error }}</NText>
    </div>
    <div v-else class="ledger">
      <!-- Summary Cards -->
      <div class="summary-row">
        <NCard size="small" class="summary-card">
          <NIcon :component="CodeOutline" color="#6366f1" :size="20" />
          <div class="summary-num">{{ totalCommands }}</div>
          <div class="summary-label">命令执行</div>
        </NCard>
        <NCard size="small" class="summary-card">
          <NIcon :component="PricetagOutline" color="#22c55e" :size="20" />
          <div class="summary-num">{{ totalFiles }}</div>
          <div class="summary-label">文件变更</div>
        </NCard>
        <NCard size="small" class="summary-card">
          <NIcon :component="AlertCircleOutline" color="#f59e0b" :size="20" />
          <div class="summary-num">{{ totalIncidents }}</div>
          <div class="summary-label">事件/问题</div>
        </NCard>
        <NCard size="small" class="summary-card">
          <NIcon :component="CheckmarkCircleOutline" color="#8b5cf6" :size="20" />
          <div class="summary-num">{{ totalVerifications }}</div>
          <div class="summary-label">验证结果</div>
        </NCard>
        <NCard size="small" class="summary-card">
          <NIcon :component="CogOutline" color="#06b6d4" :size="20" />
          <div class="summary-num">{{ totalEnvChanges }}</div>
          <div class="summary-label">环境变更</div>
        </NCard>
        <NCard size="small" class="summary-card">
          <NIcon :component="CogOutline" color="#ec4899" :size="20" />
          <div class="summary-num">{{ totalDecisions }}</div>
          <div class="summary-label">Agent 决策</div>
        </NCard>
      </div>

      <!-- Two-column layout: What Changed | Evidence -->
      <div class="ledger-columns">
        <!-- Left: What Changed -->
        <div class="ledger-col">
          <div class="col-header">
            <NIcon :component="DocumentTextOutline" color="#6366f1" :size="16" />
            <NText strong>What Changed</NText>
          </div>

          <!-- Commands (folded) -->
          <div v-if="totalCommands > 0" class="changed-section">
            <div class="changed-summary" @click="commandsExpanded = !commandsExpanded">
              <NIcon :component="TerminalOutline" color="#6366f1" :size="14" />
              <NText>{{ totalCommands }} commands executed</NText>
              <NTag v-if="importantCommands.length > 0" size="tiny" type="warning">
                {{ importantCommands.length }} important
              </NTag>
              <NText depth="3" style="font-size:11px;margin-left:auto;cursor:pointer;">
                {{ commandsExpanded ? '▾ collapse' : '▸ expand' }}
              </NText>
            </div>
            <!-- Folded: show only important commands -->
            <div v-if="!commandsExpanded && importantCommands.length > 0" class="cmd-list">
              <div v-for="(cmd, i) in importantCommands" :key="'imp-'+i" class="cmd-row">
                <NTag v-if="cmd.exitCode !== 0" size="tiny" type="error" class="exit-tag">exit {{ cmd.exitCode }}</NTag>
                <NTag v-else size="tiny" type="warning" class="exit-tag">slow</NTag>
                <NText class="cmd-text">{{ cmd.command }}</NText>
                <NText depth="3" class="cmd-duration">
                  {{ cmd.durationMs ? Math.round(cmd.durationMs / 1000) + 's' : '' }}
                </NText>
              </div>
            </div>
            <!-- Expanded: show all commands -->
            <div v-if="commandsExpanded" class="cmd-list">
              <div v-for="(cmd, i) in activity.commandsExecuted" :key="'all-'+i" class="cmd-row"
                   :class="{ 'cmd-important': cmd.exitCode !== 0 || (cmd.durationMs && cmd.durationMs > 10000) }">
                <NTag v-if="cmd.exitCode !== 0" size="tiny" type="error" class="exit-tag">exit {{ cmd.exitCode }}</NTag>
                <NTag v-else size="tiny" type="success" class="exit-tag">✓</NTag>
                <NText class="cmd-text">{{ cmd.command }}</NText>
                <NText depth="3" class="cmd-duration">
                  {{ cmd.durationMs ? Math.round(cmd.durationMs / 1000) + 's' : '' }}
                </NText>
              </div>
            </div>
          </div>

          <!-- Files Changed -->
          <div v-if="totalFiles > 0" class="changed-section">
            <div class="changed-summary">
              <NIcon :component="PricetagOutline" color="#22c55e" :size="14" />
              <NText>{{ totalFiles }} files changed</NText>
            </div>
            <div class="file-list">
              <NText v-for="(f, i) in activity.filesChanged" :key="i" class="file-tag">{{ f }}</NText>
            </div>
          </div>

          <!-- Dependencies -->
          <div v-if="totalDeps > 0" class="changed-section">
            <div class="changed-summary">
              <NIcon :component="CogOutline" color="#06b6d4" :size="14" />
              <NText>+{{ totalDeps }} dependency change{{ totalDeps > 1 ? 's' : '' }}</NText>
            </div>
            <div class="dep-list">
              <div v-for="(d, i) in activity.dependenciesChanged" :key="i" class="dep-row">
                <NTag :type="d.action === 'ADDED' ? 'success' : 'warning'" size="tiny">
                  {{ d.action === 'ADDED' ? '↑' : '↓' }}
                </NTag>
                <NText>{{ d.name }}</NText>
                <NText v-if="d.version" depth="3" style="font-size:12px;">@{{ d.version }}</NText>
              </div>
            </div>
          </div>

          <!-- Environment Changes -->
          <div v-if="totalEnvChanges > 0" class="changed-section">
            <div class="changed-summary">
              <NIcon :component="CogOutline" color="#f59e0b" :size="14" />
              <NText>{{ totalEnvChanges }} environment change{{ totalEnvChanges > 1 ? 's' : '' }}</NText>
            </div>
            <div class="env-list">
              <div v-for="(e, i) in activity.environmentChanges" :key="i" class="env-row">
                <NTag size="tiny" :type="e.action === 'ADDED' ? 'success' : e.action === 'REMOVED' ? 'warning' : 'info'">
                  {{ e.typeLabel }}
                </NTag>
                <NText>{{ e.name }}</NText>
                <NText v-if="e.detail" depth="3" style="font-size:12px;">{{ e.detail }}</NText>
              </div>
            </div>
          </div>

          <!-- Incidents -->
          <div v-if="totalIncidents > 0" class="changed-section">
            <div class="changed-summary">
              <NIcon :component="AlertCircleOutline" color="#f59e0b" :size="14" />
              <NText>{{ totalIncidents }} incident{{ totalIncidents > 1 ? 's' : '' }}</NText>
              <NTag v-if="activity.incidents.every((i: any) => i.resolved)" size="tiny" type="success">all resolved</NTag>
            </div>
            <div class="incident-list">
              <div v-for="(inc, i) in activity.incidents" :key="i" class="incident-card">
                <NText strong>{{ inc.type }}</NText>
                <NText depth="3" style="font-size:12px;">{{ inc.description }}</NText>
                <NSpace style="margin-top:4px">
                  <NTag v-if="inc.resolved" size="tiny" type="success">✅ {{ inc.resolvedBy || 'Resolved' }}</NTag>
                  <NTag v-else size="tiny" type="warning">⏳ unresolved</NTag>
                </NSpace>
              </div>
            </div>
          </div>
        </div>

        <!-- Right: Evidence -->
        <div class="ledger-col">
          <div class="col-header">
            <NIcon :component="CheckmarkCircleOutline" color="#8b5cf6" :size="16" />
            <NText strong>Evidence</NText>
          </div>

          <!-- Verifications -->
          <div v-if="totalVerifications > 0" class="evidence-section">
            <div v-for="(v, i) in activity.verifications" :key="i" class="evidence-item">
              <NIcon :component="CheckmarkCircleOutline" :color="v.failed > 0 ? '#f59e0b' : '#22c55e'" :size="14" />
              <div class="evidence-content">
                <NText>{{ v.type }}</NText>
                <NText depth="3" style="font-size:12px;">
                  {{ v.passed }} passed{{ v.failed > 0 ? ', ' + v.failed + ' failed' : '' }}
                </NText>
              </div>
            </div>
          </div>

          <!-- Agent Decisions -->
          <div v-if="totalDecisions > 0" class="evidence-section">
            <div class="evidence-subheader">
              <NIcon :component="CogOutline" color="#ec4899" :size="14" />
              <NText depth="2" style="font-size:12px;">Agent Decisions</NText>
            </div>
            <div v-for="(d, i) in activity.agentDecisions" :key="i" class="evidence-item">
              <NTag size="tiny" type="info">{{ d.type }}</NTag>
              <NText depth="2" style="font-size:12px;">{{ d.content }}</NText>
            </div>
          </div>

          <!-- No evidence -->
          <div v-if="totalVerifications === 0 && totalDecisions === 0" class="evidence-empty">
            <NText depth="3" style="font-size:12px;">No verification evidence yet</NText>
          </div>
        </div>
      </div>

      <!-- Knowledge Candidates -->
      <div v-if="activeCandidates.length > 0" class="knowledge-section">
        <div class="knowledge-header">
          <NIcon :component="BulbOutline" color="#f59e0b" :size="16" />
          <NText strong>Knowledge Candidates</NText>
          <NText depth="3" style="font-size:11px;">TeamMind detected patterns worth saving</NText>
        </div>
        <div v-for="kc in activeCandidates" :key="kc.id" class="kc-card">
          <div class="kc-info">
            <NTag size="tiny" :type="kc.type === 'ADR' ? 'info' : 'success'">{{ kc.type }}</NTag>
            <NText strong style="font-size:13px;">{{ kc.title }}</NText>
            <NText depth="3" style="font-size:12px;">{{ kc.description }}</NText>
          </div>
          <NSpace>
            <NButton size="tiny" type="primary" @click="saveCandidate(kc)">
              {{ kc.type === 'ADR' ? 'Create ADR' : 'Save Lesson' }}
            </NButton>
            <NButton size="tiny" quaternary @click="dismissCandidate(kc)">
              <template #icon><NIcon :component="CloseCircleOutline" /></template>
              Ignore
            </NButton>
          </NSpace>
        </div>
      </div>

      <!-- Empty state -->
      <NEmpty
        v-if="!loading && !error && totalCommands === 0 && totalFiles === 0 && totalIncidents === 0"
        description="暂无执行活动数据（该任务可能尚未产生事件记录）"
      />
    </div>
  </NSpin>
</template>

<style scoped>
.empty-hint { padding: 60px 0; display: flex; justify-content: center; }
.error-box { display: flex; align-items: center; gap: 8px; padding: 16px; color: #ef4444; }
.ledger { display: flex; flex-direction: column; gap: 12px; }

/* Summary cards */
.summary-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.summary-card { text-align: center; cursor: default; }
.summary-num { font-size: 24px; font-weight: 700; color: #e2e8f0; line-height: 1.2; }
.summary-label { font-size: 11px; color: #94a3b8; margin-top: 4px; }

/* Two-column layout */
.ledger-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.ledger-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.col-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #3a3a5c;
}

/* What Changed sections */
.changed-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.changed-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}

/* Commands */
.cmd-list { display: flex; flex-direction: column; gap: 2px; }
.cmd-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  font-size: 13px;
}
.cmd-important {
  background: #f59e0b11;
  border-radius: 4px;
  padding: 3px 6px;
}
.exit-tag { flex-shrink: 0; width: 28px; text-align: center; }
.cmd-text {
  font-family: monospace;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.cmd-duration { font-size: 11px; flex-shrink: 0; }

/* Files */
.file-list { display: flex; flex-wrap: wrap; gap: 6px; }
.file-tag {
  font-size: 12px;
  font-family: monospace;
  background: #2a2a3e;
  color: #94a3b8;
  padding: 2px 8px;
  border-radius: 4px;
}

/* Dependencies */
.dep-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; }

/* Environment */
.env-list { display: flex; flex-direction: column; gap: 2px; }
.env-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: 13px; }

/* Incidents */
.incident-list { display: flex; flex-direction: column; gap: 8px; }
.incident-card {
  background: #2a2a3e;
  border-radius: 8px;
  padding: 8px 12px;
}

/* Evidence column */
.evidence-section { display: flex; flex-direction: column; gap: 6px; }
.evidence-subheader {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}
.evidence-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 4px 0;
}
.evidence-content { display: flex; flex-direction: column; }
.evidence-empty { padding: 20px 0; text-align: center; }

/* Knowledge Candidates */
.knowledge-section {
  margin-top: 8px;
  border-top: 1px solid #3a3a5c;
  padding-top: 12px;
}
.knowledge-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.kc-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #1e1e2e;
  border: 1px solid #3a3a5c;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
}
.kc-card:last-child { margin-bottom: 0; }
.kc-info { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
</style>
