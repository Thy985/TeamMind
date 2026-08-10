import { describe, it, expect } from 'vitest'
import {
  generateUUID,
  delay,
  debounce,
  throttle,
  formatDate,
  formatRelativeTime,
  deepClone,
  mergeObjects,
  isEmpty,
  getNestedValue,
  setNestedValue,
  groupBy,
  unique,
  sortBy
} from '@/utils/common'

describe('Common Utils', () => {
  describe('generateUUID', () => {
    it('should generate valid UUID', () => {
      const uuid = generateUUID()
      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
      expect(uuid).toMatch(uuidRegex)
    })

    it('should generate unique UUIDs', () => {
      const uuid1 = generateUUID()
      const uuid2 = generateUUID()
      expect(uuid1).not.toBe(uuid2)
    })
  })

  describe('delay', () => {
    it('should delay execution', async () => {
      const start = Date.now()
      await delay(100)
      const elapsed = Date.now() - start
      expect(elapsed).toBeGreaterThanOrEqual(100)
    })
  })

  describe('debounce', () => {
    it('should debounce function calls', async () => {
      let callCount = 0
      const fn = () => callCount++
      const debouncedFn = debounce(fn, 100)

      debouncedFn()
      debouncedFn()
      debouncedFn()

      expect(callCount).toBe(0)

      await delay(150)
      expect(callCount).toBe(1)
    })
  })

  describe('throttle', () => {
    it('should throttle function calls', async () => {
      let callCount = 0
      const fn = () => callCount++
      const throttledFn = throttle(fn, 100)

      throttledFn()
      throttledFn()
      throttledFn()

      expect(callCount).toBe(1)

      await delay(150)
      throttledFn()
      expect(callCount).toBe(2)
    })
  })

  describe('formatDate', () => {
    it('should format date correctly', () => {
      const date = new Date('2026-03-27T12:30:45')
      expect(formatDate(date, 'YYYY-MM-DD HH:mm:ss')).toBe('2026-03-27 12:30:45')
    })

    it('should handle string dates', () => {
      const result = formatDate('2026-03-27T12:30:45', 'YYYY-MM-DD')
      expect(result).toBe('2026-03-27')
    })
  })

  describe('formatRelativeTime', () => {
    it('should format recent time as "Just now"', () => {
      const now = new Date()
      expect(formatRelativeTime(now)).toBe('Just now')
    })

    it('should format past time correctly', () => {
      const pastDate = new Date(Date.now() - 2 * 60 * 1000) // 2 minutes ago
      expect(formatRelativeTime(pastDate)).toContain('minute')
    })
  })

  describe('deepClone', () => {
    it('should deep clone objects', () => {
      const original = { a: 1, b: { c: 2 } }
      const cloned = deepClone(original)

      expect(cloned).toEqual(original)
      expect(cloned).not.toBe(original)
      expect(cloned.b).not.toBe(original.b)
    })

    it('should deep clone arrays', () => {
      const original = [1, [2, 3], { a: 4 }]
      const cloned = deepClone(original)

      expect(cloned).toEqual(original)
      expect(cloned).not.toBe(original)
      expect(cloned[1]).not.toBe(original[1])
    })

    it('should handle dates', () => {
      const date = new Date('2026-03-27')
      const cloned = deepClone(date)

      expect(cloned).toEqual(date)
      expect(cloned).not.toBe(date)
    })
  })

  describe('mergeObjects', () => {
    it('should merge objects', () => {
      const target = { a: 1, b: 2 }
      const source = { b: 3, c: 4 }
      const result = mergeObjects(target, source)

      expect(result).toEqual({ a: 1, b: 3, c: 4 })
    })

    it('should deep merge nested objects', () => {
      const target = { a: { b: 1 } }
      const source = { a: { c: 2 } }
      const result = mergeObjects(target, source)

      expect(result).toEqual({ a: { b: 1, c: 2 } })
    })
  })

  describe('isEmpty', () => {
    it('should detect empty values', () => {
      expect(isEmpty(null)).toBe(true)
      expect(isEmpty(undefined)).toBe(true)
      expect(isEmpty('')).toBe(true)
      expect(isEmpty('  ')).toBe(true)
      expect(isEmpty([])).toBe(true)
      expect(isEmpty({})).toBe(true)
    })

    it('should detect non-empty values', () => {
      expect(isEmpty('text')).toBe(false)
      expect(isEmpty([1])).toBe(false)
      expect(isEmpty({ a: 1 })).toBe(false)
    })
  })

  describe('getNestedValue', () => {
    it('should get nested value', () => {
      const obj = { a: { b: { c: 'value' } } }
      expect(getNestedValue(obj, 'a.b.c')).toBe('value')
    })

    it('should return default value if path not found', () => {
      const obj = { a: { b: 1 } }
      expect(getNestedValue(obj, 'a.c.d', 'default')).toBe('default')
    })
  })

  describe('setNestedValue', () => {
    it('should set nested value', () => {
      const obj: any = {}
      setNestedValue(obj, 'a.b.c', 'value')
      expect(obj.a.b.c).toBe('value')
    })
  })

  describe('groupBy', () => {
    it('should group array by key', () => {
      const arr = [
        { id: 1, type: 'a' },
        { id: 2, type: 'b' },
        { id: 3, type: 'a' }
      ]
      const grouped = groupBy(arr, 'type')

      expect(grouped.a).toHaveLength(2)
      expect(grouped.b).toHaveLength(1)
    })
  })

  describe('unique', () => {
    it('should remove duplicates', () => {
      const arr = [1, 2, 2, 3, 3, 3]
      expect(unique(arr)).toEqual([1, 2, 3])
    })

    it('should remove duplicates by key', () => {
      const arr = [
        { id: 1, name: 'a' },
        { id: 2, name: 'b' },
        { id: 1, name: 'c' }
      ]
      const result = unique(arr, 'id')
      expect(result).toHaveLength(2)
    })
  })

  describe('sortBy', () => {
    it('should sort array ascending', () => {
      const arr = [{ id: 3 }, { id: 1 }, { id: 2 }]
      const sorted = sortBy(arr, 'id', 'asc')
      expect(sorted[0].id).toBe(1)
      expect(sorted[2].id).toBe(3)
    })

    it('should sort array descending', () => {
      const arr = [{ id: 1 }, { id: 3 }, { id: 2 }]
      const sorted = sortBy(arr, 'id', 'desc')
      expect(sorted[0].id).toBe(3)
      expect(sorted[2].id).toBe(1)
    })
  })
})
