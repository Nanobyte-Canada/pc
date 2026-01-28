import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { getVersion, getHealth } from './api'

describe('API Service', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('getVersion', () => {
    it('returns version response on success', async () => {
      const mockResponse = { version: '1.0.0', environment: 'test' }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      })

      const result = await getVersion()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/version')
    })

    it('throws error on failed response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
      })

      await expect(getVersion()).rejects.toThrow('HTTP error! status: 500')
    })
  })

  describe('getHealth', () => {
    it('returns health response on success', async () => {
      const mockResponse = { status: 'UP', timestamp: '2024-01-01T00:00:00Z' }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      })

      const result = await getHealth()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/health')
    })

    it('throws error on failed response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 503,
      })

      await expect(getHealth()).rejects.toThrow('HTTP error! status: 503')
    })
  })
})
