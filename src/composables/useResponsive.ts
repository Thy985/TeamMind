import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

/**
 * 响应式设计工具
 */

export type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl'

const breakpoints = {
  xs: 320,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536
}

/**
 * 响应式 Composable
 */
export function useResponsive() {
  const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 0)

  const currentBreakpoint = computed<Breakpoint>(() => {
    const width = windowWidth.value
    if (width < breakpoints.sm) return 'xs'
    if (width < breakpoints.md) return 'sm'
    if (width < breakpoints.lg) return 'md'
    if (width < breakpoints.xl) return 'lg'
    if (width < breakpoints['2xl']) return 'xl'
    return '2xl'
  })

  const isXs = computed(() => currentBreakpoint.value === 'xs')
  const isSm = computed(() => currentBreakpoint.value === 'sm')
  const isMd = computed(() => currentBreakpoint.value === 'md')
  const isLg = computed(() => currentBreakpoint.value === 'lg')
  const isXl = computed(() => currentBreakpoint.value === 'xl')
  const is2xl = computed(() => currentBreakpoint.value === '2xl')

  const isMobile = computed(() => windowWidth.value < breakpoints.md)
  const isTablet = computed(
    () => windowWidth.value >= breakpoints.md && windowWidth.value < breakpoints.lg
  )
  const isDesktop = computed(() => windowWidth.value >= breakpoints.lg)

  const isSmallScreen = computed(() => windowWidth.value < breakpoints.lg)
  const isLargeScreen = computed(() => windowWidth.value >= breakpoints.lg)

  function handleResize() {
    windowWidth.value = window.innerWidth
  }

  onMounted(() => {
    window.addEventListener('resize', handleResize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
  })

  return {
    windowWidth,
    currentBreakpoint,
    isXs,
    isSm,
    isMd,
    isLg,
    isXl,
    is2xl,
    isMobile,
    isTablet,
    isDesktop,
    isSmallScreen,
    isLargeScreen
  }
}

/**
 * 媒体查询 Composable
 */
export function useMediaQuery(query: string) {
  const matches = ref(false)

  onMounted(() => {
    const mediaQuery = window.matchMedia(query)
    matches.value = mediaQuery.matches

    const handleChange = (e: MediaQueryListEvent) => {
      matches.value = e.matches
    }

    mediaQuery.addEventListener('change', handleChange)

    onBeforeUnmount(() => {
      mediaQuery.removeEventListener('change', handleChange)
    })
  })

  return matches
}

/**
 * 预定义的媒体查询
 */
export const mediaQueries = {
  // 屏幕尺寸
  mobile: '(max-width: 767px)',
  tablet: '(min-width: 768px) and (max-width: 1023px)',
  desktop: '(min-width: 1024px)',

  // 设备特性
  touchDevice: '(hover: none) and (pointer: coarse)',
  hoverDevice: '(hover: hover) and (pointer: fine)',
  darkMode: '(prefers-color-scheme: dark)',
  lightMode: '(prefers-color-scheme: light)',
  reducedMotion: '(prefers-reduced-motion: reduce)',

  // 方向
  portrait: '(orientation: portrait)',
  landscape: '(orientation: landscape)',

  // 高分辨率
  retina: '(-webkit-min-device-pixel-ratio: 2), (min-resolution: 192dpi)'
}

/**
 * 触摸设备检测
 */
export function useTouchDevice() {
  const isTouchDevice = computed(() => {
    return (
      typeof window !== 'undefined' &&
      ((navigator.maxTouchPoints || 0) > 0 ||
        'ontouchstart' in window)
    )
  })

  return { isTouchDevice }
}

/**
 * 暗色模式检测
 */
export function useDarkMode() {
  const isDarkMode = ref(false)

  onMounted(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    isDarkMode.value = mediaQuery.matches

    const handleChange = (e: MediaQueryListEvent) => {
      isDarkMode.value = e.matches
    }

    mediaQuery.addEventListener('change', handleChange)

    onBeforeUnmount(() => {
      mediaQuery.removeEventListener('change', handleChange)
    })
  })

  return { isDarkMode }
}
