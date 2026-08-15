<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  initialCount?: number
  label?: string
}>()

const count = ref(props.initialCount ?? 0)
const doubled = computed(() => count.value * 2)

function increment() {
  count.value++
}

function decrement() {
  count.value--
}

defineExpose({ count, doubled })
</script>

<template>
  <div class="counter-widget" data-testid="counter-widget">
    <h2 data-testid="widget-title">{{ label ?? 'Counter Widget' }}</h2>
    <div class="value-display" data-testid="value-display">
      Count: <strong>{{ count }}</strong> (doubled: {{ doubled }})
    </div>
    <div class="button-group" data-testid="button-group">
      <button data-testid="btn-decrement" @click="decrement">-</button>
      <button data-testid="btn-increment" @click="increment">+</button>
    </div>
    <div class="status" data-testid="status">{{ count >= 0 ? 'positive' : 'negative' }}</div>
  </div>
</template>

<style scoped>
.counter-widget {
  font-family: system-ui, sans-serif;
  padding: 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  max-width: 300px;
}
.value-display {
  font-size: 18px;
  margin: 8px 0;
}
.button-group {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
button {
  padding: 4px 12px;
  cursor: pointer;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
}
button:hover {
  background: #f0f0f0;
}
.status {
  font-size: 12px;
  color: #888;
  margin-top: 8px;
}
</style>
