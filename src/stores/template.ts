import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TeamTemplate } from '@/types'
import { templateApi } from '@/api/axios'

export const useTemplateStore = defineStore('template', () => {
  // ==================== State ====================
  const templates = ref<TeamTemplate[]>([])
  const selectedTemplate = ref<TeamTemplate | null>(null)
  const searchQuery = ref('')

  // ==================== Computed ====================
  const filteredTemplates = computed(() => {
    if (!searchQuery.value) return templates.value

    const query = searchQuery.value.toLowerCase()
    return templates.value.filter(
      (template: TeamTemplate) =>
        template.name.toLowerCase().includes(query) ||
        template.description?.toLowerCase().includes(query)
    )
  })

  const templateCount = computed(() => templates.value.length)

  // ==================== Actions ====================

  /**
   * 获取所有模板
   */
  async function fetchTemplates() {
    try {
      const response = await templateApi.list()
      if (response.success && response.data) {
        templates.value = Array.isArray(response.data) ? response.data : []
      }
    } catch (error) {
      console.error('Failed to fetch templates:', error)
      throw error
    }
  }

  /**
   * 获取单个模板详情
   */
  async function fetchTemplate(id: string) {
    try {
      const response = await templateApi.get(id)
      if (response.success && response.data) {
        selectedTemplate.value = response.data as TeamTemplate
      }
    } catch (error) {
      console.error('Failed to fetch template:', error)
      throw error
    }
  }

  /**
   * 创建模板
   */
  async function createTemplate(data: {
    name: string
    description?: string
    icon?: string
    config?: Record<string, unknown>
  }) {
    try {
      const response = await templateApi.create(data)
      if (response.success && response.data) {
        await fetchTemplates()
        return response.data
      }
    } catch (error) {
      console.error('Failed to create template:', error)
      throw error
    }
  }

  /**
   * 更新模板
   */
  async function updateTemplate(id: string, data: Partial<TeamTemplate>) {
    try {
      const response = await templateApi.update(id, data)
      if (response.success) {
        // 更新本地状态
        const template = templates.value.find((t: TeamTemplate) => t.id === id)
        if (template) {
          Object.assign(template, data)
        }
        if (selectedTemplate.value?.id === id) {
          Object.assign(selectedTemplate.value, data)
        }
      }
    } catch (error) {
      console.error('Failed to update template:', error)
      throw error
    }
  }

  /**
   * 删除模板
   */
  async function deleteTemplate(id: string) {
    try {
      const response = await templateApi.delete(id)
      if (response.success) {
        templates.value = templates.value.filter((t: TeamTemplate) => t.id !== id)
        if (selectedTemplate.value?.id === id) {
          selectedTemplate.value = null
        }
      }
    } catch (error) {
      console.error('Failed to delete template:', error)
      throw error
    }
  }

  /**
   * 克隆模板
   */
  async function cloneTemplate(id: string) {
    try {
      const response = await templateApi.clone(id)
      if (response.success && response.data) {
        await fetchTemplates()
        return response.data.id
      }
    } catch (error) {
      console.error('Failed to clone template:', error)
      throw error
    }
  }

  /**
   * 设置搜索查询
   */
  function setSearchQuery(query: string) {
    searchQuery.value = query
  }

  /**
   * 设置选中的模板
   */
  function setSelectedTemplate(template: TeamTemplate | null) {
    selectedTemplate.value = template
  }

  /**
   * 清空选中的模板
   */
  function clearSelectedTemplate() {
    selectedTemplate.value = null
  }

  return {
    // State
    templates,
    selectedTemplate,
    searchQuery,

    // Computed
    filteredTemplates,
    templateCount,

    // Actions
    fetchTemplates,
    fetchTemplate,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    cloneTemplate,
    setSearchQuery,
    setSelectedTemplate,
    clearSelectedTemplate
  }
})
