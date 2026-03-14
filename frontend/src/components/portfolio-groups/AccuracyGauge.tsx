import './AccuracyGauge.css'

interface AccuracyGaugeProps {
  accuracy: number
  size?: number
}

export function AccuracyGauge({ accuracy, size = 120 }: AccuracyGaugeProps) {
  const radius = (size - 12) / 2
  const circumference = 2 * Math.PI * radius
  const clampedAccuracy = Math.max(0, Math.min(100, accuracy))
  const offset = circumference - (clampedAccuracy / 100) * circumference

  const getColor = () => {
    if (clampedAccuracy >= 80) return '#059669'
    if (clampedAccuracy >= 50) return '#d97706'
    return '#dc2626'
  }

  const color = getColor()

  return (
    <div className="accuracy-gauge" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle
          className="gauge-bg"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth="8"
        />
        <circle
          className="gauge-fill"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth="8"
          stroke={color}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <div className="gauge-label">
        <span className="gauge-value" style={{ color }}>{Math.round(clampedAccuracy)}%</span>
        <span className="gauge-text">Accuracy</span>
      </div>
    </div>
  )
}
