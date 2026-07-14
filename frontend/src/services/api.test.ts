import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { getVersion, getHealth, ApiError } from './api'

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
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/version', expect.objectContaining({
        credentials: 'include'
      }))
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
      expect(mockFetch).toHaveBeenCalledWith('/health', expect.objectContaining({
        credentials: 'include'
      }))
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

describe('ApiError', () => {
  it('is an instance of Error', () => {
    const err = new ApiError(503, 'UNAVAILABLE', 'Service unavailable')
    expect(err).toBeInstanceOf(Error)
    expect(err).toBeInstanceOf(ApiError)
  })

  it('exposes status, code, and detail properties', () => {
    const err = new ApiError(503, 'UNAVAILABLE', 'IBKR Gateway down', 'Service Unavailable')
    expect(err.status).toBe(503)
    expect(err.code).toBe('UNAVAILABLE')
    expect(err.detail).toBe('IBKR Gateway down')
    expect(err.title).toBe('Service Unavailable')
    expect(err.message).toBe('IBKR Gateway down')
    expect(err.name).toBe('ApiError')
  })

  /**
   * Validates the 503 detection pattern used in WheelChainPanel and OptionsPage:
   * `err instanceof ApiError && err.status === 503`
   */
  it('503 ApiError is detected by instanceof and status check', () => {
    const err503 = new ApiError(503, 'UNAVAILABLE', 'Gateway unavailable')
    const err500 = new ApiError(500, 'INTERNAL', 'Internal error')
    const genericError = new Error('Network failure')

    // 503 ApiError should match the detection pattern
    expect(err503 instanceof ApiError && err503.status === 503).toBe(true)

    // 500 ApiError should NOT match
    expect(err500 instanceof ApiError && err500.status === 503).toBe(false)

    // Generic Error should NOT match
    expect(genericError instanceof ApiError && (genericError as ApiError).status === 503).toBe(false)
  })

  it('selects correct user-facing message for 503 vs other errors', () => {
    const ibkrMessage = (err: unknown) =>
      err instanceof ApiError && err.status === 503
        ? 'IBKR Gateway may be unavailable. Please check the connection and try again.'
        : 'Failed to load options chain. Please try again.'

    const err503 = new ApiError(503, 'UNAVAILABLE', 'Gateway unavailable')
    const err500 = new ApiError(500, 'INTERNAL', 'Internal error')
    const genericError = new Error('timeout')

    expect(ibkrMessage(err503)).toContain('IBKR Gateway may be unavailable')
    expect(ibkrMessage(err500)).toContain('Failed to load options chain')
    expect(ibkrMessage(genericError)).toContain('Failed to load options chain')
  })
})
