/**
 * Map a strategy outlook string (any casing/degree, e.g. "Moderately
 * Bullish") to the display label shown on strategy cards.
 */
export function outlookLabel(outlook: string): 'Bullish' | 'Bearish' | 'Neutral' {
  const lower = outlook.toLowerCase()
  if (lower.includes('bullish')) return 'Bullish'
  if (lower.includes('bearish')) return 'Bearish'
  return 'Neutral'
}
