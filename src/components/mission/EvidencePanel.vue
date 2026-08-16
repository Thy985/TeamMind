<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NTag, NIcon, NText, NSpace, NDataTable } from 'naive-ui'
import { DocumentTextOutline, CheckmarkCircleOutline, AlertCircleOutline, FileTrayOutline } from '@vicons/ionicons5'

interface Artifact {
  id: string
  type: string
  summary: string
  filesChanged?: number
  linesAdded?: number
  createdAt: string
}

interface Props {
  artifacts?: Artifact[]
}

const props = withDefaults(defineProps<Props>(), {
  artifacts: () => []
})

const columns = [
  { title: 'Type', key: 'type', width: 100 },
  { title: 'Summary', key: 'summary' },
  { title: 'Files', key: 'filesChanged', width: 80 },
  { title: 'Added', key: 'linesAdded', width: 80 },
  { title: 'Created', key: 'createdAt', width: 120 }
]

const tableData = computed(() => props.artifacts.map(a => ({
  ...a,
  type: a.type || 'UNKNOWN',
  createdAt: new Date(a.createdAt).toLocaleTimeString()
})))
</script>

<template>
  <NCard size="small" title="Artifacts">
    <div v-if="artifacts.length === 0" class="empty">
      <NText depth="3">No artifacts yet</NText>
    </div>
    <NDataTable
      v-else
      :columns="columns"
      :data="tableData"
      :pagination="false"
      size="small"
      :scroll-x="400"
    />
  </NCard>
</template>

<style scoped>
.empty {
  padding: 16px 0;
  text-align: center;
}
</style>
