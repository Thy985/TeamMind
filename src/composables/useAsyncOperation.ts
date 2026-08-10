import { ref, computed, onBeforeUnmount } from 'vue'
import { classifyError, AppError } from '@/utils/errorHandler'

/**
 * 异步操作状态
 */
export interface AsyncState<T> {
  data: T | null
  isLoading: boolean
  error: AppError | null
  isSuccess: boolean
  isError: boolean
}

/**
 * 通用异步操作 Composable
 * 统一管理加载状态、错误处理和数据管理
 */
export function useAsyncOperation<T>(
  asyncFn: () => Promise<T>,
  options?: {
    immediate?: boolean
    onSuccess?: (data: T) => void
    onError?: (error: AppError) => void
    onFinally?: () => void
  }
) {
  const data = ref<T | null>(null)
  const isLoading = ref(false)
  const error = ref<AppError | null>(null)
  let abortController: AbortController | null = null

  const isSuccess = computed(() => !isLoading.value && !error.value && data.value !== null)
  const isError = computed(() => !isLoading.value && error.value !== null)

  const execute = async () => {
    // 如果已经在加载中，则不重复执行
    if (isLoading.value) {
      return
    }

    isLoading.value = true
    error.value = null

    try {
      data.value = await asyncFn()
      options?.onSuccess?.(data.value)
    } catch (err) {
      error.value = classifyError(err)
      options?.onError?.(error.value)
    } finally {
      isLoading.value = false
      options?.onFinally?.()
    }
  }

  const reset = () => {
    data.value = null
    isLoading.value = false
    error.value = null
  }

  const retry = () => {
    error.value = null
    return execute()
  }

  const cancel = () => {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    isLoading.value = false
  }

  if (options?.immediate) {
    execute()
  }

  // 组件卸载时取消操作
  onBeforeUnmount(() => {
    cancel()
  })

  return {
    data,
    isLoading,
    error,
    isSuccess,
    isError,
    execute,
    reset,
    retry,
    cancel
  }
}

/**
 * 分页异步操作 Composable
 */
export function usePaginatedAsyncOperation<T>(
  asyncFn: (page: number, pageSize: number) => Promise<{ items: T[]; total: number }>,
  options?: {
    initialPage?: number
    initialPageSize?: number
    onSuccess?: (data: T[]) => void
    onError?: (error: AppError) => void
  }
) {
  const items = ref<T[]>([])
  const total = ref(0)
  const page = ref(options?.initialPage ?? 1)
  const pageSize = ref(options?.initialPageSize ?? 20)
  const isLoading = ref(false)
  const error = ref<AppError | null>(null)

  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
  const hasNextPage = computed(() => page.value < totalPages.value)
  const hasPreviousPage = computed(() => page.value > 1)

  const execute = async (p = page.value, ps = pageSize.value) => {
    isLoading.value = true
    error.value = null

    try {
      const result = await asyncFn(p, ps)
      items.value = result.items
      total.value = result.total
      page.value = p
      pageSize.value = ps
      options?.onSuccess?.(items.value as any)
    } catch (err) {
      error.value = classifyError(err)
      options?.onError?.(error.value)
    } finally {
      isLoading.value = false
    }
  }

  const goToPage = (p: number) => {
    if (p >= 1 && p <= totalPages.value) {
      return execute(p, pageSize.value)
    }
  }

  const nextPage = () => {
    if (hasNextPage.value) {
      return execute(page.value + 1, pageSize.value)
    }
  }

  const previousPage = () => {
    if (hasPreviousPage.value) {
      return execute(page.value - 1, pageSize.value)
    }
  }

  const changePageSize = (ps: number) => {
    return execute(1, ps)
  }

  const reset = () => {
    items.value = []
    total.value = 0
    page.value = options?.initialPage ?? 1
    pageSize.value = options?.initialPageSize ?? 20
    error.value = null
  }

  return {
    items,
    total,
    page,
    pageSize,
    isLoading,
    error,
    totalPages,
    hasNextPage,
    hasPreviousPage,
    execute,
    goToPage,
    nextPage,
    previousPage,
    changePageSize,
    reset
  }
}

/**
 * 防抖异步操作 Composable
 */
export function useDebouncedAsyncOperation<T>(
  asyncFn: () => Promise<T>,
  delay = 500,
  options?: {
    onSuccess?: (data: T) => void
    onError?: (error: AppError) => void
  }
) {
  const data = ref<T | null>(null)
  const isLoading = ref(false)
  const error = ref<AppError | null>(null)
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  const execute = () => {
    // 清除之前的定时器
    if (timeoutId) {
      clearTimeout(timeoutId)
    }

    timeoutId = setTimeout(async () => {
      isLoading.value = true
      error.value = null

      try {
        data.value = await asyncFn()
        options?.onSuccess?.(data.value)
      } catch (err) {
        error.value = classifyError(err)
        options?.onError?.(error.value)
      } finally {
        isLoading.value = false
      }
    }, delay)
  }

  const cancel = () => {
    if (timeoutId) {
      clearTimeout(timeoutId)
      timeoutId = null
    }
  }

  onBeforeUnmount(() => {
    cancel()
  })

  return {
    data,
    isLoading,
    error,
    execute,
    cancel
  }
}
