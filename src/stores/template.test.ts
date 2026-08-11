import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const { templateApiMock } = vi.hoisted(() => {
  return {
    templateApiMock: {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      clone: vi.fn()
    }
  }
})

vi.mock('@/api/axios', () => ({
  templateApi: templateApiMock
}))

import { useTemplateStore } from '@/stores/template'
import type { TeamTemplate } from '@/types'

describe('Template Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const template = (overrides: Partial<TeamTemplate> = {}): TeamTemplate => ({
    id: 't-1',
    name: 'Code Review',
    description: 'Workflow template',
    icon: '📝',
    category: 'workflow',
    agents: ['a-1'],
    createdAt: '2024-01-01',
    updatedAt: '2024-01-01',
    isPublic: true,
    usageCount: 0,
    ...overrides
  })

  describe('State & Computed', () => {
    it('should have initial state', () => {
      const store = useTemplateStore()
      expect(store.templates).toEqual([])
      expect(store.selectedTemplate).toBeNull()
      expect(store.searchQuery).toBe('')
    })

    it('filteredTemplates should filter by name/description', () => {
      const store = useTemplateStore()
      store.templates = [
        template({ id: '1', name: 'Code Review' }),
        template({ id: '2', name: 'Data Analysis', description: 'chart template' }),
        template({ id: '3', name: 'Text Summarizer' })
      ]

      store.searchQuery = 'code'
      expect(store.filteredTemplates.map(t => t.id)).toEqual(['1'])

      store.searchQuery = 'chart'
      expect(store.filteredTemplates.map(t => t.id)).toEqual(['2'])

      store.searchQuery = ''
      expect(store.filteredTemplates).toHaveLength(3)
    })

    it('templateCount should reflect template list size', () => {
      const store = useTemplateStore()
      store.templates = [template(), template()]
      expect(store.templateCount).toBe(2)
    })
  })

  describe('Actions', () => {
    it('fetchTemplates should populate templates', async () => {
      const store = useTemplateStore()
      templateApiMock.list.mockResolvedValue({ success: true, data: [template(), template()] })

      await store.fetchTemplates()

      expect(store.templates).toHaveLength(2)
    })

    it('fetchTemplate should set selectedTemplate', async () => {
      const store = useTemplateStore()
      templateApiMock.get.mockResolvedValue({ success: true, data: template({ id: 't-9' }) })

      await store.fetchTemplate('t-9')

      expect(store.selectedTemplate?.id).toBe('t-9')
    })

    it('createTemplate should create and refresh list', async () => {
      const store = useTemplateStore()
      templateApiMock.create.mockResolvedValue({ success: true, data: template({ id: 'new-1' }) })
      templateApiMock.list.mockResolvedValue({ success: true, data: [template({ id: 'new-1' })] })

      const result = await store.createTemplate({ name: 'New' })

      expect(templateApiMock.list).toHaveBeenCalled()
      expect(store.templates).toHaveLength(1)
      expect(result).toEqual(template({ id: 'new-1' }))
    })

    it('updateTemplate should update local template and selectedTemplate', async () => {
      const store = useTemplateStore()
      store.templates = [template({ id: 't-1' })]
      store.selectedTemplate = template({ id: 't-1' })
      templateApiMock.update.mockResolvedValue({ success: true })

      await store.updateTemplate('t-1', { name: 'Renamed' })

      expect(store.templates[0].name).toBe('Renamed')
      expect(store.selectedTemplate?.name).toBe('Renamed')
    })

    it('deleteTemplate should remove template and clear selection', async () => {
      const store = useTemplateStore()
      store.templates = [template({ id: 't-1' }), template({ id: 't-2' })]
      store.selectedTemplate = template({ id: 't-1' })
      templateApiMock.delete.mockResolvedValue({ success: true })

      await store.deleteTemplate('t-1')

      expect(store.templates.map(t => t.id)).toEqual(['t-2'])
      expect(store.selectedTemplate).toBeNull()
    })

    it('cloneTemplate should refresh list and return new id', async () => {
      const store = useTemplateStore()
      templateApiMock.clone.mockResolvedValue({ success: true, data: template({ id: 'clone-1' }) })
      templateApiMock.list.mockResolvedValue({ success: true, data: [template({ id: 'clone-1' })] })

      const newId = await store.cloneTemplate('t-1')

      expect(newId).toBe('clone-1')
      expect(store.templates).toHaveLength(1)
    })

    it('setSearchQuery / setSelectedTemplate / clearSelectedTemplate manage state', () => {
      const store = useTemplateStore()

      store.setSearchQuery('review')
      expect(store.searchQuery).toBe('review')

      store.setSelectedTemplate(template())
      expect(store.selectedTemplate).not.toBeNull()

      store.clearSelectedTemplate()
      expect(store.selectedTemplate).toBeNull()
    })
  })
})
