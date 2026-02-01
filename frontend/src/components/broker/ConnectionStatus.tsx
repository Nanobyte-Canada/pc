import type { ConnectionStatusType } from '../../types/broker'
import { connectionStatusColors, connectionStatusLabels } from '../../types/broker'

interface ConnectionStatusProps {
  status: ConnectionStatusType
}

export function ConnectionStatus({ status }: ConnectionStatusProps) {
  const color = connectionStatusColors[status]
  const label = connectionStatusLabels[status]

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '2px 8px',
        borderRadius: '12px',
        fontSize: '12px',
        fontWeight: 500,
        backgroundColor: `${color}20`,
        color: color
      }}
    >
      <span
        style={{
          width: '6px',
          height: '6px',
          borderRadius: '50%',
          backgroundColor: color,
          marginRight: '6px'
        }}
      />
      {label}
    </span>
  )
}
