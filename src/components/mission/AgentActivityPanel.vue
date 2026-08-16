<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NTag, NIcon, NText, NSpace, NBadge } from 'naive-ui'
import { PersonOutline, ArrowForwardOutline, TimeOutline } from '@vicons/ionicons5'

interface Props {
  currentAgent?: string
  currentStep?: string
  handoffHistory?: Array<{ from: string; to: string; reason: string; time: string }>
}

const props = withDefaults(defineProps<Props>(), {
  currentAgent: 'codex',
  currentStep: 'implement',
  handoffHistory: () => []
})

const agentLabels: Record<string, string> = {
  codex: 'Codex',
  'claude-code': 'Claude Code',
  'git-verifier': 'Git Verifier',
  'test-runner-verifier': 'Test Runner'
}

const stepLabels: Record<string, string> = {
  implement: 'Implement',
  review: 'Review',
  verify: 'Verify',
  approve: 'Approve'
}

const currentLabel = computed(() => agentLabels[props.currentAgent] || props.currentAgent || 'Unknown')
const stepLabel = computed(() => stepLabels[props.currentStep] || props.currentStep || 'N/A')
</script>

<template>
  <NCard size="small" title="Agent Activity">
    <!-- Current Agent Card -->
    <div class="current-agent">
      <NSpace align="center">
        <NIcon size="20" color="#6366f1" :component="PersonOutline" />
        <div>
          <NText strong style="font-size:14px;">{{ currentLabel }}</NText>
          <div>
            <NTag size="small" type="info">{{ stepLabel }}</NTag>
          </div>
        </div>
        <NBadge :show="handoffHistory.length > 0" :dot-style="{ right: '8px' }">
          <NIcon :component="TimeOutline" style="margin-left:auto;" depth="3" />
        </NBadge>
      </NSpace>
    </div>

    <!-- Handoff History -->
    <div v-if="handoffHistory.length > 0" class="handoff-history">
      <div class="handoff-header">
        <NText depth="3" style="font-size:11px;text-transform:uppercase;letter-spacing:0.5px;">
          Handoff History
        </NText>
      </div>
      <div v-for="(h, idx) in handoffHistory" :key="idx" class="handoff-item">
        <NText depth="2" style="font-size:12px;">
          {{ agentLabels[h.from] || h.from }}
        </NText>
        <NIcon :component="ArrowForwardOutline" size="14" depth="3" />
        <NText depth="2" style="font-size:12px;">
          {{ agentLabels[h.to] || h.to }}
        </NText>
        <NText depth="3" style="font-size:11px;margin-left:auto;">
          {{ h.reason }}
        </NText>
      </div>
    </div>

    <div v-else class="no-handoffs">
      <NText depth="3" style="font-size:12px;">No handoffs yet</NText>
    </div>
  </NCard>
</template>

<style scoped>
.current-agent {
  padding: 8px 0;
  border-bottom: 1px solid var(--n-border-color);
  margin-bottom: 8px;
}
.handoff-history {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.handoff-header {
  margin-bottom: 4px;
}
.handoff-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--n-border-color);
  border-radius: var(--n-border-radius);
  font-size: 12px;
}
.no-handoffs {
  padding: 8px 0;
  text-align: center;
}
</style>
