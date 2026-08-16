<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NGrid, NGi, NButton, NSpace, NTag, NIcon, NEmpty, NModal, NInput, NText, useMessage } from 'naive-ui'
import { AddOutline, PlayOutline, TrashOutline, CreateOutline } from '@vicons/ionicons5'
import TemplateCard from '@/components/common/TemplateCard.vue'
import { useTemplateStore } from '@/stores'
import type { TeamTemplate } from '@/types'

const router = useRouter()
const message = useMessage()
const templateStore = useTemplateStore()

// State
const showCreateModal = ref(false)
const showEditModal = ref(false)
const editingTemplate = ref<TeamTemplate | null>(null)
const newTemplate = ref({
  name: '',
  description: '',
  category: 'General',
  icon: '📄'
})

// Computed
const publicTemplates = computed(() => templateStore.templates.filter((t: TeamTemplate) => t.isPublic))
const myTemplates = computed(() => templateStore.templates.filter((t: TeamTemplate) => !t.isPublic))

// Actions
function handleUseTemplate(templateId: string) {
  router.push({ name: 'mission-new', query: { template: templateId } })
}

function handleEditTemplate(templateId: string) {
  const tpl = templateStore.templates.find(t => t.id === templateId)
  if (tpl) {
    editingTemplate.value = { ...tpl }
    showEditModal.value = true
  }
}

async function saveEditTemplate() {
  if (!editingTemplate.value) return
  try {
    await templateStore.updateTemplate(editingTemplate.value.id, {
      name: editingTemplate.value.name,
      description: editingTemplate.value.description,
      icon: editingTemplate.value.icon
    })
    message.success('Template updated')
    showEditModal.value = false
    editingTemplate.value = null
  } catch (e) {
    message.error('Failed to update template')
  }
}

function handleDeleteTemplate(templateId: string) {
  templateStore.deleteTemplate(templateId)
}

function handleCreateTemplate() {
  templateStore.createTemplate({
    name: newTemplate.value.name,
    description: newTemplate.value.description,
    icon: newTemplate.value.icon
  })
  showCreateModal.value = false
  newTemplate.value = { name: '', description: '', category: 'General', icon: '📄' }
}

onMounted(() => {
  templateStore.fetchTemplates()
})
</script>

<template>
  <div class="templates-page">
    <div class="page-header">
      <div>
        <h1>Team Templates</h1>
        <p>Manage and use pre-configured agent workflows</p>
      </div>
      <NButton type="primary" @click="showCreateModal = true">
        <template #icon><NIcon><AddOutline /></NIcon></template>
        Create Template
      </NButton>
    </div>

    <!-- My Templates -->
    <div v-if="myTemplates.length > 0" class="section">
      <h2>My Templates</h2>
      <NGrid :cols="4" :x-gap="16" :y-gap="16">
        <NGi v-for="template in myTemplates" :key="template.id">
          <TemplateCard 
            :template="template"
            @use="handleUseTemplate(template.id)"
            @edit="handleEditTemplate(template.id)"
            @delete="handleDeleteTemplate(template.id)"
          />
        </NGi>
      </NGrid>
    </div>

    <!-- Public Templates -->
    <div class="section">
      <h2>Public Templates</h2>
      <NGrid v-if="publicTemplates.length > 0" :cols="4" :x-gap="16" :y-gap="16">
        <NGi v-for="template in publicTemplates" :key="template.id">
          <TemplateCard 
            :template="template"
            :editable="false"
            @use="handleUseTemplate(template.id)"
          />
        </NGi>
      </NGrid>
      
      <NEmpty v-else description="No public templates available." />
    </div>

    <!-- Create Modal -->
    <NModal 
      v-model:show="showCreateModal"
      preset="card"
      title="Create New Template"
      style="width: 500px"
    >
      <NSpace vertical>
        <div>
          <NText class="form-label">Name</NText>
          <NInput v-model:value="newTemplate.name" placeholder="Template name" />
        </div>
        <div>
          <NText class="form-label">Description</NText>
          <NInput 
            v-model:value="newTemplate.description" 
            type="textarea"
            placeholder="Describe your template..."
          />
        </div>
        <div>
          <NText class="form-label">Icon</NText>
          <NInput v-model:value="newTemplate.icon" placeholder="📄" style="width: 100px" />
        </div>
      </NSpace>
      
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showCreateModal = false">Cancel</NButton>
          <NButton type="primary" @click="handleCreateTemplate">Create</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- Edit Modal -->
    <NModal
      v-model:show="showEditModal"
      preset="card"
      title="Edit Template"
      style="width: 500px"
    >
      <NSpace vertical v-if="editingTemplate">
        <div>
          <NText class="form-label">Name</NText>
          <NInput v-model:value="editingTemplate.name" placeholder="Template name" />
        </div>
        <div>
          <NText class="form-label">Description</NText>
          <NInput
            v-model:value="editingTemplate.description"
            type="textarea"
            placeholder="Describe your template..."
          />
        </div>
        <div>
          <NText class="form-label">Icon</NText>
          <NInput v-model:value="editingTemplate.icon" placeholder="📄" style="width: 100px" />
        </div>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showEditModal = false">Cancel</NButton>
          <NButton type="primary" @click="saveEditTemplate">Save</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.templates-page {
  max-width: var(--content-max-width);
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-6);
}

.page-header h1 {
  margin-bottom: var(--spacing-2);
}

.section {
  margin-bottom: var(--spacing-8);
}

.section h2 {
  margin-bottom: var(--spacing-4);
}

.form-label {
  display: block;
  margin-bottom: var(--spacing-2);
  font-weight: 500;
}
</style>
