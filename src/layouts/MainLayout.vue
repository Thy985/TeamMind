<script setup lang="ts">
import { computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NLayout, NLayoutHeader, NLayoutContent, NMenu, NButton, NIcon, NSpace, NText, NBadge } from 'naive-ui'
import { 
  HomeOutline, 
  TimeOutline, 
  AppsOutline, 
  FolderOutline, 
  SettingsOutline,
  MoonOutline,
  SunnyOutline,
  MenuOutline,
  AnalyticsOutline
} from '@vicons/ionicons5'
import { useUIStore } from '@/stores'

const router = useRouter()
const route = useRoute()
const uiStore = useUIStore()

const menuOptions = [
  {
    label: 'Dashboard',
    key: 'dashboard',
    icon: () => h(NIcon, null, { default: () => h(HomeOutline) })
  },
  {
    label: 'Mission Control',
    key: 'mission-control',
    icon: () => h(NIcon, null, { default: () => h(AnalyticsOutline) })
  },
  {
    label: 'History',
    key: 'history',
    icon: () => h(NIcon, null, { default: () => h(TimeOutline) })
  },
  {
    label: 'Market',
    key: 'market',
    icon: () => h(NIcon, null, { default: () => h(AppsOutline) })
  },
  {
    label: 'Templates',
    key: 'templates',
    icon: () => h(NIcon, null, { default: () => h(FolderOutline) })
  },
  {
    label: 'Settings',
    key: 'settings',
    icon: () => h(NIcon, null, { default: () => h(SettingsOutline) })
  }
]

const activeKey = computed(() => route.name as string)

function handleMenuSelect(key: string) {
  router.push({ name: key })
}
</script>

<template>
  <NLayout class="main-layout" position="absolute">
    <!-- Header -->
    <NLayoutHeader class="header" bordered>
      <div class="header-left">
        <NButton 
          quaternary 
          circle 
          class="menu-toggle"
          @click="uiStore.toggleSidebar"
        >
          <template #icon>
            <NIcon><MenuOutline /></NIcon>
          </template>
        </NButton>
        
        <div class="logo" @click="router.push('/')">
          <span class="logo-icon">🧠</span>
          <NText class="logo-text" strong>TeamMind</NText>
        </div>
      </div>
      
      <div class="header-center">
        <NMenu
          mode="horizontal"
          :value="activeKey"
          :options="menuOptions"
          @update:value="handleMenuSelect"
        />
      </div>
      
      <div class="header-right">
        <NSpace align="center">
          <NButton 
            quaternary 
            circle
            @click="uiStore.toggleTheme"
          >
            <template #icon>
              <NIcon>
                <MoonOutline v-if="uiStore.isDarkTheme" />
                <SunnyOutline v-else />
              </NIcon>
            </template>
          </NButton>
          
          <NBadge dot :show="false">
            <NButton quaternary circle>
              <template #icon>
                <NIcon><AppsOutline /></NIcon>
              </template>
            </NButton>
          </NBadge>
        </NSpace>
      </div>
    </NLayoutHeader>
    
    <!-- Content -->
    <NLayoutContent class="content">
      <router-view v-slot="{ Component }">
        <Transition name="fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </NLayoutContent>
  </NLayout>
</template>

<style scoped>
.main-layout {
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-4);
  height: var(--header-height);
  background-color: var(--color-bg-secondary);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  cursor: pointer;
  user-select: none;
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: var(--font-size-lg);
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.content {
  padding: var(--spacing-6);
  background-color: var(--color-bg-primary);
  overflow: auto;
}
</style>
