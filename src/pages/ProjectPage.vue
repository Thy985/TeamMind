<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NButton, NInput, NModal, NForm, NFormItem,
  NDataTable, NSpace, NIcon, NText, NTag, NMessageProvider, useMessage
} from 'naive-ui'
import { AddOutline, RefreshOutline, SettingsOutline, CheckmarkCircleOutline, AlertCircleOutline } from '@vicons/ionicons5'
import axios from 'axios'

const router = useRouter()
const message = useMessage()

const loading = ref(false)
const projects = ref<any[]>([])
const showCreateModal = ref(false)
const newName = ref('')
const newDesc = ref('')
const newRootPath = ref('.')

interface CLIHealth {
  health: string
  processAlive: boolean
  pid: number
  command: string
  outputFormat: string
}

async function loadProjects() {
  try {
    loading.value = true
    const res = await axios.get('/api/projects')
    projects.value = res.data || []
  } catch (e) {
    console.error('Failed to load projects:', e)
    message.error('加载项目失败')
  } finally {
    loading.value = false
  }
}

async function createProject() {
  if (!newName.value.trim()) {
    message.warning('请输入项目名称')
    return
  }
  try {
    await axios.post('/api/projects', {
      name: newName.value,
      description: newDesc.value,
      rootPath: newRootPath.value || '.'
    })
    showCreateModal.value = false
    newName.value = ''
    newDesc.value = ''
    newRootPath.value = '.'
    await loadProjects()
    message.success('项目创建成功')
  } catch (e) {
    message.error('创建项目失败')
  }
}

async function checkCLIHealth(projectId: string) {
  try {
    const res = await axios.get(`/api/projects/${projectId}/cli-health`)
    const data = res.data
    const cliEntries = Object.entries(data.cliStatuses || {})
    const alive = cliEntries.filter(([, v]: any) => v.processAlive).length
    const total = cliEntries.length

    if (total === 0) {
      message.info('暂无已注册的 CLI 适配器')
    } else {
      message.success(`检测到 ${alive}/${total} 个 CLI 运行中`)
    }
  } catch (e) {
    message.error('CLI 健康检查失败')
  }
}

const columns = [
  { title: '名称', key: 'name', width: 200 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '根目录', key: 'rootPath', width: 300 },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render(row: any) {
      return [
        h(NButton, {
          size: 'small',
          type: 'primary',
          onClick: () => router.push(`/mission-control/${row.id}`)
        }, { default: () => '进入控制' }),
        h(NButton, {
          size: 'small',
          type: 'info',
          style: 'margin-left: 8px;',
          onClick: () => checkCLIHealth(row.id)
        }, { default: () => 'CLI 健康检查' })
      ]
    }
  }
]

function h(tag: any, props: any, children?: any) {
  return { __isVNode: true, type: tag, props, children }
}

onMounted(loadProjects)
</script>

<template>
  <div class="project-page">
    <div class="page-header">
      <div>
        <NText strong style="font-size:20px;">Projects</NText>
        <NText depth="3" style="font-size:13px;margin-left:12px;">管理你的 AI 协作项目</NText>
      </div>
      <NSpace>
        <NButton size="small" @click="loadProjects" :loading="loading">
          <template #icon><NIcon :component="RefreshOutline" /></template>
          刷新
        </NButton>
        <NButton type="primary" size="small" @click="showCreateModal = true">
          <template #icon><NIcon :component="AddOutline" /></template>
          新建项目
        </NButton>
      </NSpace>
    </div>

    <NCard :bordered="false" class="project-list">
      <NDataTable
        :columns="columns"
        :data="projects"
        :loading="loading"
        :pagination="false"
        empty-text="暂无项目，点击「新建项目」开始"
      />
    </NCard>

    <!-- Create Modal -->
    <NModal v-model:show="showCreateModal" preset="card" title="新建项目" style="width: 480px">
      <NForm label-placement="left" label-width="80">
        <NFormItem label="名称">
          <NInput v-model:value="newName" placeholder="输入项目名称" />
        </NFormItem>
        <NFormItem label="描述">
          <NInput v-model:value="newDesc" placeholder="可选" />
        </NFormItem>
        <NFormItem label="根目录">
          <NInput v-model:value="newRootPath" placeholder="项目根目录路径" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showCreateModal = false">取消</NButton>
          <NButton type="primary" @click="createProject">创建</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.project-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.project-list {
  border-radius: 12px;
}
</style>
