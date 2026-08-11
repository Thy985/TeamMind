import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { AppSettings, ModelConfig } from '@/types'

const STORAGE_KEY = 'teammind-settings'

// 用于生成唯一模型配置 id 的自增序列（避免同毫秒内 Date.now() 冲突）
let modelConfigSeq = 0

// Default settings
const defaultSettings: AppSettings = {
  theme: 'dark',
  modelConfigs: [],
  privacyMode: false,
  autoSave: true,
  language: 'zh-CN'
}

/**
 * 深拷贝默认设置，避免不同 store 实例共享 modelConfigs 数组引用导致状态泄漏。
 */
function cloneDefaultSettings(): AppSettings {
  return {
    ...defaultSettings,
    modelConfigs: defaultSettings.modelConfigs.map(m => ({ ...m }))
  }
}

export const useUIStore = defineStore('ui', () => {
  // State
  const settings = ref<AppSettings>(cloneDefaultSettings())
  const sidebarCollapsed = ref(false)
  const isCommandPaletteOpen = ref(false)

  // Getters
  const isDarkTheme = computed(() => settings.value.theme === 'dark')
  
  const defaultModel = computed(() => 
    settings.value.modelConfigs.find(m => m.isDefault)
  )

  // Actions
  function loadSettings() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        settings.value = {
          ...cloneDefaultSettings(),
          ...parsed,
          // 合并 modelConfigs 时同样做浅拷贝，避免与持久化数组共享引用
          modelConfigs: (parsed.modelConfigs ?? []).map((m: ModelConfig) => ({ ...m }))
        }
      } else {
        // 无持久化数据时重置为默认深拷贝，保证实例隔离
        settings.value = cloneDefaultSettings()
      }
    } catch (e) {
      console.error('Failed to load settings:', e)
    }
  }

  function saveSettings() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
    } catch (e) {
      console.error('Failed to save settings:', e)
    }
  }

  function updateSettings(updates: Partial<AppSettings>) {
    settings.value = { ...settings.value, ...updates }
    saveSettings()
  }

  function toggleTheme() {
    settings.value.theme = settings.value.theme === 'dark' ? 'light' : 'dark'
    saveSettings()
  }

  function setTheme(theme: 'dark' | 'light') {
    settings.value.theme = theme
    saveSettings()
  }

  function addModelConfig(config: Omit<ModelConfig, 'id'>) {
    const newConfig: ModelConfig = {
      ...config,
      id: `model-${Date.now()}-${modelConfigSeq++}`
    }
    if (config.isDefault) {
      settings.value.modelConfigs.forEach(m => m.isDefault = false)
    }
    settings.value.modelConfigs.push(newConfig)
    saveSettings()
  }

  function updateModelConfig(id: string, updates: Partial<ModelConfig>) {
    const config = settings.value.modelConfigs.find(m => m.id === id)
    if (config) {
      if (updates.isDefault) {
        settings.value.modelConfigs.forEach(m => m.isDefault = false)
      }
      Object.assign(config, updates)
      saveSettings()
    }
  }

  function removeModelConfig(id: string) {
    settings.value.modelConfigs = settings.value.modelConfigs.filter(m => m.id !== id)
    saveSettings()
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleCommandPalette() {
    isCommandPaletteOpen.value = !isCommandPaletteOpen.value
  }

  // Watch for theme changes and apply to document
  watch(() => settings.value.theme, (theme) => {
    document.documentElement.setAttribute('data-theme', theme)
  }, { immediate: true })

  // Initialize
  loadSettings()

  return {
    // State
    settings,
    sidebarCollapsed,
    isCommandPaletteOpen,
    // Getters
    isDarkTheme,
    defaultModel,
    // Actions
    loadSettings,
    saveSettings,
    updateSettings,
    toggleTheme,
    setTheme,
    addModelConfig,
    updateModelConfig,
    removeModelConfig,
    toggleSidebar,
    toggleCommandPalette
  }
})
