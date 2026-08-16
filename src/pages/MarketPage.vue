<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NCard, NGrid, NGi, NInput, NSpace, NButton, NTag, NIcon, NEmpty, NTooltip, NProgress, NText } from 'naive-ui'
import { SearchOutline, DownloadOutline, LockClosedOutline, StarOutline } from '@vicons/ionicons5'
import AgentCard from '@/components/common/AgentCard.vue'
import { useAgentMarketStore } from '@/stores'
import type { Agent } from '@/types'

const agentStore = useAgentMarketStore()

// State
const searchQuery = ref('')
const selectedCategory = ref<string | null>(null)

// Categories
const categories = ['Development', 'Data', 'Documentation', 'Testing', 'Integration']

// Filter agents
const filteredAgents = computed(() => {
  let result = agentStore.agents
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter((agent: any) => 
      agent.name.toLowerCase().includes(query) ||
      agent.description.toLowerCase().includes(query)
    )
  }
  return result
})

// Actions
function handleInstall(agentId: string) {
  agentStore.installAgent(agentId)
}

onMounted(() => {
  agentStore.fetchAgents()
})
</script>

<template>
  <div class="market-page">
    <div class="page-header">
      <h1>Agent Market</h1>
      <p>Browse and install AI agents for your missions</p>
    </div>

    <!-- Search & Filters -->
    <NCard class="search-card">
      <NSpace>
        <NInput 
          v-model:value="searchQuery"
          placeholder="Search agents..."
          clearable
          style="width: 400px"
        >
          <template #prefix>
            <NIcon><SearchOutline /></NIcon>
          </template>
        </NInput>
      </NSpace>
    </NCard>

    <!-- Installed Section -->
    <div v-if="agentStore.installedAgents.length > 0" class="section">
      <h2>Installed Agents</h2>
      <NGrid :cols="3" :x-gap="16" :y-gap="16">
        <NGi v-for="agent in agentStore.installedAgents" :key="agent.id">
          <AgentCard 
            :agent="agent"
            :installed="true"
            @toggle="agentStore.toggleAgent(agent.id, !agent.enabled)"
            @uninstall="agentStore.uninstallAgent(agent.id)"
          />
        </NGi>
      </NGrid>
    </div>

    <!-- Available Section -->
    <div class="section">
      <h2>Available Agents</h2>
      <NGrid v-if="filteredAgents.length > 0" :cols="3" :x-gap="16" :y-gap="16">
        <NGi v-for="agent in filteredAgents" :key="agent.id">
          <AgentCard 
            :agent="agent"
            @install="handleInstall(agent.id)"
          />
        </NGi>
      </NGrid>
      
      <NEmpty v-else description="未找到匹配的 Agent" />
    </div>
  </div>
</template>

<style scoped>
.market-page {
  max-width: var(--content-max-width);
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--spacing-6);
}

.page-header h1 {
  margin-bottom: var(--spacing-2);
}

.search-card {
  margin-bottom: var(--spacing-6);
}

.section {
  margin-bottom: var(--spacing-8);
}

.section h2 {
  margin-bottom: var(--spacing-4);
}
</style>
