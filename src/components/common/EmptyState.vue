<script setup lang="ts">
import { NButton, NIcon } from 'naive-ui'

interface Props {
  title: string
  description?: string
  icon?: string
  action?: {
    label: string
    onClick: () => void
  }
  type?: 'empty' | 'error' | 'no-permission' | 'no-network'
}

const props = withDefaults(defineProps<Props>(), {
  type: 'empty'
})

const iconMap = {
  empty: '📭',
  error: '⚠️',
  'no-permission': '🔒',
  'no-network': '📡'
}

const defaultDescriptions = {
  empty: '暂无数据',
  error: '出错了，请稍后重试',
  'no-permission': '您没有权限访问此内容',
  'no-network': '网络连接失败'
}
</script>

<template>
  <div class="empty-state" :class="`empty-state-${props.type}`">
    <div class="empty-state-icon">
      {{ props.icon || iconMap[props.type] }}
    </div>
    <h3 class="empty-state-title">{{ props.title }}</h3>
    <p v-if="props.description" class="empty-state-description">
      {{ props.description }}
    </p>
    <p v-else class="empty-state-description">
      {{ defaultDescriptions[props.type] }}
    </p>
    <div v-if="props.action" class="empty-state-action">
      <NButton type="primary" @click="props.action.onClick">
        {{ props.action.label }}
      </NButton>
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-12);
  text-align: center;
  min-height: 300px;
  background: var(--color-bg-primary);
  border-radius: var(--radius-md);
}

.empty-state-icon {
  font-size: var(--font-size-4xl);
  margin-bottom: var(--spacing-4);
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.empty-state-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--color-gray-900);
  margin-bottom: var(--spacing-2);
}

.empty-state-description {
  font-size: var(--font-size-base);
  color: var(--color-gray-500);
  margin-bottom: var(--spacing-4);
  max-width: 400px;
}

.empty-state-action {
  margin-top: var(--spacing-4);
}

/* 不同类型的样式 */
.empty-state-error {
  background: rgba(239, 68, 68, 0.05);
}

.empty-state-error .empty-state-title {
  color: var(--color-error);
}

.empty-state-no-permission {
  background: rgba(99, 102, 241, 0.05);
}

.empty-state-no-permission .empty-state-title {
  color: var(--color-primary);
}

.empty-state-no-network {
  background: rgba(245, 158, 11, 0.05);
}

.empty-state-no-network .empty-state-title {
  color: var(--color-warning);
}
</style>
