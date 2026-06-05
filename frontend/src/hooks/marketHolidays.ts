// Market holidays for US (NYSE) and Canada (TSX).
// Options expiring on a holiday Friday shift to the preceding Thursday.
// Update annually — exchanges publish schedules each December.

const HOLIDAYS: Set<string> = new Set([
  // 2025
  '2025-01-01', // New Year's Day
  '2025-01-20', // MLK Day
  '2025-02-17', // Presidents' Day / Family Day (TSX)
  '2025-04-18', // Good Friday
  '2025-05-19', // Victoria Day (TSX)
  '2025-05-26', // Memorial Day
  '2025-06-19', // Juneteenth
  '2025-07-01', // Canada Day (TSX)
  '2025-07-04', // Independence Day
  '2025-09-01', // Labor Day / Labour Day
  '2025-10-13', // Thanksgiving (TSX)
  '2025-11-27', // Thanksgiving (NYSE)
  '2025-12-25', // Christmas

  // 2026
  '2026-01-01', // New Year's Day
  '2026-01-19', // MLK Day
  '2026-02-16', // Presidents' Day / Family Day (TSX)
  '2026-04-03', // Good Friday
  '2026-05-18', // Victoria Day (TSX)
  '2026-05-25', // Memorial Day
  '2026-06-19', // Juneteenth
  '2026-07-01', // Canada Day (TSX)
  '2026-07-03', // Independence Day (observed)
  '2026-09-07', // Labor Day / Labour Day
  '2026-10-12', // Thanksgiving (TSX)
  '2026-11-26', // Thanksgiving (NYSE)
  '2026-12-25', // Christmas

  // 2027
  '2027-01-01', // New Year's Day
  '2027-01-18', // MLK Day
  '2027-02-15', // Presidents' Day / Family Day (TSX)
  '2027-03-26', // Good Friday
  '2027-05-24', // Victoria Day (TSX)
  '2027-05-31', // Memorial Day
  '2027-06-18', // Juneteenth (observed)
  '2027-07-01', // Canada Day (TSX)
  '2027-07-05', // Independence Day (observed)
  '2027-09-06', // Labor Day / Labour Day
  '2027-10-11', // Thanksgiving (TSX)
  '2027-11-25', // Thanksgiving (NYSE)
  '2027-12-24', // Christmas (observed)
])

export function isMarketHoliday(dateStr: string): boolean {
  return HOLIDAYS.has(dateStr)
}

function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * For a given week, returns the actual option expiry date.
 * Normally Friday, but shifts to Thursday if Friday is a market holiday.
 */
export function getWeeklyExpiryDate(friday: Date): Date {
  const iso = toIso(friday)
  if (isMarketHoliday(iso)) {
    const thursday = new Date(friday)
    thursday.setDate(thursday.getDate() - 1)
    return thursday
  }
  return friday
}

function getThirdFriday(year: number, month: number): Date {
  const first = new Date(year, month, 1)
  const dayOfWeek = first.getDay()
  const firstFriday = dayOfWeek <= 5 ? (5 - dayOfWeek + 1) : (5 + 7 - dayOfWeek + 1)
  return new Date(year, month, firstFriday + 14)
}

/**
 * Checks if a date is a monthly option expiry.
 * Monthly expiry = 3rd Friday, or Thursday before if that Friday is a holiday.
 */
export function isMonthlyExpiry(dateStr: string): boolean {
  const d = new Date(dateStr + 'T00:00:00')
  const thirdFriday = getThirdFriday(d.getFullYear(), d.getMonth())
  const thirdFridayIso = toIso(thirdFriday)

  if (isMarketHoliday(thirdFridayIso)) {
    const thirdThursday = new Date(thirdFriday)
    thirdThursday.setDate(thirdThursday.getDate() - 1)
    return d.getDate() === thirdThursday.getDate()
  }

  return d.getDate() === thirdFriday.getDate()
}
