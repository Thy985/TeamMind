<script setup lang="ts">
import { NCard, NButton, NSpace, NTag, NIcon, NText } from 'naive-ui'
import { PlayOutline, CreateOutline, TrashOutline } from '@vicons/ionicons5'
import type { TeamTemplate } from '@/types'

const props = defineProps<{
  template: TeamTemplate
  editable?: boolean
}>()

const emit = defineEmits<{
  (e: 'use', templateId: string): void
  (e: 'edit', templateId: string): void
  (e: 'delete', templateId: string): void
}>()
</script>

<template>
  <NCard class="template-card" hoverable>
    <div class="card-header">
      <span class="template-icon">{{ template.icon }}</span>
      <div class="template-title">
        <h3>{{ template.name }}</h3>
        <NTag size="small" :bordered="false">{{ template.category }}</NTag>
      </div>
    </div>

    <p class="template-description">{{ template.description }}</p>

    <!-- Agents count -->
    <div class="template-meta">
      <NText depth="3">{{ template.agents.length }} agents</NText>
      <NText depth="3">•</NText>
      <NText depth="3">{{ template.usageCount }} uses</NText>
    </div>

    <!-- Actions -->
    <div class="card-actions">
      <NButton 
        type="primary"
        size="small"
        @click="emit('use', template.id)"
      >
        <template #icon><NIcon><PlayOutline /></NIcon></template>
        Use Template
      </NButton>
      
      <template v-if="editable !== false">
        <NButton 
          size="small"
          quaternary
          @click="emit('edit', template.id)"
        >
          <template #icon><NIcon><CreateOutline /></NIcon></template>
        </NButton>
        <NButton 
          size="small"
          quaternary
          type="error"
          @click="emit('delete', template.id)"
        >
          <template #icon><NIcon><TrashOutline /></NIcon></template>
        </NButton>
      </template>
    </div>
  </NCard>
</template>

<style scoped>
.template-card {
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

.template-icon {
  font-size: var(--font-size-3xl);
}

.template-title {
  flex: 1;
}

.template-title h3 {
  margin: 0 0 var(--spacing-1) 0;
  font-size: var(--font-size-base);
}

.template-description {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-3);
}

.template-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-3);
  font-size: var(--font-size-xs);
}

.card-actions {
  display: flex;
  gap: var(--spacing-2);
  margin-top: auto;
}
</style>
