import { useRouter, useRoute } from 'vue-router'
import { ref, computed } from 'vue'

/**
 * 用户认证和权限管理
 */

export interface User {
  id: string
  name: string
  email: string
  roles: string[]
  permissions: string[]
}

export interface AuthState {
  user: User | null
  token: string | null
  isAuthenticated: boolean
}

/**
 * 认证 Composable
 */
export function useAuth() {
  const user = ref<User | null>(null)
  const token = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value && !!user.value)

  /**
   * 从本地存储恢复认证状态
   */
  function restoreAuth() {
    const storedToken = localStorage.getItem('token')
    const storedUser = localStorage.getItem('user')

    if (storedToken && storedUser) {
      token.value = storedToken
      try {
        user.value = JSON.parse(storedUser)
      } catch (error) {
        console.error('Failed to parse stored user:', error)
        clearAuth()
      }
    }
  }

  /**
   * 登录
   */
  async function login(email: string, password: string) {
    // 这里应该调用实际的登录 API
    // const response = await authApi.login(email, password)
    // token.value = response.token
    // user.value = response.user
    // localStorage.setItem('token', token.value)
    // localStorage.setItem('user', JSON.stringify(user.value))
  }

  /**
   * 登出
   */
  function logout() {
    clearAuth()
  }

  /**
   * 清除认证信息
   */
  function clearAuth() {
    user.value = null
    token.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  /**
   * 检查是否有特定权限
   */
  function hasPermission(permission: string): boolean {
    if (!user.value) return false
    return user.value.permissions.includes(permission)
  }

  /**
   * 检查是否有特定角色
   */
  function hasRole(role: string): boolean {
    if (!user.value) return false
    return user.value.roles.includes(role)
  }

  /**
   * 检查是否有任何权限
   */
  function hasAnyPermission(permissions: string[]): boolean {
    return permissions.some(permission => hasPermission(permission))
  }

  /**
   * 检查是否有所有权限
   */
  function hasAllPermissions(permissions: string[]): boolean {
    return permissions.every(permission => hasPermission(permission))
  }

  return {
    user,
    token,
    isAuthenticated,
    restoreAuth,
    login,
    logout,
    clearAuth,
    hasPermission,
    hasRole,
    hasAnyPermission,
    hasAllPermissions
  }
}

/**
 * 路由守卫
 */
export function setupRouteGuards() {
  const router = useRouter()
  const auth = useAuth()

  router.beforeEach(async (to, from, next) => {
    // 恢复认证状态
    if (!auth.user.value && !auth.token.value) {
      auth.restoreAuth()
    }

    // 检查是否需要认证
    const requiresAuth = to.meta.requiresAuth as boolean | undefined
    if (requiresAuth && !auth.isAuthenticated.value) {
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }

    // 检查权限
    const requiredPermissions = to.meta.permissions as string[] | undefined
    if (requiredPermissions && !auth.hasAllPermissions(requiredPermissions)) {
      next({ name: 'forbidden' })
      return
    }

    // 检查角色
    const requiredRole = to.meta.requiredRole as string | undefined
    if (requiredRole && !auth.hasRole(requiredRole)) {
      next({ name: 'forbidden' })
      return
    }

    // 数据预加载
    const preload = to.meta.preload as (() => Promise<void>) | undefined
    if (preload) {
      try {
        await preload()
      } catch (error) {
        console.error('Failed to preload data:', error)
        next({ name: 'error' })
        return
      }
    }

    next()
  })

  router.afterEach((to) => {
    // 更新页面标题
    const title = to.meta.title as string | undefined
    if (title) {
      document.title = `${title} | TeamMind`
    }
  })
}

/**
 * 权限检查 Composable
 */
export function usePermission() {
  const auth = useAuth()

  const can = (permission: string) => auth.hasPermission(permission)
  const cannot = (permission: string) => !auth.hasPermission(permission)
  const is = (role: string) => auth.hasRole(role)
  const isNot = (role: string) => !auth.hasRole(role)

  return {
    can,
    cannot,
    is,
    isNot
  }
}
