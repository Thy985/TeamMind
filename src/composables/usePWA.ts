/**
 * PWA Service Worker 注册和更新管理
 */
import { ref, onUnmounted } from 'vue'

export interface PWAState {
  updateAvailable: boolean
  updateReady: boolean
  offlineReady: boolean
  installPrompt: any
}

export function usePWA() {
  const state = ref<PWAState>({
    updateAvailable: false,
    updateReady: false,
    offlineReady: false,
    installPrompt: null
  })

  let updateCallback: (() => void) | null = null

  /**
   * 注册 Service Worker
   */
  async function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) {
      console.warn('Service Worker not supported')
      return
    }

    try {
      const registration = await navigator.serviceWorker.register('/sw.js', {
        scope: '/'
      })

      console.log('Service Worker registered:', registration)

      // 检查更新
      registration.addEventListener('updatefound', () => {
        const newWorker = registration.installing
        if (newWorker) {
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              state.value.updateAvailable = true
              state.value.updateReady = true
            }
          })
        }
      })

      // 离线就绪
      state.value.offlineReady = true

    } catch (error) {
      console.error('Service Worker registration failed:', error)
    }
  }

  /**
   * 监听安装提示
   */
  function listenForInstallPrompt() {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault()
      state.value.installPrompt = e
    })
  }

  /**
   * 触发安装
   */
  async function install() {
    if (!state.value.installPrompt) {
      return false
    }

    const promptEvent = state.value.installPrompt as any
    await promptEvent.prompt()
    const { outcome } = await promptEvent.userChoice

    if (outcome === 'accepted') {
      state.value.installPrompt = null
      return true
    }

    return false
  }

  /**
   * 应用更新
   */
  function applyUpdate() {
    if (state.value.updateReady && updateCallback) {
      updateCallback()
      state.value.updateReady = false
      state.value.updateAvailable = false
    }
  }

  /**
   * 设置更新回调
   */
  function onUpdateReady(callback: () => void) {
    updateCallback = callback
  }

  /**
   * 检查是否离线
   */
  function isOffline() {
    return !navigator.onLine
  }

  /**
   * 监听网络状态
   */
  function onNetworkChange(callback: (online: boolean) => void) {
    const handleOnline = () => callback(true)
    const handleOffline = () => callback(false)

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    onUnmounted(() => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    })
  }

  return {
    state,
    registerServiceWorker,
    listenForInstallPrompt,
    install,
    applyUpdate,
    onUpdateReady,
    isOffline,
    onNetworkChange
  }
}
