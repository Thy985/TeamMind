<script setup lang="ts">
import { ref, computed } from 'vue'
import { 
  NCard, 
  NSpace, 
  NButton, 
  NSwitch, 
  NSelect, 
  NInput, 
  NDivider, 
  NIcon,
  NText,
  NAlert,
  NGrid,
  NGi,
  NList,
  NListItem,
  NThing
} from 'naive-ui'
import { AddOutline, TrashOutline, DownloadOutline, MoonOutline, SunnyOutline } from '@vicons/ionicons5'
import { useUIStore } from '@/stores'
import type { ModelConfig } from '@/types'

const uiStore = useUIStore()

// State
const newModelConfig = ref<Omit<ModelConfig, 'id'>>({
  name: '',
  provider: 'openai',
  apiKey: '',
  baseUrl: '',
  isDefault: false
})

// Model provider options
const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'Azure OpenAI', value: 'azure' },
  { label: 'Local / Custom', value: 'custom' }
]

// Language options
const languageOptions = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

// Actions
function handleAddModel() {
  if (newModelConfig.value.name && newModelConfig.value.apiKey) {
    uiStore.addModelConfig(newModelConfig.value)
    newModelConfig.value = {
      name: '',
      provider: 'openai',
      apiKey: '',
      baseUrl: '',
      isDefault: false
    }
  }
}

function handleRemoveModel(id: string) {
  uiStore.removeModelConfig(id)
}

function handleSetDefault(id: string) {
  uiStore.updateModelConfig(id, { isDefault: true })
}

function handleExportData() {
  // TODO: Export user data
  const data = {
    settings: uiStore.settings,
    exportedAt: new Date().toISOString()
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'teammind-settings.json'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="settings-page">
    <div class="page-header">
      <h1>Settings</h1>
      <p>Configure your TeamMind experience</p>
    </div>

    <NGrid :cols="2" :x-gap="24">
      <!-- Model Configuration -->
      <NGi>
        <NCard title="Model Configuration">
          <NSpace vertical>
            <!-- Existing Models -->
            <div v-if="uiStore.settings.modelConfigs.length > 0">
              <NText class="section-label">Configured Models</NText>
              <NList bordered>
                <NListItem v-for="model in uiStore.settings.modelConfigs" :key="model.id">
                  <NThing :title="model.name">
                    <template #description>
                      <NSpace>
                        <NTag size="small">{{ model.provider }}</NTag>
                        <NTag v-if="model.isDefault" type="success" size="small">Default</NTag>
                      </NSpace>
                    </template>
                  </NThing>
                  <template #suffix>
                    <NSpace>
                      <NButton 
                        v-if="!model.isDefault"
                        size="small"
                        @click="handleSetDefault(model.id)"
                      >
                        Set Default
                      </NButton>
                      <NButton 
                        size="small"
                        quaternary
                        type="error"
                        @click="handleRemoveModel(model.id)"
                      >
                        <template #icon><NIcon><TrashOutline /></NIcon></template>
                      </NButton>
                    </NSpace>
                  </template>
                </NListItem>
              </NList>
            </div>

            <NDivider />

            <!-- Add New Model -->
            <NText class="section-label">Add New Model</NText>
            <NSpace vertical>
              <NInput 
                v-model:value="newModelConfig.name"
                placeholder="Model name (e.g., GPT-4, Claude)"
              />
              <NSelect 
                v-model:value="newModelConfig.provider"
                :options="providerOptions"
                placeholder="Select provider"
              />
              <NInput 
                v-model:value="newModelConfig.apiKey"
                type="password"
                placeholder="API Key"
                show-password-on="click"
              />
              <NInput 
                v-model:value="newModelConfig.baseUrl"
                placeholder="Base URL (optional)"
              />
              <NSpace>
                <NSwitch v-model:value="newModelConfig.isDefault">
                  Set as default
                </NSwitch>
                <NButton type="primary" @click="handleAddModel">
                  <template #icon><NIcon><AddOutline /></NIcon></template>
                  Add Model
                </NButton>
              </NSpace>
            </NSpace>
          </NSpace>
        </NCard>
      </NGi>

      <!-- App Settings -->
      <NGi>
        <NSpace vertical :size="16">
          <!-- Appearance -->
          <NCard title="Appearance">
            <NSpace vertical>
              <div class="setting-row">
                <div class="setting-info">
                  <NText>Theme</NText>
                  <NText depth="3" style="font-size: 12px">Choose your preferred theme</NText>
                </div>
                <NSpace>
                  <NButton 
                    :type="uiStore.settings.theme === 'dark' ? 'primary' : 'default'"
                    @click="uiStore.setTheme('dark')"
                  >
                    <template #icon><NIcon><MoonOutline /></NIcon></template>
                    Dark
                  </NButton>
                  <NButton 
                    :type="uiStore.settings.theme === 'light' ? 'primary' : 'default'"
                    @click="uiStore.setTheme('light')"
                  >
                    <template #icon><NIcon><SunnyOutline /></NIcon></template>
                    Light
                  </NButton>
                </NSpace>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <NText>Language</NText>
                  <NText depth="3" style="font-size: 12px">Select display language</NText>
                </div>
                <NSelect 
                  :value="uiStore.settings.language"
                  :options="languageOptions"
                  style="width: 150px"
                  @update:value="(v) => uiStore.updateSettings({ language: v })"
                />
              </div>
            </NSpace>
          </NCard>

          <!-- Privacy -->
          <NCard title="Privacy">
            <NSpace vertical>
              <div class="setting-row">
                <div class="setting-info">
                  <NText>Privacy Mode</NText>
                  <NText depth="3" style="font-size: 12px">Limit data collection and sharing</NText>
                </div>
                <NSwitch 
                  :value="uiStore.settings.privacyMode"
                  @update:value="(v) => uiStore.updateSettings({ privacyMode: v })"
                />
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <NText>Auto Save</NText>
                  <NText depth="3" style="font-size: 12px">Automatically save mission progress</NText>
                </div>
                <NSwitch 
                  :value="uiStore.settings.autoSave"
                  @update:value="(v) => uiStore.updateSettings({ autoSave: v })"
                />
              </div>
            </NSpace>
          </NCard>

          <!-- Data Export -->
          <NCard title="Data">
            <NSpace vertical>
              <NButton @click="handleExportData">
                <template #icon><NIcon><DownloadOutline /></NIcon></template>
                Export Settings
              </NButton>
              <NAlert type="info" style="font-size: 12px">
                Export your settings and configurations as a JSON file.
              </NAlert>
            </NSpace>
          </NCard>
        </NSpace>
      </NGi>
    </NGrid>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: var(--content-max-width);
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--spacing-6);
}

.page-header h1 {
  margin-bottom: var(--spacing-2);
}

.section-label {
  display: block;
  margin-bottom: var(--spacing-3);
  font-weight: 500;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-3) 0;
  border-bottom: 1px solid var(--color-border-light);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-1);
}
</style>
