import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { AppSettings, ModelConfig } from '@/types'

const STORAGE_KEY = 'teammind-settings'

// Default settings
const defaultSettings: AppSettings = {
  theme: 'dark',
  modelConfigs: [],
  privacyMode: false,
  autoSave: true,
  language: 'zh-CN'
}

export const useUIStore = defineStore('ui', () => {
  // State
  const settings = ref<AppSettings>({ ...defaultSettings })
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
        settings.value = { ...defaultSettings, ...JSON.parse(stored) }
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
      id: `model-${Date.now()}`
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
