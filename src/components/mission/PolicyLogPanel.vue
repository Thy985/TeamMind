<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NTag, NIcon, NText, NSpace, NAlert } from 'naive-ui'
import { AlertCircleOutline, CheckmarkCircleOutline, LockClosedOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'

interface Finding {
  id: string
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  description: string
  resolved: boolean
}

interface ApprovalRequest {
  id: string
  requestedBy: string
  reason: string
  granted: boolean
  timestamp: string
}

interface Props {
  findings?: Finding[]
  approvalRequests?: ApprovalRequest[]
  needsApproval?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  findings: () => [],
  approvalRequests: () => [],
  needsApproval: false
})

const severityColors: Record<string, string> = {
  CRITICAL: 'var(--color-error)',
  HIGH: 'var(--color-warning)',
  MEDIUM: 'var(--color-info)',
  LOW: 'var(--color-text-tertiary)'
}

const criticalCount = computed(() => props.findings.filter(f => f.severity === 'CRITICAL' && !f.resolved).length)
const highCount = computed(() => props.findings.filter(f => f.severity === 'HIGH' && !f.resolved).length)
const pendingApprovals = computed(() => props.approvalRequests.filter(a => !a.granted).length)
</script>

<template>
  <NCard size="small" title="Policy & Findings">
    <!-- Approval Alert -->
    <NAlert
      v-if="needsApproval || pendingApprovals > 0"
      type="warning"
      :bordered="false"
      class="approval-alert"
    >
      <template #icon><NIcon :component="LockClosedOutline" /></template>
      <strong>Needs Approval</strong>
      <span v-if="pendingApprovals > 0">
        — {{ pendingApprovals }} pending request{{ pendingApprovals > 1 ? 's' : '' }}
      </span>
    </NAlert>

    <!-- Critical Findings -->
    <div v-if="criticalCount > 0" class="finding-group">
      <div class="finding-header">
        <NIcon :component="AlertCircleOutline" color="var(--color-error)" :size="14" />
        <NText strong style="color:var(--color-error);font-size: var(--font-size-xs);">
          CRITICAL ({{ criticalCount }})
        </NText>
      </div>
      <div v-for="f in findings.filter(f => f.severity === 'CRITICAL' && !f.resolved)" :key="f.id" class="finding-item critical">
        <NText style="font-size: var(--font-size-xs);">{{ f.description }}</NText>
      </div>
    </div>

    <!-- High Findings -->
    <div v-if="highCount > 0" class="finding-group">
      <div class="finding-header">
        <NIcon :component="AlertCircleOutline" color="var(--color-warning)" :size="14" />
        <NText strong style="color:var(--color-warning);font-size: var(--font-size-xs);">
          HIGH ({{ highCount }})
        </NText>
      </div>
      <div v-for="f in findings.filter(f => f.severity === 'HIGH' && !f.resolved)" :key="f.id" class="finding-item high">
        <NText style="font-size: var(--font-size-xs);">{{ f.description }}</NText>
      </div>
    </div>

    <!-- All Findings Table -->
    <div v-if="findings.length > 0" class="findings-table">
      <div class="finding-header">
        <NIcon :component="ShieldCheckmarkOutline" :size="14" color="var(--color-primary)" />
        <NText strong style="font-size: var(--font-size-xs);color:var(--color-primary);">All Findings</NText>
      </div>
      <div v-for="f in findings" :key="f.id" class="finding-item" :class="{ resolved: f.resolved }">
        <NTag :color="severityColors[f.severity]" size="tiny" style="background:transparent;color:inherit;border:1px solid currentColor;padding:0 4px;">
          {{ f.severity }}
        </NTag>
        <NText style="font-size: var(--font-size-xs);flex:1;">{{ f.description }}</NText>
        <NIcon
          v-if="f.resolved"
          :component="CheckmarkCircleOutline"
          size="14"
          color="var(--color-success)"
        />
      </div>
    </div>

    <div v-if="findings.length === 0 && !needsApproval" class="empty">
      <NText depth="3">No findings — all checks passed</NText>
    </div>
  </NCard>
</template>

<style scoped>
.approval-alert {
  margin-bottom: 12px;
}
.finding-group {
  margin-bottom: 12px;
}
.finding-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.finding-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: var(--n-border-color);
  border-radius: var(--n-border-radius);
  margin-bottom: 2px;
}
.finding-item.critical {
  background: var(--color-error)22;
  border-left: 2px solid var(--color-error);
}
.finding-item.high {
  background: var(--color-warning)22;
  border-left: 2px solid var(--color-warning);
}
.finding-item.resolved {
  opacity: 0.5;
}
.empty {
  padding: 16px 0;
  text-align: center;
}
</style>
