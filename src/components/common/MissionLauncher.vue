<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NInput, NButton, NIcon, NCard, NSpace, NText } from 'naive-ui'
import { SendOutline, BulbOutline } from '@vicons/ionicons5'

const router = useRouter()

// State
const inputText = ref('')
const isSubmitting = ref(false)
const currentTipIndex = ref(0)

// Smart tips rotation
const smartTips = [
  'Review my authentication code for security vulnerabilities...',
  'Help me plan a sprint for the new feature rollout...',
  'Analyze the user engagement data from last month...',
  'Generate API documentation from my codebase...',
  'Create a testing plan for the payment module...'
]

// Computed
const currentTip = computed(() => smartTips[currentTipIndex.value])

// Rotate tips every 5 seconds
setInterval(() => {
  currentTipIndex.value = (currentTipIndex.value + 1) % smartTips.length
}, 5000)

// Actions
async function handleSubmit() {
  if (!inputText.value.trim()) return
  
  isSubmitting.value = true
  
  // Navigate to new mission page and auto-submit
  router.push({
    name: 'mission-new',
    query: { prompt: inputText.value.trim() }
  })
}

function useTip() {
  inputText.value = currentTip.value
}
</script>

<template>
  <div class="mission-launcher">
    <div class="input-container">
      <NInput
        v-model:value="inputText"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        placeholder="Describe your mission... What do you want to accomplish?"
        size="large"
        @keydown.enter.ctrl="handleSubmit"
      />
      <div class="input-actions">
        <NButton 
          type="primary"
          size="large"
          :loading="isSubmitting"
          :disabled="!inputText.trim()"
          @click="handleSubmit"
        >
          <template #icon><NIcon><SendOutline /></NIcon></template>
          Start Mission
        </NButton>
        <span class="shortcut-hint">Ctrl + Enter to submit</span>
      </div>
    </div>

    <!-- Smart Tips -->
    <div class="smart-tips">
      <div class="tip-header">
        <NIcon><BulbOutline /></NIcon>
        <NText depth="3">Try asking:</NText>
      </div>
      <NCard 
        class="tip-card" 
        @click="useTip"
      >
        <NText>{{ currentTip }}</NText>
      </NCard>
    </div>
  </div>
</template>

<style scoped>
.mission-launcher {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}

.input-container {
  margin-bottom: var(--spacing-6);
}

.input-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
  margin-top: var(--spacing-4);
}

.shortcut-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.smart-tips {
  text-align: center;
}

.tip-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-3);
}

.tip-card {
  cursor: pointer;
  transition: all var(--transition-base);
  border: 1px solid var(--color-border);
}

.tip-card:hover {
  border-color: var(--color-primary);
  background-color: var(--color-bg-tertiary);
}
</style>
