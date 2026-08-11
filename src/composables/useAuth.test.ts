import { describe, it, expect, beforeEach, vi } from 'vitest'

const { authApiMock } = vi.hoisted(() => {
  return {
    authApiMock: {
      login: vi.fn(),
      me: vi.fn()
    }
  }
})

vi.mock('@/api/axios', () => ({
  authApi: authApiMock
}))

import { useAuth } from '@/composables/useAuth'

describe('useAuth composable', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('初始状态', () => {
    it('should start unauthenticated', () => {
      const auth = useAuth()
      expect(auth.user.value).toBeNull()
      expect(auth.token.value).toBeNull()
      expect(auth.isAuthenticated.value).toBe(false)
    })
  })

  describe('登录', () => {
    it('login 成功应写入 token/user 并持久化', async () => {
      const auth = useAuth()
      authApiMock.login.mockResolvedValue({
        success: true,
        data: {
          token: 'jwt-token',
          userId: 'u-1',
          username: 'admin',
          email: 'admin@teammind.dev',
          roles: ['ADMIN'],
          permissions: ['read:code']
        }
      })

      const data = await auth.login('admin', 'admin123')

      expect(auth.token.value).toBe('jwt-token')
      expect(auth.user.value?.id).toBe('u-1')
      expect(auth.user.value?.name).toBe('admin')
      expect(auth.isAuthenticated.value).toBe(true)
      expect(localStorage.getItem('token')).toBe('jwt-token')
      expect(JSON.parse(localStorage.getItem('user') || '{}').permissions).toContain('read:code')
      expect(data.token).toBe('jwt-token')
    })

    it('login 失败应抛出异常且不写入状态', async () => {
      const auth = useAuth()
      authApiMock.login.mockResolvedValue({ success: false, message: 'Invalid credentials' })

      await expect(auth.login('admin', 'wrong')).rejects.toThrow('Invalid credentials')
      expect(auth.isAuthenticated.value).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()
    })

    it('login 网络异常应向上传播', async () => {
      const auth = useAuth()
      authApiMock.login.mockRejectedValue(new Error('network down'))

      await expect(auth.login('admin', 'admin123')).rejects.toThrow('network down')
    })
  })

  describe('恢复认证', () => {
    it('restoreAuth 应恢复本地存储的认证状态', () => {
      localStorage.setItem('token', 'saved-token')
      localStorage.setItem('user', JSON.stringify({ id: 'u-1', name: 'admin', roles: [], permissions: ['read:code'] }))

      const auth = useAuth()
      auth.restoreAuth()

      expect(auth.token.value).toBe('saved-token')
      expect(auth.user.value?.id).toBe('u-1')
      expect(auth.isAuthenticated.value).toBe(true)
    })

    it('restoreAuth 对损坏的 user 数据应清除认证', () => {
      localStorage.setItem('token', 'saved-token')
      localStorage.setItem('user', '{bad json')

      const auth = useAuth()
      auth.restoreAuth()

      expect(auth.token.value).toBeNull()
      expect(auth.user.value).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
      expect(localStorage.getItem('user')).toBeNull()
    })
  })

  describe('登出与清除', () => {
    it('logout 应清除认证状态与本地存储', async () => {
      const auth = useAuth()
      localStorage.setItem('token', 't')
      localStorage.setItem('user', JSON.stringify({ id: 'u-1', name: 'a', roles: [], permissions: [] }))
      auth.restoreAuth()

      auth.logout()

      expect(auth.isAuthenticated.value).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()
      expect(localStorage.getItem('user')).toBeNull()
    })

    it('clearAuth 应清除状态与本地存储', () => {
      const auth = useAuth()
      auth.token.value = 't'
      auth.user.value = { id: 'u', name: 'a', email: '', roles: [], permissions: [] }

      auth.clearAuth()

      expect(auth.token.value).toBeNull()
      expect(auth.user.value).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  describe('权限与角色', () => {
    function authWithUser(permissions: string[], roles: string[] = []) {
      const auth = useAuth()
      auth.user.value = { id: 'u', name: 'a', email: '', roles, permissions }
      return auth
    }

    it('hasPermission 应校验用户权限', () => {
      const auth = authWithUser(['read:code', 'write:text'])
      expect(auth.hasPermission('read:code')).toBe(true)
      expect(auth.hasPermission('admin')).toBe(false)
    })

    it('hasPermission 对未登录用户应返回 false', () => {
      const auth = useAuth()
      expect(auth.hasPermission('read:code')).toBe(false)
    })

    it('hasRole 应校验用户角色', () => {
      const auth = authWithUser([], ['ADMIN'])
      expect(auth.hasRole('ADMIN')).toBe(true)
      expect(auth.hasRole('USER')).toBe(false)
    })

    it('hasAnyPermission 应满足任一权限', () => {
      const auth = authWithUser(['read:code'])
      expect(auth.hasAnyPermission(['admin', 'read:code'])).toBe(true)
      expect(auth.hasAnyPermission(['admin', 'write'])).toBe(false)
    })

    it('hasAllPermissions 应要求全部权限', () => {
      const auth = authWithUser(['read:code', 'write:text'])
      expect(auth.hasAllPermissions(['read:code', 'write:text'])).toBe(true)
      expect(auth.hasAllPermissions(['read:code', 'admin'])).toBe(false)
    })
  })
})
