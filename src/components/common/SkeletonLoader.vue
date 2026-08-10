<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  count?: number
  type?: 'card' | 'list' | 'table' | 'text'
  animated?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  count: 3,
  type: 'card',
  animated: true
})

const skeletonItems = computed(() => Array(props.count).fill(0))
</script>

<template>
  <div class="skeleton-loader" :class="{ animated: props.animated }">
    <!-- Card Skeleton -->
    <template v-if="props.type === 'card'">
      <div v-for="i in skeletonItems" :key="i" class="skeleton-card">
        <div class="skeleton-header">
          <div class="skeleton-avatar"></div>
          <div class="skeleton-title"></div>
        </div>
        <div class="skeleton-content">
          <div class="skeleton-line"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line short"></div>
        </div>
        <div class="skeleton-footer">
          <div class="skeleton-button"></div>
          <div class="skeleton-button"></div>
        </div>
      </div>
    </template>

    <!-- List Skeleton -->
    <template v-else-if="props.type === 'list'">
      <div v-for="i in skeletonItems" :key="i" class="skeleton-list-item">
        <div class="skeleton-avatar"></div>
        <div class="skeleton-content">
          <div class="skeleton-line"></div>
          <div class="skeleton-line short"></div>
        </div>
        <div class="skeleton-action"></div>
      </div>
    </template>

    <!-- Table Skeleton -->
    <template v-else-if="props.type === 'table'">
      <div class="skeleton-table">
        <div class="skeleton-table-header">
          <div class="skeleton-cell"></div>
          <div class="skeleton-cell"></div>
          <div class="skeleton-cell"></div>
          <div class="skeleton-cell"></div>
        </div>
        <div v-for="i in skeletonItems" :key="i" class="skeleton-table-row">
          <div class="skeleton-cell"></div>
          <div class="skeleton-cell"></div>
          <div class="skeleton-cell"></div>
          <div class="skeleton-cell"></div>
        </div>
      </div>
    </template>

    <!-- Text Skeleton -->
    <template v-else-if="props.type === 'text'">
      <div v-for="i in skeletonItems" :key="i" class="skeleton-text">
        <div class="skeleton-line"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.skeleton-loader {
  width: 100%;
}

.skeleton-loader.animated {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

/* Card Skeleton */
.skeleton-card {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-4);
  margin-bottom: var(--spacing-4);
  box-shadow: var(--shadow-sm);
}

.skeleton-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-4);
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-gray-300);
}

.skeleton-title {
  flex: 1;
  height: 20px;
  background: var(--color-gray-300);
  border-radius: var(--radius-sm);
}

.skeleton-content {
  margin-bottom: var(--spacing-4);
}

.skeleton-line {
  height: 16px;
  background: var(--color-gray-300);
  border-radius: var(--radius-sm);
  margin-bottom: var(--spacing-2);
}

.skeleton-line.short {
  width: 60%;
}

.skeleton-footer {
  display: flex;
  gap: var(--spacing-2);
}

.skeleton-button {
  flex: 1;
  height: 40px;
  background: var(--color-gray-300);
  border-radius: var(--radius-sm);
}

/* List Skeleton */
.skeleton-list-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3);
  border-bottom: 1px solid var(--color-gray-200);
}

.skeleton-action {
  width: 80px;
  height: 32px;
  background: var(--color-gray-300);
  border-radius: var(--radius-sm);
}

/* Table Skeleton */
.skeleton-table {
  width: 100%;
  border-collapse: collapse;
}

.skeleton-table-header,
.skeleton-table-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-2);
  padding: var(--spacing-3);
  border-bottom: 1px solid var(--color-gray-200);
}

.skeleton-cell {
  height: 20px;
  background: var(--color-gray-300);
  border-radius: var(--radius-sm);
}

/* Text Skeleton */
.skeleton-text {
  margin-bottom: var(--spacing-4);
}
</style>
