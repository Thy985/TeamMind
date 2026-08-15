<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NTag, NIcon, NSpace, NText, NSpin, NEmpty, NButton } from 'naive-ui'
import { 
  AlertCircleOutline, CheckmarkCircleOutline, 
  RefreshOutline, BulbOutline, WarningOutline 
} from '@vicons/ionicons5'
import { missionControlApi } from '@/api/axios'

interface Props {
  projectId: string
}
const props = defineProps<Props>()

const recommendation = ref<any>(null)
const driftAlerts = ref<any[]>([])
const loading = ref(false)
const recalculating = ref(false)

async function load() {
  loading.value = true
  try {
    const [rec, drift] = await Promise.all([
      missionControlApi.recommendation(props.projectId),
      missionControlApi.driftAlerts(props.projectId)
    ])
    recommendation.value = rec || null
    driftAlerts.value = drift || []
  } finally {
    loading.value = false
  }
}

async function recalculate() {
  recalculating.value = true
  try {
    await missionControlApi.recalculate(props.projectId)
    await load()
  } finally {
    recalculating.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <div v-if="loading" class="loading-wrap"><NSpin size="large" /></div>
  <div v-else class="panels">
    <!-- Recommendations Panel -->
    <div class="panel">
      <div class="panel-header">
        <NSpace>
          <NIcon :component="BulbOutline" color="#f59e0b" />
          <span>团队配置推荐</span>
        </NSpace>
      </div>
      <div class="panel-body">
        <NCard v-if="recommendation" embedded size="small">
          <template #header>
            <NText depth="2" style="font-size: 13px">
              基于 {{ recommendation.totalTasks }} 次任务表现
            </NText>
          </template>
          <div v-if="recommendation.issues?.length">
            <div v-for="issue in recommendation.issues" :key="issue.role" class="issue-item">
              <NIcon :component="WarningOutline" color="#ef4444" size="16" />
              <div>
                <NText depth="2" style="font-size: 13px; font-weight: 600">
                  {{ issue.role }} → {{ issue.currentPlugin }}
                </NText>
                <NText depth="3" style="font-size: 12px">{{ issue.reason }}</NText>
              </div>
            </div>
          </div>
          <div v-else class="good-config">
            <NIcon :component="CheckmarkCircleOutline" color="#22c55e" size="20" />
            <NText>当前配置表现良好，无需调整</NText>
          </div>
          <div style="margin-top: 12px; padding-top: 12px; border-top: 1px solid #3a3a5c">
            <NText depth="2" style="font-size: 12px">推荐配置：</NText>
            <div v-for="(plugin, role) in recommendation.recommendedTeam" :key="role" class="rec-item">
              <NTag size="tiny" color="#6366f122" text-color="#6366f1" border-type="solid">{{ role }}</NTag>
              <NText depth="2" style="font-size: 12px; margin-left: 8px">{{ plugin }}</NText>
            </div>
          </div>
        </NCard>
        <NEmpty v-else description="数据不足，暂时无法生成推荐" style="padding: 40px 0" />
      </div>
    </div>

    <!-- Drift Alerts Panel -->
    <div class="panel">
      <div class="panel-header">
        <NSpace>
          <NIcon :component="AlertCircleOutline" color="#ef4444" />
          <span>漂移告警</span>
          <NTag v-if="driftAlerts.length" size="small" color="#ef444422" text-color="#ef4444" border-type="solid">
            {{ driftAlerts.length }}
          </NTag>
        </NSpace>
        <NButton size="tiny" :loading="recalculating" @click="recalculate">
          <template #icon><NIcon :component="RefreshOutline" /></template>
          刷新
        </NButton>
      </div>
      <div class="panel-body">
        <div v-if="driftAlerts.length">
          <div v-for="alert in driftAlerts" :key="alert.pluginId + alert.role" class="alert-item">
            <NTag 
              :color="alert.trend === 'DECLINING' ? '#ef444422' : '#22c55e22'"
              :text-color="alert.trend === 'DECLINING' ? '#ef4444' : '#22c55e'"
              size="tiny"
            >
              {{ alert.trend }}
            </NTag>
            <div class="alert-content">
              <NText depth="2" style="font-size: 13px; font-weight: 600">
                {{ alert.pluginId }} @ {{ alert.role }}
              </NText>
              <NText depth="3" style="font-size: 12px">{{ alert.recommendation }}</NText>
            </div>
          </div>
        </div>
        <NEmpty v-else description="没有检测到性能漂移" style="padding: 40px 0" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading-wrap { display: flex; justify-content: center; padding: 60px; }

.panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel {
  display: flex;
  flex-direction: column;
  background: #1e1e2e;
  border-radius: 12px;
  border: 1px solid #3a3a5c;
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: #252538;
  border-bottom: 1px solid #3a3a5c;
  font-weight: 600;
  font-size: 14px;
  color: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.issue-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  background: #ef444411;
  border-radius: 8px;
  margin-bottom: 8px;
}

.good-config {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: #22c55e11;
  border-radius: 8px;
}

.rec-item {
  display: flex;
  align-items: center;
  margin-top: 4px;
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  background: #252538;
  border-radius: 8px;
  margin-bottom: 8px;
}

.alert-content {
  flex: 1;
  min-width: 0;
}
</style>
