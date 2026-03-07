import { useState } from 'react'

type Preset = '1y' | 'ytd' | 'all' | 'custom'

interface DateRangeSelectorProps {
  startDate: string
  endDate: string
  onDateChange: (start: string, end: string) => void
}

export function DateRangeSelector({ startDate, endDate, onDateChange }: DateRangeSelectorProps) {
  const [activePreset, setActivePreset] = useState<Preset>('all')
  const [customStart, setCustomStart] = useState(startDate)
  const [customEnd, setCustomEnd] = useState(endDate)

  const handlePreset = (preset: Preset) => {
    setActivePreset(preset)
    const now = new Date()
    const end = now.toISOString().split('T')[0]

    switch (preset) {
      case '1y': {
        const start = new Date(now.getFullYear() - 1, now.getMonth(), now.getDate())
        onDateChange(start.toISOString().split('T')[0], end)
        break
      }
      case 'ytd': {
        const start = new Date(now.getFullYear(), 0, 1)
        onDateChange(start.toISOString().split('T')[0], end)
        break
      }
      case 'all':
        onDateChange('', '')
        break
      case 'custom':
        break
    }
  }

  const handleApplyCustom = () => {
    onDateChange(customStart, customEnd)
  }

  return (
    <div className="date-range-selector">
      <div className="preset-buttons">
        {(['1y', 'ytd', 'all', 'custom'] as Preset[]).map(preset => (
          <button
            key={preset}
            className={`preset-btn ${activePreset === preset ? 'active' : ''}`}
            onClick={() => handlePreset(preset)}
          >
            {preset === '1y' ? '1 Year' : preset === 'ytd' ? 'Year to Date' : preset === 'all' ? 'All Time' : 'Custom'}
          </button>
        ))}
      </div>
      {activePreset === 'custom' && (
        <div className="custom-date-inputs">
          <input
            type="date"
            value={customStart}
            onChange={e => setCustomStart(e.target.value)}
          />
          <span className="date-separator">to</span>
          <input
            type="date"
            value={customEnd}
            onChange={e => setCustomEnd(e.target.value)}
          />
          <button className="apply-btn" onClick={handleApplyCustom}>Apply</button>
        </div>
      )}
    </div>
  )
}
