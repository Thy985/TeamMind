import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

import { useUIStore } from '@/stores/ui'

describe('UI Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  describe('State & Computed', () => {
    it('should have default settings', () => {
      const store = useUIStore()
      expect(store.settings.theme).toBe('dark')
      expect(store.settings.autoSave).toBe(true)
      expect(store.settings.language).toBe('zh-CN')
      expect(store.sidebarCollapsed).toBe(false)
      expect(store.isCommandPaletteOpen).toBe(false)
    })

    it('isDarkTheme should reflect theme', () => {
      const store = useUIStore()
      expect(store.isDarkTheme).toBe(true)

      store.setTheme('light')
      expect(store.isDarkTheme).toBe(false)
    })

    it('defaultModel should return the isDefault model config', () => {
      const store = useUIStore()
      store.settings.modelConfigs = [
        { id: 'm1', name: 'A', provider: 'x', isDefault: false },
        { id: 'm2', name: 'B', provider: 'y', isDefault: true }
      ]
      expect(store.defaultModel?.id).toBe('m2')
    })
  })

  describe('Settings persistence', () => {
    it('loadSettings should restore saved settings from localStorage', () => {
      localStorage.setItem('teammind-settings', JSON.stringify({ theme: 'light' }))
      const store = useUIStore()
      store.loadSettings()
      expect(store.settings.theme).toBe('light')
    })

    it('saveSettings should write to localStorage', () => {
      const store = useUIStore()
      store.saveSettings()
      const saved = JSON.parse(localStorage.getItem('teammind-settings') || '{}')
      expect(saved.theme).toBe('dark')
    })

    it('updateSettings should merge and persist', () => {
      const store = useUIStore()
      store.updateSettings({ autoSave: false, privacyMode: true })
      expect(store.settings.autoSave).toBe(false)
      expect(store.settings.privacyMode).toBe(true)
      const saved = JSON.parse(localStorage.getItem('teammind-settings') || '{}')
      expect(saved.privacyMode).toBe(true)
    })

    it('loadSettings should tolerate corrupt localStorage', () => {
      localStorage.setItem('teammind-settings', '{invalid json')
      const store = useUIStore()
      expect(() => store.loadSettings()).not.toThrow()
      // 保持默认设置
      expect(store.settings.theme).toBe('dark')
    })
  })

  describe('Actions', () => {
    it('toggleTheme should flip theme and persist', () => {
      const store = useUIStore()
      store.toggleTheme()
      expect(store.settings.theme).toBe('light')
      store.toggleTheme()
      expect(store.settings.theme).toBe('dark')
    })

    it('setTheme should set the theme', () => {
      const store = useUIStore()
      store.setTheme('light')
      expect(store.settings.theme).toBe('light')
    })

    it('addModelConfig should add and enforce single default', () => {
      const store = useUIStore()
      store.addModelConfig({ name: 'A', provider: 'x', isDefault: true })
      store.addModelConfig({ name: 'B', provider: 'y', isDefault: true })

      expect(store.settings.modelConfigs).toHaveLength(2)
      // 新添加的默认配置应把之前的默认取消
      expect(store.settings.modelConfigs[0].isDefault).toBe(false)
      expect(store.settings.modelConfigs[1].isDefault).toBe(true)
    })

    it('updateModelConfig should update fields and enforce single default', () => {
      const store = useUIStore()
      store.addModelConfig({ name: 'A', provider: 'x', isDefault: true })
      store.addModelConfig({ name: 'B', provider: 'y', isDefault: false })
      const idB = store.settings.modelConfigs[1].id

      store.updateModelConfig(idB, { isDefault: true })

      expect(store.settings.modelConfigs[0].isDefault).toBe(false)
      expect(store.settings.modelConfigs[1].isDefault).toBe(true)
    })

    it('removeModelConfig should remove the config', () => {
      const store = useUIStore()
      store.addModelConfig({ name: 'A', provider: 'x', isDefault: true })
      const id = store.settings.modelConfigs[0].id

      store.removeModelConfig(id)

      expect(store.settings.modelConfigs).toHaveLength(0)
    })

    it('toggleSidebar / toggleCommandPalette should flip flags', () => {
      const store = useUIStore()
      store.toggleSidebar()
      expect(store.sidebarCollapsed).toBe(true)

      store.toggleCommandPalette()
      expect(store.isCommandPaletteOpen).toBe(true)
    })
  })
})
