<script setup lang="ts">
import { ref, onMounted, watch, markRaw } from 'vue'
import { VueFlow, useVueFlow, type Node, type Edge } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import AgentNode from './AgentNode.vue'
import type { MissionNode, MissionEdge, AgentStatus, WSEvent } from '@/types'
import { useMissionStore } from '@/stores'
import { useWebSocketListener } from '@/composables/useWebSocket'

// Import Vue Flow styles
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

const props = defineProps<{
  missionId?: string
}>()

const emit = defineEmits<{
  (e: 'node-click', nodeId: string): void
}>()

const missionStore = useMissionStore()
const { onNodeClick, fitView } = useVueFlow()

// 将后端 node_update / agent_status_update 事件中的状态映射为画布可识别的节点状态
function normalizeStatus(raw: unknown): AgentStatus {
  const s = String(raw ?? '').toLowerCase()
  switch (s) {
    case 'running':
    case 'started':
      return 'running'
    case 'success':
    case 'completed':
      return 'success'
    case 'error':
    case 'failed':
      return 'error'
    case 'waiting':
      return 'waiting'
    default:
      return 'idle'
  }
}

// 处理 WebSocket 实时节点更新事件
function handleNodeUpdate(event: WSEvent) {
  // 仅处理当前任务的事件
  if (props.missionId && event.missionId && event.missionId !== props.missionId) return
  const payload = (event.payload || event.data || {}) as Record<string, unknown>
  const nodeId = String(payload.nodeId ?? '')
  const status = normalizeStatus(payload.status)
  if (!nodeId) return
  missionStore.updateNodeStatus(nodeId, status)
}

// 处理 Agent 状态更新事件
function handleAgentStatusUpdate(event: WSEvent) {
  if (props.missionId && event.missionId && event.missionId !== props.missionId) return
  const payload = (event.payload || event.data || {}) as Record<string, unknown>
  const agentId = String(payload.agentId ?? '')
  const status = normalizeStatus(payload.status)
  // Agent 事件可能用 agentId 关联节点 id
  if (!agentId) return
  missionStore.updateNodeStatus(agentId, status)
}

// 订阅 WebSocket 事件，画布节点状态随任务执行实时刷新
// immediate: true 会在挂载时自动连接并订阅任务专属频道
useWebSocketListener(['node_update', 'agent_status_update'], (event) => {
  if (event.type === 'node_update') {
    handleNodeUpdate(event)
  } else if (event.type === 'agent_status_update') {
    handleAgentStatusUpdate(event)
  }
}, { immediate: true, missionId: props.missionId })

// Node types
const nodeTypes = {
  agent: markRaw(AgentNode)
}

// Convert mission nodes to Vue Flow nodes
function convertNodes(nodes: MissionNode[]): Node[] {
  return nodes.map(node => ({
    id: node.id,
    type: 'agent',
    position: node.position,
    data: node.data
  }))
}

// Convert mission edges to Vue Flow edges
function convertEdges(edges: MissionEdge[]): Edge[] {
  return edges.map(edge => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    type: edge.type === 'dependency' ? 'smoothstep' : 'straight',
    animated: edge.animated,
    style: edge.type === 'negotiation' 
      ? { strokeDasharray: '5,5', stroke: '#6366f1' }
      : undefined
  }))
}

// Local state
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])

// Watch for store changes
watch(
  () => missionStore.nodes,
  (newNodes) => {
    nodes.value = convertNodes(newNodes)
  },
  { deep: true, immediate: true }
)

watch(
  () => missionStore.edges,
  (newEdges) => {
    edges.value = convertEdges(newEdges)
  },
  { deep: true, immediate: true }
)

// Handle node click
onNodeClick(({ node }) => {
  emit('node-click', node.id)
})

// Fit view on mount
onMounted(() => {
  setTimeout(() => fitView(), 100)
})
</script>

<template>
  <div class="collaboration-canvas">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :node-types="nodeTypes"
      :default-viewport="{ zoom: 1, x: 0, y: 0 }"
      :min-zoom="0.2"
      :max-zoom="4"
      fit-view-on-init
      class="vue-flow-container"
    >
      <Background pattern-color="#3a3a5c" :gap="20" />
      <Controls />
    </VueFlow>

    <!-- Empty State -->
    <div v-if="nodes.length === 0" class="empty-state">
      <p>No agents active yet.</p>
      <p class="hint">Start a mission to see the collaboration canvas.</p>
    </div>
  </div>
</template>

<style scoped>
.collaboration-canvas {
  width: 100%;
  height: 500px;
  position: relative;
  background-color: var(--color-bg-primary);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.vue-flow-container {
  width: 100%;
  height: 100%;
}

.empty-state {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: var(--color-text-tertiary);
}

.empty-state p {
  margin-bottom: var(--spacing-2);
}

.empty-state .hint {
  font-size: var(--font-size-sm);
}
</style>
