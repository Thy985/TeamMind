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
  READY: 'var(--color-success)',
  DEGRADED: 'var(--color-warning)',
  RECOVERING: 'var(--color-info)',
  BLOCKED: 'var(--color-error)',
  UNAVAILABLE: 'var(--color-text-tertiary)'
}

const stateIcons: Record<string, any> = {
  READY: CheckmarkCircleOutline,
  DEGRADED: AlertCircleOutline,
  RECOVERING: ShieldCheckmarkOutline,
  BLOCKED: AlertCircleOutline,
  UNAVAILABLE: AlertCircleOutline
}

const icon = computed(() => stateIcons[props.readinessState] || AlertCircleOutline)
const color = computed(() => stateColors[props.readinessState] || 'var(--color-text-tertiary)')
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
      <NText v-if="agentVersion && agentVersion !== '—'" depth="3" style="font-size: var(--font-size-xs);">
        v{{ agentVersion }}
      </NText>
      <NText depth="3" style="font-size: var(--font-size-xs);">
        provider: {{ providerEndpoint }}
      </NText>
      <NText depth="3" style="font-size: var(--font-size-xs);">
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
