import { describe, it, expect } from 'vitest'
import {
  AgentSchema,
  MissionSchema,
  isAgent,
  isMission,
  isTemplate,
  validateApiResponse,
  validatePaginatedResponse
} from '@/utils/validation'

describe('Validation Utils', () => {
  describe('Type Guards', () => {
    it('should validate agent', () => {
      const validAgent = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        name: 'Test Agent',
        description: 'Test',
        icon: 'https://example.com/icon.png',
        version: '1.0.0',
        permissions: [],
        status: 'idle'
      }

      expect(isAgent(validAgent)).toBe(true)
    })

    it('should reject invalid agent', () => {
      const invalidAgent = {
        id: 'invalid-id',
        name: 'Test Agent'
        // missing required fields
      }

      expect(isAgent(invalidAgent)).toBe(false)
    })

    it('should validate mission', () => {
      const validMission = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        title: 'Test Mission',
        status: 'pending',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        nodes: [],
        edges: [],
        logs: []
      }

      expect(isMission(validMission)).toBe(true)
    })

    it('should reject invalid mission', () => {
      const invalidMission = {
        id: 'invalid-id',
        title: 'Test Mission'
        // missing required fields
      }

      expect(isMission(invalidMission)).toBe(false)
    })
  })

  describe('API Response Validation', () => {
    it('should validate successful API response', () => {
      const response = {
        success: true,
        data: {
          id: '550e8400-e29b-41d4-a716-446655440000',
          name: 'Test Agent',
          description: 'Test',
          icon: 'https://example.com/icon.png',
          version: '1.0.0',
          permissions: [],
          status: 'idle'
        }
      }

      const result = validateApiResponse(response, AgentSchema)
      expect(result.name).toBe('Test Agent')
    })

    it('should throw on failed API response', () => {
      const response = {
        success: false,
        message: 'Error occurred'
      }

      expect(() => validateApiResponse(response, AgentSchema)).toThrow()
    })

    it('should throw on missing data', () => {
      const response = {
        success: true
      }

      expect(() => validateApiResponse(response, AgentSchema)).toThrow()
    })
  })

  describe('Paginated Response Validation', () => {
    it('should validate paginated response', () => {
      const response = {
        success: true,
        data: {
          items: [
            {
              id: '550e8400-e29b-41d4-a716-446655440000',
              name: 'Agent 1',
              description: 'Test',
              icon: 'https://example.com/icon.png',
              version: '1.0.0',
              permissions: [],
              status: 'idle'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 20
        }
      }

      const result = validatePaginatedResponse(response, AgentSchema)
      expect(result.items).toHaveLength(1)
      expect(result.total).toBe(1)
      expect(result.page).toBe(1)
    })

    it('should throw on invalid items', () => {
      const response = {
        success: true,
        data: {
          items: [{ invalid: 'data' }],
          total: 1
        }
      }

      expect(() => validatePaginatedResponse(response, AgentSchema)).toThrow()
    })
  })
})
