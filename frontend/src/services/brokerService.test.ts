import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  getAvailableBrokers,
  getUserConnections,
  getAggregatedPositions,
  connectBroker,
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

  describe('getAvailableBrokers', () => {
    it('returns brokers list on success', async () => {
      const mockResponse = {
        brokers: [
          { name: 'Questrade', slug: 'questrade', logoUrl: null, description: 'Canadian brokerage' },
          { name: 'Wealthsimple', slug: 'wealthsimple', logoUrl: null, description: null }
        ]
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await getAvailableBrokers()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers', expect.any(Object))
    })

    it('throws error on failed response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500
      })

      await expect(getAvailableBrokers()).rejects.toThrow()
    })
  })

  describe('getUserConnections', () => {
    it('returns connections list on success', async () => {
      const mockResponse = {
        connections: [
          { id: 1, broker: { name: 'Questrade', slug: 'questrade' }, status: 'ACTIVE' }
        ]
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await getUserConnections()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers/connections', expect.any(Object))
    })
  })

  describe('connectBroker', () => {
    it('sends POST request to connect endpoint', async () => {
      const mockResponse = {
        redirectUrl: 'https://snaptrade.com/portal?token=abc'
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await connectBroker({ broker: 'questrade' })

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/brokers/connect',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ broker: 'questrade' })
        })
      )
    })

    it('sends POST request without broker slug', async () => {
      const mockResponse = {
        redirectUrl: 'https://snaptrade.com/portal?token=abc'
      }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await connectBroker()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/brokers/connect',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({})
        })
      )
    })
  })

  describe('getAggregatedPositions', () => {
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

      const result = await getAggregatedPositions()

      expect(result).toEqual(mockResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/brokers/positions', expect.any(Object))
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
      expect(formatCurrency(1234.56)).toMatch(/\$1,234\.56/)
    })

    it('formats negative numbers', () => {
      expect(formatCurrency(-1234.56)).toMatch(/-?\$1,234\.56/)
    })

    it('returns dash for null', () => {
      expect(formatCurrency(null)).toBe('-')
    })

    it('formats zero', () => {
      expect(formatCurrency(0)).toMatch(/\$0\.00/)
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

    it('formats with up to 4 decimal places', () => {
      expect(formatQuantity(100.1234)).toBe('100.1234')
    })
  })

  describe('getRelativeTime', () => {
    it('returns relative time string for recent date', () => {
      const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
      const result = getRelativeTime(fiveMinutesAgo)
      expect(result).toContain('min ago')
    })

    it('returns Never for null', () => {
      expect(getRelativeTime(null)).toBe('Never')
    })
  })
})
