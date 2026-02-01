import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  fetchAvailableBrokers,
  fetchBrokerConnections,
  fetchAggregatedPositions,
  fetchBrokerPreferences,
  updateBrokerPreferences,
  triggerPositionFetch,
  formatCurrency,
  formatPercent,
  formatQuantity,
  getRelativeTime
} from './brokerService'

describe('Broker Service', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('fetchAvailableBrokers', () => {
    it('returns brokers list on success', async () => {
      const mockResponse = {
        brokers: [
          { id: 1, code: 'QUESTRADE', name: 'Questrade', status: 'ACTIVE' },
          { id: 2, code: 'IBKR', name: 'Interactive Brokers', status: 'ACTIVE' }
        ]
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await fetchAvailableBrokers()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers', expect.any(Object))
    })

    it('throws error on failed response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500
      })

      await expect(fetchAvailableBrokers()).rejects.toThrow()
    })
  })

  describe('fetchBrokerConnections', () => {
    it('returns connections list on success', async () => {
      const mockResponse = {
        connections: [
          { id: 1, broker: { code: 'QUESTRADE', name: 'Questrade' }, status: 'ACTIVE' }
        ]
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await fetchBrokerConnections()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers/connections', expect.any(Object))
    })
  })

  describe('fetchAggregatedPositions', () => {
    it('returns aggregated positions on success', async () => {
      const mockResponse = {
        positions: [
          { symbol: 'VFV.TO', totalQuantity: 100, totalValue: 10500 }
        ],
        aggregateSummary: {
          totalValue: 10500,
          totalPnl: 500,
          accountCount: 1,
          brokerCount: 1
        }
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await fetchAggregatedPositions()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers/positions', expect.any(Object))
    })
  })

  describe('fetchBrokerPreferences', () => {
    it('returns preferences on success', async () => {
      const mockResponse = {
        autoFetchEnabled: true,
        fetchTimeUtc: '06:00'
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await fetchBrokerPreferences()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers/preferences', expect.any(Object))
    })
  })

  describe('updateBrokerPreferences', () => {
    it('sends PUT request with preferences', async () => {
      const mockResponse = {
        autoFetchEnabled: true,
        fetchTimeUtc: '07:00',
        message: 'Updated'
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await updateBrokerPreferences({
        autoFetchEnabled: true,
        fetchTimeUtc: '07:00'
      })

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/brokers/preferences',
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({ autoFetchEnabled: true, fetchTimeUtc: '07:00' })
        })
      )
    })
  })

  describe('triggerPositionFetch', () => {
    it('sends POST request to trigger fetch', async () => {
      const mockResponse = {
        fetchId: 'abc123',
        status: 'PENDING',
        message: 'Fetch initiated'
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await triggerPositionFetch(1)

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/brokers/connections/1/fetch',
        expect.objectContaining({ method: 'POST' })
      )
    })
  })
})

describe('Formatting utilities', () => {
  describe('formatCurrency', () => {
    it('formats positive numbers', () => {
      expect(formatCurrency(1234.56)).toBe('$1,234.56')
    })

    it('formats negative numbers', () => {
      expect(formatCurrency(-1234.56)).toBe('-$1,234.56')
    })

    it('returns dash for null', () => {
      expect(formatCurrency(null)).toBe('-')
    })

    it('returns dash for undefined', () => {
      expect(formatCurrency(undefined)).toBe('-')
    })

    it('formats zero', () => {
      expect(formatCurrency(0)).toBe('$0.00')
    })
  })

  describe('formatPercent', () => {
    it('formats positive percent with plus sign', () => {
      expect(formatPercent(12.345)).toBe('+12.35%')
    })

    it('formats negative percent', () => {
      expect(formatPercent(-5.678)).toBe('-5.68%')
    })

    it('returns dash for null', () => {
      expect(formatPercent(null)).toBe('-')
    })

    it('formats zero', () => {
      expect(formatPercent(0)).toBe('+0.00%')
    })
  })

  describe('formatQuantity', () => {
    it('formats whole numbers without decimals', () => {
      expect(formatQuantity(100)).toBe('100')
    })

    it('formats fractional quantities', () => {
      expect(formatQuantity(100.5)).toBe('100.5')
    })

    it('formats with specified decimals', () => {
      expect(formatQuantity(100.123, 2)).toBe('100.12')
    })

    it('returns dash for null', () => {
      expect(formatQuantity(null)).toBe('-')
    })
  })

  describe('getRelativeTime', () => {
    it('returns relative time string for recent date', () => {
      const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
      const result = getRelativeTime(fiveMinutesAgo)
      expect(result).toContain('minutes ago')
    })

    it('returns dash for null', () => {
      expect(getRelativeTime(null)).toBe('-')
    })

    it('returns dash for undefined', () => {
      expect(getRelativeTime(undefined)).toBe('-')
    })
  })
})
