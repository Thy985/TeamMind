<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NDataTable, NButton, NSpace, NTag, NInput, NSelect, NEmpty, NIcon } from 'naive-ui'
import { PlayOutline, TrashOutline, CopyOutline, SearchOutline } from '@vicons/ionicons5'
import type { DataTableColumns } from 'naive-ui'
import type { MissionHistory } from '@/types'

const router = useRouter()

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

function cloneMission(id: string) {
  // TODO: Implement clone
  console.log('Clone mission:', id)
}

function deleteMission(id: string) {
  // TODO: Implement delete
  console.log('Delete mission:', id)
}

// Fetch data
onMounted(async () => {
  loading.value = true
  // TODO: Fetch from API
  
  // Mock data
  history.value = [
    {
      id: '1',
      title: 'Code review for authentication module',
      status: 'completed',
      createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
      completedAt: new Date(Date.now() - 1 * 60 * 60 * 1000).toISOString()
    },
    {
      id: '2',
      title: 'Analyze user engagement metrics',
      status: 'running',
      createdAt: new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString()
    },
    {
      id: '3',
      title: 'Generate API documentation',
      status: 'failed',
      createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
      completedAt: new Date(Date.now() - 23 * 60 * 60 * 1000).toISOString()
    },
    {
      id: '4',
      title: 'Data pipeline ETL task',
      status: 'completed',
      createdAt: new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString(),
      completedAt: new Date(Date.now() - 47 * 60 * 60 * 1000).toISOString()
    }
  ]
  
  loading.value = false
})
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
      description="No missions found. Start a new mission from the Dashboard."
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
