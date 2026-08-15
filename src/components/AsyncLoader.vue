<script setup lang="ts">
import { ref, onMounted } from 'vue'

const props = defineProps<{
  greeting?: string
}>()

const message = ref('')
const loading = ref(false)

async function simulateAsyncFetch() {
  loading.value = true
  try {
    // Simulate network request with 500ms delay
    await new Promise(resolve => setTimeout(resolve, 500))
    message.value = props.greeting ?? 'Hello from Vue Component!'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  simulateAsyncFetch()
})

defineExpose({ message, loading })
</script>

<template>
  <div class="async-loader" data-testid="async-loader">
    <h2 data-testid="loader-title">Async Loader</h2>
    <p data-testid="loading-state" v-if="loading">Loading...</p>
    <p data-testid="result" v-else>{{ message }}</p>
    <button data-testid="btn-reload" @click="simulateAsyncFetch">Reload</button>
  </div>
</template>

<style scoped>
.async-loader {
  padding: 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  max-width: 300px;
}
button {
  padding: 6px 12px;
  cursor: pointer;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  margin-top: 8px;
}
</style>
