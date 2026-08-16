<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NTag, NIcon, NText, NSpace } from 'naive-ui'
import { ShieldCheckmarkOutline, AlertCircleOutline, CheckmarkCircleOutline } from '@vicons/ionicons5'

interface Props {
  agentId?: string
  agentName?: string
  agentVersion?: string
  readinessState?: string
  providerEndpoint?: string
  configStatus?: string
}

const props = withDefaults(defineProps<Props>(), {
  agentId: 'codex',
  agentName: 'Codex',
  readinessState: 'UNKNOWN',
  providerEndpoint: '—',
  configStatus: '—'
})

const stateColors: Record<string, string> = {
  READY: '#22c55e',
  DEGRADED: '#f59e0b',
  RECOVERING: '#3b82f6',
  BLOCKED: '#ef4444',
  UNAVAILABLE: '#64748b'
}

const stateIcons: Record<string, any> = {
  READY: CheckmarkCircleOutline,
  DEGRADED: AlertCircleOutline,
  RECOVERING: ShieldCheckmarkOutline,
  BLOCKED: AlertCircleOutline,
  UNAVAILABLE: AlertCircleOutline
}

const icon = computed(() => stateIcons[props.readinessState] || AlertCircleOutline)
const color = computed(() => stateColors[props.readinessState] || '#64748b')
</script>

<template>
  <NCard size="small" class="readiness-badge">
    <template #header>
      <NSpace align="center">
        <NIcon :color="color" :size="16" :component="icon" />
        <NText strong>{{ agentName }}</NText>
        <NTag :color="color" size="tiny" style="background:transparent;color:inherit;border:none;padding:0 4px;">
          {{ readinessState }}
        </NTag>
      </NSpace>
    </template>
    <div class="readiness-details">
      <NText v-if="agentVersion && agentVersion !== '—'" depth="3" style="font-size:12px;">
        v{{ agentVersion }}
      </NText>
      <NText depth="3" style="font-size:12px;">
        provider: {{ providerEndpoint }}
      </NText>
      <NText depth="3" style="font-size:12px;">
        config: {{ configStatus }}
      </NText>
    </div>
  </NCard>
</template>

<style scoped>
.readiness-badge :deep(.n-card__header) {
  padding: 8px 12px;
}
.readiness-badge :deep(.n-card__content) {
  padding: 4px 12px 8px;
}
.readiness-details {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
