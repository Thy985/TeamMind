import { ref, computed } from 'vue'

/**
 * 用户引导系统
 */

export interface GuideStep {
  id: string
  title: string
  description: string
  target?: string // CSS 选择器
  position?: 'top' | 'bottom' | 'left' | 'right'
  action?: {
    label: string
    onClick: () => void
  }
  skip?: boolean
}

export interface GuideTour {
  id: string
  name: string
  steps: GuideStep[]
  autoStart?: boolean
  showProgress?: boolean
}

/**
 * 用户引导 Composable
 */
export function useGuide() {
  const currentTour = ref<GuideTour | null>(null)
  const currentStepIndex = ref(0)
  const completedTours = ref<Set<string>>(new Set())
  const isGuideActive = ref(false)

  // 从本地存储恢复已完成的引导
  function restoreCompletedTours() {
    const stored = localStorage.getItem('completedTours')
    if (stored) {
      completedTours.value = new Set(JSON.parse(stored))
    }
  }

  // 保存已完成的引导
  function saveCompletedTours() {
    localStorage.setItem('completedTours', JSON.stringify(Array.from(completedTours.value)))
  }

  // 当前步骤
  const currentStep = computed(() => {
    if (!currentTour.value) return null
    return currentTour.value.steps[currentStepIndex.value]
  })

  // 进度
  const progress = computed(() => {
    if (!currentTour.value) return 0
    return Math.round(((currentStepIndex.value + 1) / currentTour.value.steps.length) * 100)
  })

  // 是否是最后一步
  const isLastStep = computed(() => {
    if (!currentTour.value) return false
    return currentStepIndex.value === currentTour.value.steps.length - 1
  })

  /**
   * 开始引导
   */
  function startTour(tour: GuideTour) {
    currentTour.value = tour
    currentStepIndex.value = 0
    isGuideActive.value = true
  }

  /**
   * 下一步
   */
  function nextStep() {
    if (!currentTour.value) return

    if (isLastStep.value) {
      completeTour()
    } else {
      currentStepIndex.value++
    }
  }

  /**
   * 上一步
   */
  function previousStep() {
    if (currentStepIndex.value > 0) {
      currentStepIndex.value--
    }
  }

  /**
   * 跳过引导
   */
  function skipTour() {
    if (currentTour.value) {
      completedTours.value.add(currentTour.value.id)
      saveCompletedTours()
    }
    endTour()
  }

  /**
   * 完成引导
   */
  function completeTour() {
    if (currentTour.value) {
      completedTours.value.add(currentTour.value.id)
      saveCompletedTours()
    }
    endTour()
  }

  /**
   * 结束引导
   */
  function endTour() {
    currentTour.value = null
    currentStepIndex.value = 0
    isGuideActive.value = false
  }

  /**
   * 检查引导是否已完成
   */
  function isTourCompleted(tourId: string) {
    return completedTours.value.has(tourId)
  }

  /**
   * 重置引导
   */
  function resetTour(tourId: string) {
    completedTours.value.delete(tourId)
    saveCompletedTours()
  }

  /**
   * 重置所有引导
   */
  function resetAllTours() {
    completedTours.value.clear()
    localStorage.removeItem('completedTours')
  }

  return {
    currentTour,
    currentStepIndex,
    currentStep,
    progress,
    isLastStep,
    isGuideActive,
    completedTours,
    restoreCompletedTours,
    startTour,
    nextStep,
    previousStep,
    skipTour,
    completeTour,
    endTour,
    isTourCompleted,
    resetTour,
    resetAllTours
  }
}

/**
 * 预定义的引导
 */
export const predefinedTours: Record<string, GuideTour> = {
  // 新手引导
  gettingStarted: {
    id: 'getting-started',
    name: '快速开始',
    autoStart: true,
    showProgress: true,
    steps: [
      {
        id: 'welcome',
        title: '欢迎使用 TeamMind',
        description: '这是一个 AI Agent 协作平台。让我们快速了解如何使用它。',
        position: 'bottom'
      },
      {
        id: 'create-mission',
        title: '创建任务',
        description: '点击这里创建一个新的任务。',
        target: '.mission-launcher',
        position: 'bottom'
      },
      {
        id: 'view-history',
        title: '查看历史',
        description: '在这里可以查看所有的任务历史记录。',
        target: '[href*="history"]',
        position: 'bottom'
      },
      {
        id: 'agent-market',
        title: 'Agent 市场',
        description: '浏览和安装更多的 Agent。',
        target: '[href*="market"]',
        position: 'bottom'
      }
    ]
  },

  // 任务创建引导
  createMission: {
    id: 'create-mission',
    name: '创建任务',
    showProgress: true,
    steps: [
      {
        id: 'mission-title',
        title: '输入任务标题',
        description: '给你的任务起一个清晰的名称。',
        target: 'input[placeholder*="标题"]',
        position: 'bottom'
      },
      {
        id: 'mission-description',
        title: '添加描述',
        description: '详细描述你的任务需求。',
        target: 'textarea[placeholder*="描述"]',
        position: 'bottom'
      },
      {
        id: 'select-agents',
        title: '选择 Agent',
        description: '选择参与此任务的 Agent。',
        target: '.agent-selector',
        position: 'bottom'
      },
      {
        id: 'submit-mission',
        title: '提交任务',
        description: '点击提交按钮开始任务。',
        target: 'button[type="submit"]',
        position: 'top'
      }
    ]
  }
}
