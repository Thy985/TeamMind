<script setup lang="ts">
import { NCard, NButton, NSpace, NTag, NIcon, NTooltip, NProgress, NText } from 'naive-ui'
import { DownloadOutline, TrashOutline, PlayOutline, PauseOutline, LockClosedOutline, StarOutline } from '@vicons/ionicons5'
import type { Agent } from '@/types'

const props = defineProps<{
  agent: Agent
  installed?: boolean
}>()

const emit = defineEmits<{
  (e: 'install', agentId: string): void
  (e: 'uninstall', agentId: string): void
  (e: 'toggle', agentId: string): void
}>()

// Permission tooltip
const permissionsText = props.agent.permissions?.join(', ') || 'No special permissions'
</script>

<template>
  <NCard class="agent-card" hoverable>
    <div class="card-header">
      <span class="agent-icon">{{ agent.icon }}</span>
      <div class="agent-title">
        <h3>{{ agent.name }}</h3>
        <NText depth="3">{{ agent.version }}</NText>
      </div>
      <NTooltip v-if="agent.permissions && agent.permissions.length > 0">
        <template #trigger>
          <NIcon class="lock-icon" size="16">
            <LockClosedOutline />
          </NIcon>
        </template>
        Permissions: {{ permissionsText }}
      </NTooltip>
    </div>

    <p class="agent-description">{{ agent.description }}</p>

    <!-- Stats -->
    <div class="agent-stats">
      <div v-if="agent.rating" class="stat">
        <NIcon><StarOutline /></NIcon>
        <span>{{ agent.rating }}</span>
      </div>
      <div v-if="agent.downloadCount" class="stat">
        <NIcon><DownloadOutline /></NIcon>
        <span>{{ agent.downloadCount.toLocaleString() }}</span>
      </div>
    </div>

    <!-- Test Report -->
    <div v-if="agent.testReport" class="test-report">
      <NText depth="3">Test Pass Rate</NText>
      <NProgress 
        type="line"
        :percentage="agent.testReport.passRate"
        :show-indicator="true"
        :height="6"
        :border-radius="3"
      />
    </div>

    <!-- Actions -->
    <div class="card-actions">
      <template v-if="installed">
        <NButton 
          :type="agent.status === 'running' ? 'warning' : 'primary'"
          size="small"
          @click="emit('toggle', agent.id)"
        >
          <template #icon>
            <NIcon>
              <PauseOutline v-if="agent.status === 'running'" />
              <PlayOutline v-else />
            </NIcon>
          </template>
          {{ agent.status === 'running' ? 'Disable' : 'Enable' }}
        </NButton>
        <NButton 
          size="small"
          type="error"
          quaternary
          @click="emit('uninstall', agent.id)"
        >
          <template #icon><NIcon><TrashOutline /></NIcon></template>
          Uninstall
        </NButton>
      </template>
      <template v-else>
        <NButton 
          type="primary"
          size="small"
          @click="emit('install', agent.id)"
        >
          <template #icon><NIcon><DownloadOutline /></NIcon></template>
          Install
        </NButton>
      </template>
    </div>
  </NCard>
</template>

<style scoped>
.agent-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-3);
}

.agent-icon {
  font-size: var(--font-size-3xl);
}

.agent-title {
  flex: 1;
}

.agent-title h3 {
  margin: 0 0 var(--spacing-1) 0;
  font-size: var(--font-size-base);
}

.lock-icon {
  color: var(--color-warning);
}

.agent-description {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-3);
}

.agent-stats {
  display: flex;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-3);
}

.stat {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.test-report {
  margin-bottom: var(--spacing-3);
}

.card-actions {
  display: flex;
  gap: var(--spacing-2);
  margin-top: auto;
}
</style>
