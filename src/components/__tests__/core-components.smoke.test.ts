import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import StructuredConsole from '@/components/common/StructuredConsole.vue'

describe('核心组件冒烟测试', () => {
  describe('EmptyState', () => {
    it('应渲染标题与默认描述', () => {
      const wrapper = mount(EmptyState, {
        props: { title: '暂无任务' }
      })
      expect(wrapper.find('.empty-state').exists()).toBe(true)
      expect(wrapper.find('.empty-state-title').text()).toContain('暂无任务')
      expect(wrapper.text()).toContain('暂无数据')
    })

    it('应渲染自定义描述与默认图标', () => {
      const wrapper = mount(EmptyState, {
        props: { title: '出错了', type: 'error' }
      })
      expect(wrapper.text()).toContain('出错了，请稍后重试')
      expect(wrapper.find('.empty-state-icon').text()).toContain('⚠️')
    })

    it('自定义 icon 应覆盖默认图标', () => {
      const wrapper = mount(EmptyState, {
        props: { title: 'x', icon: '🌟' }
      })
      expect(wrapper.find('.empty-state-icon').text()).toContain('🌟')
    })

    it('提供 action 时应渲染按钮并可点击', async () => {
      const onClick = vi.fn()
      const wrapper = mount(EmptyState, {
        props: { title: '空', action: { label: '去创建', onClick } }
      })
      expect(wrapper.text()).toContain('去创建')
      await wrapper.find('button').trigger('click')
      expect(onClick).toHaveBeenCalled()
    })
  })

  describe('SkeletonLoader', () => {
    it('默认渲染 card 类型且数量为 3', () => {
      const wrapper = mount(SkeletonLoader)
      expect(wrapper.find('.skeleton-loader').exists()).toBe(true)
      expect(wrapper.findAll('.skeleton-card')).toHaveLength(3)
    })

    it('应渲染指定数量的骨架项', () => {
      const wrapper = mount(SkeletonLoader, { props: { count: 5 } })
      expect(wrapper.findAll('.skeleton-card')).toHaveLength(5)
    })

    it('list 类型应渲染 list 骨架', () => {
      const wrapper = mount(SkeletonLoader, { props: { type: 'list', count: 2 } })
      expect(wrapper.findAll('.skeleton-list-item')).toHaveLength(2)
    })

    it('应应用 animated class', () => {
      const wrapper = mount(SkeletonLoader, { props: { animated: true } })
      expect(wrapper.find('.skeleton-loader.animated').exists()).toBe(true)
    })
  })

  describe('StructuredConsole', () => {
    const logs = [
      { id: '1', type: 'task', message: '开始任务', timestamp: '2024-01-01T10:00:00Z', agentName: 'planner' },
      { id: '2', type: 'error', message: '执行失败', timestamp: '2024-01-01T10:01:00Z', agentName: 'coder' },
      { id: '3', type: 'tool', message: '调用搜索工具', timestamp: '2024-01-01T10:02:00Z' }
    ]

    it('应渲染日志条目', () => {
      const wrapper = mount(StructuredConsole, { props: { logs } })
      expect(wrapper.find('.structured-console').exists()).toBe(true)
      expect(wrapper.findAll('.log-entry')).toHaveLength(3)
      expect(wrapper.text()).toContain('开始任务')
    })

    it('空日志时应显示空提示', () => {
      const wrapper = mount(StructuredConsole, { props: { logs: [] } })
      expect(wrapper.findAll('.log-entry')).toHaveLength(0)
      expect(wrapper.text()).toContain('No logs to display')
    })

    it('error 类型日志应带对应 class', () => {
      const wrapper = mount(StructuredConsole, { props: { logs } })
      const errorEntry = wrapper.find('.log-entry.log-type-error')
      expect(errorEntry.exists()).toBe(true)
      expect(errorEntry.text()).toContain('执行失败')
    })
  })
})
