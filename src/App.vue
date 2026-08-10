<script setup lang="ts">
import { computed, provide } from 'vue'
import { 
  NConfigProvider, 
  NMessageProvider, 
  NDialogProvider, 
  NNotificationProvider,
  darkTheme,
  lightTheme,
  type GlobalThemeOverrides
} from 'naive-ui'
import MainLayout from '@/layouts/MainLayout.vue'
import { useUIStore } from '@/stores'

const uiStore = useUIStore()

// Theme
const theme = computed(() => uiStore.isDarkTheme ? darkTheme : lightTheme)

// Theme overrides
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#6366f1',
    primaryColorHover: '#818cf8',
    primaryColorPressed: '#4f46e5',
    borderRadius: '8px'
  }
}

// Provide message/dialog/notification for use in components
provide('message', null)
provide('dialog', null)
provide('notification', null)
</script>

<template>
  <NConfigProvider :theme="theme" :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <MainLayout />
        </NNotificationProvider>
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<style>
#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}
</style>
