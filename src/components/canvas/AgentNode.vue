<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { NCard, NTag, NIcon, NSpin } from 'naive-ui'
import { 
  CheckmarkCircleOutline, 
  AlertCircleOutline, 
  TimeOutline,
  EllipseOutline
} from '@vicons/ionicons5'
import type { AgentStatus } from '@/types'
import type { Component } from 'vue'

interface NodeData {
  label: string
  status?: AgentStatus
  agent?: {
    icon?: string
    description?: string
  }
}

const props = defineProps<NodeProps<NodeData>>()

// Status config
const statusConfig: Record<AgentStatus, { color: 'default' | 'info' | 'success' | 'error' | 'warning'; icon: Component; label: string }> = {
  idle: { color: 'info', icon: EllipseOutline, label: 'Idle' },
  running: { color: 'info', icon: TimeOutline, label: 'Running' },
  success: { color: 'success', icon: CheckmarkCircleOutline, label: 'Success' },
  error: { color: 'error', icon: AlertCircleOutline, label: 'Error' },
  waiting: { color: 'warning', icon: TimeOutline, label: 'Waiting' }
}

const currentStatus = computed(() => statusConfig[props.data.status || 'idle'])
</script>

<template>
  <div class="agent-node">
    <Handle type="target" :position="Position.Left" />
    
    <NCard 
      size="small"
      :class="['node-card', `status-${props.data.status}`]"
    >
      <div class="node-content">
        <div class="node-header">
          <span class="node-icon">{{ props.data.agent?.icon || '🤖' }}</span>
          <div class="node-title">
            <h4>{{ props.data.label }}</h4>
            <NTag 
              :type="currentStatus.color"
              size="small"
              :bordered="false"
            >
              <template #icon>
                <NIcon>
                  <NSpin v-if="props.data.status === 'running'" :size="14" />
                  <component v-else :is="currentStatus.icon" />
                </NIcon>
              </template>
              {{ currentStatus.label }}
            </NTag>
          </div>
        </div>
        
        <p v-if="props.data.agent?.description" class="node-desc">
          {{ props.data.agent.description }}
        </p>
      </div>
    </NCard>
    
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<style scoped>
.agent-node {
  position: relative;
}

.node-card {
  min-width: 200px;
  max-width: 280px;
  transition: all var(--transition-base);
  border: 2px solid transparent;
}

.node-card:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-lg);
}

.node-card.status-idle {
  border-color: var(--color-text-tertiary);
}

.node-card.status-running {
  border-color: var(--color-primary);
}

.node-card.status-success {
  border-color: var(--color-success);
}

.node-card.status-error {
  border-color: var(--color-error);
}

.node-card.status-waiting {
  border-color: var(--color-warning);
}

.node-content {
  padding: var(--spacing-2);
}

.node-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.node-icon {
  font-size: 28px;
}

.node-title {
  flex: 1;
}

.node-title h4 {
  margin: 0 0 var(--spacing-1) 0;
  font-size: var(--font-size-sm);
}

.node-desc {
  margin: var(--spacing-2) 0 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}
</style>
