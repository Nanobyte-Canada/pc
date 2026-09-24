import { describe, it, expect } from 'vitest'
import { outlookLabel } from './outlookLabel'

describe('outlookLabel', () => {
  it('maps a moderately bullish outlook to Bullish', () => {
    expect(outlookLabel('Moderately Bullish')).toBe('Bullish')
  })

  it('maps a moderately bearish outlook to Bearish', () => {
    expect(outlookLabel('Moderately Bearish')).toBe('Bearish')
  })

  it('maps a range-bound outlook to Neutral', () => {
    expect(outlookLabel('Neutral (Range-Bound)')).toBe('Neutral')
  })

  it('defaults to Neutral for an unknown outlook', () => {
    expect(outlookLabel('Something Else')).toBe('Neutral')
  })
})
