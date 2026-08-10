/**
 * 设计系统 - 颜色、排版、间距、动画
 */

// ==================== 颜色系统 ====================
export const colors = {
  // 主色
  primary: '#6366f1',
  primaryLight: '#818cf8',
  primaryDark: '#4f46e5',

  // 成功
  success: '#10b981',
  successLight: '#6ee7b7',
  successDark: '#059669',

  // 警告
  warning: '#f59e0b',
  warningLight: '#fcd34d',
  warningDark: '#d97706',

  // 错误
  error: '#ef4444',
  errorLight: '#fca5a5',
  errorDark: '#dc2626',

  // 信息
  info: '#3b82f6',
  infoLight: '#93c5fd',
  infoDark: '#1d4ed8',

  // 中性色
  gray50: '#f9fafb',
  gray100: '#f3f4f6',
  gray200: '#e5e7eb',
  gray300: '#d1d5db',
  gray400: '#9ca3af',
  gray500: '#6b7280',
  gray600: '#4b5563',
  gray700: '#374151',
  gray800: '#1f2937',
  gray900: '#111827'
}

// ==================== 排版系统 ====================
export const typography = {
  // 字体
  fontFamily: {
    base: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    mono: '"SF Mono", Monaco, "Cascadia Code", "Roboto Mono", Consolas, "Courier New", monospace'
  },

  // 字号
  fontSize: {
    xs: '12px',
    sm: '14px',
    base: '16px',
    lg: '18px',
    xl: '20px',
    '2xl': '24px',
    '3xl': '30px',
    '4xl': '36px'
  },

  // 行高
  lineHeight: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
    loose: 2
  },

  // 字重
  fontWeight: {
    light: 300,
    normal: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
    extrabold: 800
  }
}

// ==================== 间距系统 ====================
export const spacing = {
  0: '0',
  1: '4px',
  2: '8px',
  3: '12px',
  4: '16px',
  5: '20px',
  6: '24px',
  8: '32px',
  10: '40px',
  12: '48px',
  16: '64px',
  20: '80px',
  24: '96px'
}

// ==================== 圆角系统 ====================
export const borderRadius = {
  none: '0',
  sm: '4px',
  base: '8px',
  md: '12px',
  lg: '16px',
  xl: '20px',
  full: '9999px'
}

// ==================== 阴影系统 ====================
export const shadows = {
  none: 'none',
  sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
  base: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
  md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
  lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
  xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
  '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
  inner: 'inset 0 2px 4px 0 rgba(0, 0, 0, 0.06)'
}

// ==================== 动画系统 ====================
export const animations = {
  // 过渡时间
  duration: {
    fast: '150ms',
    base: '200ms',
    slow: '300ms',
    slower: '500ms'
  },

  // 缓动函数
  easing: {
    linear: 'linear',
    in: 'cubic-bezier(0.4, 0, 1, 1)',
    out: 'cubic-bezier(0, 0, 0.2, 1)',
    inOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    bounce: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)'
  }
}

// ==================== 断点系统 ====================
export const breakpoints = {
  xs: '320px',
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px'
}

// ==================== 组件尺寸 ====================
export const sizes = {
  // 按钮
  button: {
    xs: { height: '24px', padding: '0 8px', fontSize: '12px' },
    sm: { height: '32px', padding: '0 12px', fontSize: '14px' },
    base: { height: '40px', padding: '0 16px', fontSize: '16px' },
    lg: { height: '48px', padding: '0 20px', fontSize: '16px' },
    xl: { height: '56px', padding: '0 24px', fontSize: '18px' }
  },

  // 输入框
  input: {
    sm: { height: '32px', padding: '0 12px', fontSize: '14px' },
    base: { height: '40px', padding: '0 16px', fontSize: '16px' },
    lg: { height: '48px', padding: '0 20px', fontSize: '16px' }
  },

  // 图标
  icon: {
    xs: '16px',
    sm: '20px',
    base: '24px',
    lg: '32px',
    xl: '40px'
  }
}

// ==================== 导出 CSS 变量 ====================
export function generateCSSVariables() {
  const vars: Record<string, string> = {}

  // 颜色
  Object.entries(colors).forEach(([key, value]) => {
    vars[`--color-${key}`] = value
  })

  // 间距
  Object.entries(spacing).forEach(([key, value]) => {
    vars[`--spacing-${key}`] = value
  })

  // 圆角
  Object.entries(borderRadius).forEach(([key, value]) => {
    vars[`--radius-${key}`] = value
  })

  // 阴影
  Object.entries(shadows).forEach(([key, value]) => {
    vars[`--shadow-${key}`] = value
  })

  // 动画
  Object.entries(animations.duration).forEach(([key, value]) => {
    vars[`--duration-${key}`] = value
  })

  return vars
}

// ==================== CSS 字符串 ====================
export function generateCSSString() {
  const vars = generateCSSVariables()
  return `:root {\n${Object.entries(vars)
    .map(([key, value]) => `  ${key}: ${value};`)
    .join('\n')}\n}`
}
