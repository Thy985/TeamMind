/**
 * Component registry for Playwright testing
 * Maps component names to their import paths and test configurations
 */
export const COMPONENT_REGISTRY: Record<string, {
  importName: string
  path: string
  props?: Record<string, unknown>
  assertions: string[]
}> = {
  CounterWidget: {
    importName: 'CounterWidget',
    path: '@/components/CounterWidget.vue',
    props: { initialCount: 0, label: 'Test Counter' },
    assertions: [
      'count-initial-0',
      'button-increment-clickable',
      'button-decrement-clickable',
      'doubled-value-correct',
      'status-indicator-visible'
    ]
  },
  AsyncLoader: {
    importName: 'AsyncLoader',
    path: '@/components/AsyncLoader.vue',
    props: { greeting: 'Hello from Async Loader!' },
    assertions: [
      'async-message-displayed',
      'loading-state-transient',
      'reload-button-clickable'
    ]
  }
}

export default COMPONENT_REGISTRY
