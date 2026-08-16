<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NEmpty, NSpin, NTag, NIcon, NDivider, NText, NP } from 'naive-ui'
import { TerminalOutline, PricetagOutline, AlertCircleOutline, CheckmarkCircleOutline, CogOutline, CodeOutline } from '@vicons/ionicons5'
import { taskDetailApi } from '@/api/axios'

interface Props {
  taskId: string
}
const props = defineProps<Props>()

const loading = ref(false)
const error = ref<string | null>(null)
const activity = ref<any>(null)

async function loadActivity() {
  if (!props.taskId) return
  try {
    loading.value = true
    const res = await taskDetailApi.getActivity(props.taskId)
    activity.value = (res as any).data || res
    error.value = null
  } catch (e) {
    error.value = String(e)
    console.error('Failed to load activity:', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadActivity)

// Computed summaries
const totalCommands = ref(0)
const totalFiles = ref(0)
const totalIncidents = ref(0)
const totalDeps = ref(0)
const totalVerifications = ref(0)

function updateCounts() {
  if (!activity.value) return
  totalCommands.value = (activity.value.commandsExecuted || []).length
  totalFiles.value = (activity.value.filesChanged || []).length
  totalIncidents.value = (activity.value.incidents || []).length
  totalDeps.value = (activity.value.dependenciesChanged || []).length
  totalVerifications.value = (activity.value.verifications || []).length
}
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
          <div class="summary-num">{{ activity?.agentDecisions?.length ?? 0 }}</div>
          <div class="summary-label">Agent 决策</div>
        </NCard>
      </div>

      <!-- Commands Executed -->
      <NCard size="small" class="section-card" v-if="activity?.commandsExecuted?.length">
        <template #header>
          <NSpace>
            <NIcon :component="TerminalOutline" color="#6366f1" />
            <span>命令执行 ({{ activity.commandsExecuted.length }})</span>
          </NSpace>
        </template>
        <div class="cmd-list">
          <div v-for="(cmd, i) in activity.commandsExecuted" :key="i" class="cmd-row">
            <NTag v-if="cmd.exitCode !== 0" size="tiny" type="error" class="exit-tag">exit {{ cmd.exitCode }}</NTag>
            <NTag v-else size="tiny" type="success" class="exit-tag">✓</NTag>
            <NText style="font-family:monospace;font-size:13px;">{{ cmd.command }}</NText>
            <NText depth="3" style="font-size:11px;margin-left:auto;">
              {{ cmd.durationMs ? Math.round(cmd.durationMs / 1000) + 's' : '' }}
            </NText>
          </div>
        </div>
      </NCard>

      <!-- Files Changed -->
      <NCard size="small" class="section-card" v-if="activity?.filesChanged?.length">
        <template #header>
          <NSpace>
            <NIcon :component="PricetagOutline" color="#22c55e" />
            <span>文件变更 ({{ activity.filesChanged.length }})</span>
          </NSpace>
        </template>
        <div class="file-list">
          <NText v-for="(f, i) in activity.filesChanged" :key="i" class="file-tag">
            {{ f }}
          </NText>
        </div>
      </NCard>

      <!-- Dependencies Changed -->
      <NCard size="small" class="section-card" v-if="activity?.dependenciesChanged?.length">
        <template #header>
          <NSpace>
            <NIcon :component="BrainOutline" color="#06b6d4" />
            <span>依赖变更 ({{ activity.dependenciesChanged.length }})</span>
          </NSpace>
        </template>
        <div class="dep-list">
          <div v-for="(d, i) in activity.dependenciesChanged" :key="i" class="dep-row">
            <NTag :type="d.action === 'ADDED' ? 'success' : 'warning'" size="tiny">
              {{ d.action === 'ADDED' ? '↑ 新增' : '↓ 移除' }}
            </NTag>
            <NText>{{ d.name }}</NText>
            <NText v-if="d.version" depth="3" style="font-size:12px;">@{{ d.version }}</NText>
          </div>
        </div>
      </NCard>

      <!-- Incidents -->
      <NCard size="small" class="section-card" v-if="activity?.incidents?.length">
        <template #header>
          <NSpace>
            <NIcon :component="AlertCircleOutline" color="#f59e0b" />
            <span>Incident / Resolution ({{ activity.incidents.length }})</span>
          </NSpace>
        </template>
        <div class="incident-list">
          <div v-for="(inc, i) in activity.incidents" :key="i" class="incident-card">
            <NText strong>{{ inc.type }}</NText>
            <NText depth="3" style="font-size:12px;">{{ inc.description }}</NText>
            <NSpace style="margin-top:4px">
              <NTag v-if="inc.resolved" size="tiny" type="success">✅ {{ inc.resolvedBy || 'Resolved' }}</NTag>
              <NTag v-else size="tiny" type="warning">⏳ 未解决</NTag>
            </NSpace>
          </div>
        </div>
      </NCard>

      <!-- Verifications -->
      <NCard size="small" class="section-card" v-if="activity?.verifications?.length">
        <template #header>
          <NSpace>
            <NIcon :component="CheckmarkCircleOutline" color="#8b5cf6" />
            <span>验证结果</span>
          </NSpace>
        </template>
        <div class="verif-list">
          <div v-for="(v, i) in activity.verifications" :key="i" class="verif-row">
            <NTag :type="v.failed > 0 ? 'warning' : 'success'" size="tiny">{{ v.type }}</NTag>
            <NText>✓ {{ v.passed }} passed</NText>
            <NText v-if="v.failed > 0" depth="3">✗ {{ v.failed }} failed</NText>
          </div>
        </div>
      </NCard>

      <!-- Agent Decisions -->
      <NCard size="small" class="section-card" v-if="activity?.agentDecisions?.length">
        <template #header>
          <NSpace>
            <NIcon :component="BrainOutline" color="#06b6d4" />
            <span>Agent 决策 ({{ activity.agentDecisions.length }})</span>
          </NSpace>
        </template>
        <div class="decision-list">
          <div v-for="(d, i) in activity.agentDecisions" :key="i" class="decision-row">
            <NTag size="tiny" type="info">{{ d.type }}</NTag>
            <NText depth="2">{{ d.content }}</NText>
          </div>
        </div>
      </NCard>

      <!-- Empty state when no data -->
      <NEmpty v-if="!loading && !error && !activity?.commandsExecuted?.length && !activity?.filesChanged?.length"
              description="暂无执行活动数据（该任务可能尚未产生事件记录）" />
    </div>
  </NSpin>
</template>

<style scoped>
.empty-hint { padding: 60px 0; display: flex; justify-content: center; }
.error-box { display: flex; align-items: center; gap: 8px; padding: 16px; color: #ef4444; }
.ledger { display: flex; flex-direction: column; gap: 12px; }

.summary-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.summary-card {
  text-align: center;
  cursor: default;
}
.summary-num {
  font-size: 24px;
  font-weight: 700;
  color: #e2e8f0;
  line-height: 1.2;
}
.summary-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.section-card { border-radius: 10px; }
.cmd-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid #2a2a3e;
  font-size: 13px;
}
.cmd-row:last-child { border-bottom: none; }
.exit-tag { flex-shrink: 0; width: 28px; }

.file-list { display: flex; flex-wrap: wrap; gap: 6px; }
.file-tag {
  font-size: 12px;
  font-family: monospace;
  background: #2a2a3e;
  color: #94a3b8;
  padding: 2px 8px;
  border-radius: 4px;
}

.dep-row { display: flex; align-items: center; gap: 8px; padding: 4px 0; }

.incident-card {
  background: #2a2a3e;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
}
.incident-card:last-child { margin-bottom: 0; }

.verif-row { display: flex; align-items: center; gap: 8px; padding: 4px 0; }

.decision-row { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
</style>
