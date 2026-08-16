<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NInput, NButton, NSpace, NText, NIcon, NGrid, NGi, NStatistic, NProgress, NSpin, NTag } from 'naive-ui'
import { PlayOutline, FlashOutline, TimeOutline, ChevronForwardOutline } from '@vicons/ionicons5'
import MissionLauncher from '@/components/common/MissionLauncher.vue'
import { useMissionStore, useAgentMarketStore } from '@/stores'
import { templateApi } from '@/api/axios'

const router = useRouter()
const missionStore = useMissionStore()
const agentStore = useAgentMarketStore()

// Quick templates from backend
const quickTemplates = ref<Array<{ id: string; name: string; icon: string; description: string }>>([])
const isLoadingTemplates = ref(false)

// Stats from store
const stats = computed(() => missionStore.stats)

// Recent missions from store
const recentMissions = computed(() => missionStore.missions.slice(0, 5))

// Active agents count
const activeAgentsCount = computed(() => agentStore.installedAgents.filter(a => a.enabled).length)

// Load data
onMounted(async () => {
  await Promise.all([
    missionStore.fetchMissions(),
    agentStore.fetchAgents(),
    fetchTemplates()
  ])
})

async function fetchTemplates() {
  isLoadingTemplates.value = true
  try {
    const response = await templateApi.list()
    if (response.success && response.data) {
      quickTemplates.value = (response.data as any[]).map((t: any) => ({
        id: t.id,
        name: t.name,
        icon: t.icon || '📝',
        description: t.description || ''
      }))
    }
  } catch (e) {
    console.error('Failed to fetch templates:', e)
  } finally {
    isLoadingTemplates.value = false
  }
}

function handleTemplateClick(templateId: string) {
  router.push({ name: 'mission-new', query: { template: templateId } })
}

function handleMissionClick(missionId: string) {
  router.push({ name: 'mission-detail', params: { id: missionId } })
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  
  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins} minutes ago`
  if (diffHours < 24) return `${diffHours} hours ago`
  return `${diffDays} days ago`
}
</script>

<template>
  <div class="dashboard-page">
    <!-- Hero Section with Mission Launcher -->
    <section class="hero-section">
      <NCard class="launcher-card">
        <div class="hero-header">
          <h1>Start Your Mission</h1>
          <NText depth="3">Describe your goal and let AI agents collaborate to achieve it</NText>
        </div>
        <MissionLauncher />
      </NCard>
    </section>

    <!-- Quick Templates -->
    <section class="quick-templates">
      <h2>Quick Start Templates</h2>
      <NSpin :show="isLoadingTemplates">
        <NGrid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
          <NGi v-for="template in quickTemplates" :key="template.id">
            <NCard 
              class="template-card" 
              hoverable
              @click="handleTemplateClick(template.id)"
            >
              <div class="template-content">
                <span class="template-icon">{{ template.icon }}</span>
                <h3>{{ template.name }}</h3>
                <NText depth="3">{{ template.description }}</NText>
              </div>
            </NCard>
          </NGi>
        </NGrid>
      </NSpin>
    </section>

    <!-- Stats & Recent -->
    <NGrid :cols="4" :x-gap="16" :y-gap="16" class="stats-section">
      <!-- Stats Cards -->
      <NGi>
        <NCard>
          <NStatistic label="Total Missions" :value="stats.total">
            <template #prefix>
              <NIcon><FlashOutline /></NIcon>
            </template>
          </NStatistic>
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <NStatistic label="Success Rate" :value="stats.successRate" suffix="%">
            <template #prefix>
              <NIcon><PlayOutline /></NIcon>
            </template>
          </NStatistic>
          <NProgress 
            type="line" 
            :percentage="stats.successRate" 
            :show-indicator="false"
            :height="8"
            style="margin-top: 8px"
          />
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <NStatistic label="Active Agents" :value="activeAgentsCount">
            <template #prefix>
              <NIcon><TimeOutline /></NIcon>
            </template>
          </NStatistic>
        </NCard>
      </NGi>
      <NGi>
        <NCard>
          <NStatistic label="Running" :value="stats.running">
            <template #prefix>
              <NIcon><PlayOutline /></NIcon>
            </template>
          </NStatistic>
        </NCard>
      </NGi>

      <!-- Recent Missions -->
      <NGi :span="4">
        <NCard title="Recent Missions">
          <div v-if="isLoadingTemplates" class="loading-container">
            <NSpin />
          </div>
          <div v-else-if="recentMissions.length === 0" class="empty-state">
            <NText depth="3">No missions yet. Create your first mission!</NText>
          </div>
          <div v-else class="recent-list">
            <div 
              v-for="mission in recentMissions" 
              :key="mission.id"
              class="recent-item"
              @click="handleMissionClick(mission.id)"
            >
              <div class="recent-info">
                <h4>{{ mission.title }}</h4>
                <NText depth="3">{{ formatDate(mission.createdAt) }}</NText>
              </div>
              <NSpace align="center">
                <NTag 
                  :type="mission.status === 'completed' ? 'success' : mission.status === 'running' ? 'info' : 'default'"
                  size="small"
                >
                  {{ mission.status }}
                </NTag>
                <NButton quaternary circle size="small">
                  <template #icon>
                    <NIcon><ChevronForwardOutline /></NIcon>
                  </template>
                </NButton>
              </NSpace>
            </div>
          </div>
        </NCard>
      </NGi>
    </NGrid>
  </div>
</template>

<style scoped>
.dashboard-page {
  max-width: var(--content-max-width);
  margin: 0 auto;
}

.hero-section {
  margin-bottom: var(--spacing-8);
}

.launcher-card {
  background: linear-gradient(135deg, var(--color-bg-secondary) 0%, var(--color-bg-tertiary) 100%);
}

.hero-header {
  text-align: center;
  margin-bottom: var(--spacing-6);
}

.hero-header h1 {
  margin-bottom: var(--spacing-2);
}

.quick-templates {
  margin-bottom: var(--spacing-8);
}

.quick-templates h2 {
  margin-bottom: var(--spacing-4);
}

.template-card {
  cursor: pointer;
  transition: all var(--transition-base);
}

.template-card:hover {
  transform: translateY(-4px);
}

.template-content {
  text-align: center;
}

.template-icon {
  font-size: var(--font-size-3xl);
  display: block;
  margin-bottom: var(--spacing-2);
}

.template-content h3 {
  margin-bottom: var(--spacing-1);
  font-size: var(--font-size-base);
}

.stats-section {
  margin-bottom: var(--spacing-8);
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: var(--spacing-8);
}

.empty-state {
  text-align: center;
  padding: var(--spacing-8);
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-3);
  border-radius: var(--radius-md);
  background-color: var(--color-bg-tertiary);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.recent-item:hover {
  background-color: var(--color-bg-elevated);
}

.recent-info h4 {
  margin-bottom: var(--spacing-1);
  font-size: var(--font-size-sm);
}
</style>
