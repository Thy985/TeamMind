<script setup lang="ts">
import { ref, onMounted, watch, markRaw } from 'vue'
import { VueFlow, useVueFlow, type Node, type Edge } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import AgentNode from './AgentNode.vue'
import type { MissionNode, MissionEdge } from '@/types'
import { useMissionStore } from '@/stores'

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
      <Background pattern-color="#374151" :gap="20" />
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
