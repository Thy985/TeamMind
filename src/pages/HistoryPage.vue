<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NDataTable, NButton, NSpace, NTag, NInput, NSelect, NEmpty, NIcon, useMessage } from 'naive-ui'
import { PlayOutline, TrashOutline, CopyOutline, SearchOutline } from '@vicons/ionicons5'
import type { DataTableColumns } from 'naive-ui'
import type { MissionHistory } from '@/types'
import { missionApi } from '@/api/axios'

const router = useRouter()
const message = useMessage()

// State
const loading = ref(false)
const searchQuery = ref('')
const statusFilter = ref<string | null>(null)
const history = ref<MissionHistory[]>([])

// Table columns
const columns: DataTableColumns<MissionHistory> = [
  {
    title: 'Title',
    key: 'title',
    ellipsis: { tooltip: true }
  },
  {
    title: 'Status',
    key: 'status',
    width: 120,
    render(row) {
      const type = row.status === 'completed' ? 'success' : 
                   row.status === 'failed' ? 'error' : 
                   row.status === 'running' ? 'info' : 'warning'
      return h(NTag, { type, size: 'small' }, { default: () => row.status })
    }
  },
  {
    title: 'Created',
    key: 'createdAt',
    width: 180,
    render(row) {
      return new Date(row.createdAt).toLocaleString()
    }
  },
  {
    title: 'Completed',
    key: 'completedAt',
    width: 180,
    render(row) {
      return row.completedAt ? new Date(row.completedAt).toLocaleString() : '-'
    }
  },
  {
    title: 'Actions',
    key: 'actions',
    width: 150,
    render(row) {
      return h(NSpace, null, {
        default: () => [
          h(NButton, {
            size: 'small',
            quaternary: true,
            onClick: () => viewMission(row.id)
          }, { default: () => 'View' }),
          h(NButton, {
            size: 'small',
            quaternary: true,
            onClick: () => cloneMission(row.id)
          }, { default: () => 'Clone' }),
          h(NButton, {
            size: 'small',
            quaternary: true,
            type: 'error',
            onClick: () => deleteMission(row.id)
          }, { default: () => 'Delete' })
        ]
      })
    }
  }
]

// Status filter options
const statusOptions = [
  { label: 'All', value: '' },
  { label: 'Completed', value: 'completed' },
  { label: 'Running', value: 'running' },
  { label: 'Failed', value: 'failed' },
  { label: 'Paused', value: 'paused' }
]

// Actions
function viewMission(id: string) {
  router.push({ name: 'mission-detail', params: { id } })
}

async function cloneMission(id: string) {
  try {
    const res = await missionApi.clone(id)
    const newId = (res as any)?.data?.id || (res as any)?.id
    message.success('Mission cloned')
    if (newId) {
      router.push({ name: 'mission-detail', params: { id: newId } })
    } else {
      loadHistory()
    }
  } catch (e) {
    message.error('Failed to clone mission')
    console.error('Clone failed:', e)
  }
}

async function deleteMission(id: string) {
  try {
    await missionApi.delete(id)
    message.success('Mission deleted')
    loadHistory()
  } catch (e) {
    message.error('Failed to delete mission')
    console.error('Delete failed:', e)
  }
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await missionApi.list(1, 20)
    const items = (res as any)?.data?.items || (res as any)?.data || []
    history.value = items.map((m: any) => ({
      id: m.id,
      title: m.title,
      status: m.status === 'completed' ? 'completed' : m.status === 'running' ? 'running' : m.status === 'failed' ? 'failed' : 'paused',
      createdAt: m.createdAt,
      completedAt: m.completedAt
    }))
  } catch (e) {
    console.error('Failed to load history:', e)
  }
  loading.value = false
}

onMounted(loadHistory)
</script>

<template>
  <div class="history-page">
    <div class="page-header">
      <h1>Mission History</h1>
      <p>View and manage your past missions</p>
    </div>

    <!-- Filters -->
    <NCard class="filters-card">
      <NSpace>
        <NInput 
          v-model:value="searchQuery"
          placeholder="Search missions..."
          clearable
          style="width: 300px"
        >
          <template #prefix>
            <NIcon><SearchOutline /></NIcon>
          </template>
        </NInput>
        <NSelect 
          v-model:value="statusFilter"
          :options="statusOptions"
          placeholder="Filter by status"
          clearable
          style="width: 150px"
        />
      </NSpace>
    </NCard>

    <!-- Table -->
    <NCard>
      <NDataTable
        :columns="columns"
        :data="history"
        :loading="loading"
        :pagination="{ pageSize: 10 }"
        :row-key="(row: MissionHistory) => row.id"
      />
    </NCard>

    <!-- Empty State -->
    <NEmpty 
      v-if="!loading && history.length === 0"
      description="暂无任务记录，请在 Dashboard 创建新任务"
    >
      <template #extra>
        <NButton type="primary" @click="router.push('/')">
          Go to Dashboard
        </NButton>
      </template>
    </NEmpty>
  </div>
</template>

<style scoped>
.history-page {
  max-width: var(--content-max-width);
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--spacing-6);
}

.page-header h1 {
  margin-bottom: var(--spacing-2);
}

.filters-card {
  margin-bottom: var(--spacing-4);
}
</style>
