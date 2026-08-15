<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { NCard, NSpace, NTag, NP, NText, NIcon, NButton, NSpin, NEmpty, NTabs, NTabPane, NModal, NForm, NFormItem, NSelect, NDivider } from 'naive-ui'
import { 
  PlayOutline, PauseCircleOutline, TimeOutline, CheckmarkCircleOutline, 
  WarningOutline, TrendingUpOutline, TrendingDownOutline, RefreshOutline,
  AnalyticsOutline, AppstoreOutline, AlertCircleOutline, SettingsOutline
} from '@vicons/ionicons5'
import { missionControlApi } from '@/api/axios'
import Panel1Overview from '@/components/mission/Panel1ProjectOverview.vue'
import Panel2Live from '@/components/mission/Panel2LiveExecution.vue'
import Panel3Performance from '@/components/mission/Panel3PerformanceProfile.vue'
import Panel4Recommendations from '@/components/mission/Panel4Recommendations.vue'

interface Props {
  projectId: string
}

const props = defineProps<Props>()

const loading = ref(false)
const activeTab = ref('overview')

// Control mode
const controlMode = ref('SUPERVISED')
const showModeModal = ref(false)

const modeOptions = [
  { label: 'AUTOMATED（全自动）', value: 'AUTOMATED' },
  { label: 'SUPERVISED（监督模式）', value: 'SUPERVISED' },
  { label: 'MANUAL（人工模式）', value: 'MANUAL' }
]

const modeColors: Record<string, string> = {
  AUTOMATED: '#22c55e',
  SUPERVISED: '#f59e0b',
  MANUAL: '#ef4444'
}

async function loadMode() {
  try {
    const data = await missionControlApi.controlMode(props.projectId)
    controlMode.value = (data as any).controlMode || 'SUPERVISED'
  } catch {
    // ignore
  }
}

async function saveMode(mode: string) {
  try {
    await missionControlApi.setControlMode(props.projectId, mode)
    controlMode.value = mode
    showModeModal.value = false
  } catch (e) {
    console.error('Failed to set control mode', e)
  }
}

// Tab navigation
const tabs = [
  { key: 'overview', label: '概览', icon: AnalyticsOutline },
  { key: 'live', label: '实时执行', icon: PlayOutline },
  { key: 'performance', label: '性能档案', icon: TrendingUpOutline },
  { key: 'recommendations', label: '推荐', icon: SettingsOutline }
]

onMounted(() => {
  loadMode()
})
</script>

<template>
  <div class="mission-control">
    <!-- Header -->
    <div class="mc-header">
      <div class="mc-header-left">
        <NIcon :size="24" color="#6366f1" :component="AppstoreOutline" />
        <span class="mc-title">Mission Control</span>
        <NTag 
          :color="{ color: modeColors[controlMode] + '22', borderColor: modeColors[controlMode], textColor: modeColors[controlMode] }"
          border-type="solid"
          size="small"
        >
          {{ controlMode }}
        </NTag>
      </div>
      <NSpace>
        <NButton size="small" @click="showModeModal = true">
          <template #icon><NIcon :component="SettingsOutline" /></template>
          控制模式
        </NButton>
      </NSpace>
    </div>

    <!-- Tabs -->
    <NTabs v-model:value="activeTab" type="line" size="large" class="mc-tabs">
      <NTabPane v-for="tab in tabs" :key="tab.key" :tab="tab.label" :name="tab.key">
        <template #tab>
          <NSpace>
            <NIcon :component="tab.icon" />
            <span>{{ tab.label }}</span>
          </NSpace>
        </template>
      </NTabPane>
    </NTabs>

    <!-- Panel Content -->
    <div class="mc-content">
      <Panel1Overview v-if="activeTab === 'overview'" :project-id="projectId" />
      <Panel2Live v-else-if="activeTab === 'live'" :project-id="projectId" />
      <Panel3Performance v-else-if="activeTab === 'performance'" :project-id="projectId" />
      <Panel4Recommendations v-else-if="activeTab === 'recommendations'" :project-id="projectId" />
    </div>

    <!-- Control Mode Modal -->
    <NModal v-model:show="showModeModal" preset="card" title="设置控制模式" style="width: 400px">
      <NP depth="3" class="mb-4">
        选择此项目的 Agent 执行控制级别。
      </NP>
      <NForm label-placement="left" label-width="120">
        <NFormItem label="控制模式">
          <NSelect
            :value="controlMode"
            :options="modeOptions"
            @update:value="(v: string) => controlMode = v"
            style="width: 100%"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showModeModal = false">取消</NButton>
          <NButton type="primary" @click="saveMode(controlMode)">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.mission-control {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 0;
}

.mc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: linear-gradient(135deg, #1e1e2e 0%, #2d2d44 100%);
  border-bottom: 1px solid #3a3a5c;
}

.mc-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mc-title {
  font-size: 18px;
  font-weight: 700;
  color: #e2e8f0;
  letter-spacing: 0.5px;
}

.mc-tabs {
  padding: 0 24px;
  background: #1e1e2e;
  border-bottom: 1px solid #3a3a5c;
}

.mc-content {
  flex: 1;
  overflow: auto;
  padding: 24px;
  background: #0f0f1a;
}

.mb-4 { margin-bottom: 16px; }
</style>
