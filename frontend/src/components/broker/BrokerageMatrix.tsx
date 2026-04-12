import { useMemo } from 'react'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ICellRendererParams } from 'ag-grid-community'
import 'ag-grid-community/styles/ag-grid.css'
import 'ag-grid-community/styles/ag-theme-quartz.css'
import type { Broker } from '../../types/broker'
import './BrokerageMatrix.css'

interface BrokerageMatrixProps {
  brokers: Broker[]
  onConnect: (brokerSlug?: string) => void
  connectedSlugs: Set<string>
  isConnecting: boolean
}

const AUTH_TYPE_LABELS: Record<string, string> = {
  OAUTH: 'OAuth',
  SCRAPE: 'Credentials',
  UNOFFICIAL_API: 'API Key',
}

function LogoCellRenderer(params: ICellRendererParams<Broker>) {
  const broker = params.data
  if (!broker) return null
  return broker.logoUrl ? (
    <img src={broker.logoUrl} alt={broker.name} style={{ width: 24, height: 24, borderRadius: 4, objectFit: 'contain' }} />
  ) : (
    <span style={{ width: 24, height: 24, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', borderRadius: 4, background: 'var(--bg-primary)', fontWeight: 600, fontSize: 12 }}>
      {broker.name.charAt(0)}
    </span>
  )
}

function BooleanCellRenderer(params: ICellRendererParams) {
  if (params.value === true) return <span className="matrix-check">&#10003;</span>
  if (params.value === false) return <span className="matrix-cross">&#10007;</span>
  return <span className="matrix-na">-</span>
}

function AuthTypeCellRenderer(params: ICellRendererParams<Broker>) {
  const broker = params.data
  if (!broker?.authTypes?.length) return <span className="matrix-na">-</span>
  const types = new Set(broker.authTypes.map(at => at.authType))
  return (
    <span className="matrix-auth-types">
      {Array.from(types).map(t => (
        <span key={t} className={`matrix-auth-badge matrix-auth-${t.toLowerCase().replace('_', '-')}`}>
          {AUTH_TYPE_LABELS[t] ?? t}
        </span>
      ))}
    </span>
  )
}

function StatusCellRenderer(params: ICellRendererParams<Broker>) {
  const broker = params.data
  if (!broker) return null
  if (broker.maintenanceMode) return <span className="matrix-status-badge maintenance">Maintenance</span>
  if (broker.isDegraded) return <span className="matrix-status-badge degraded">Degraded</span>
  return <span className="matrix-status-badge active">Active</span>
}

export function BrokerageMatrix({ brokers, onConnect, connectedSlugs, isConnecting }: BrokerageMatrixProps) {
  const columnDefs = useMemo<ColDef<Broker>[]>(() => [
    { headerName: '', field: 'logoUrl', width: 50, cellRenderer: LogoCellRenderer, sortable: false, filter: false },
    { headerName: 'Brokerage', field: 'name', flex: 1, minWidth: 180 },
    { headerName: 'Auth Type', field: 'authTypes', width: 130, cellRenderer: AuthTypeCellRenderer, sortable: false },
    { headerName: 'Trading', field: 'allowsTrading', width: 95, cellRenderer: BooleanCellRenderer },
    { headerName: 'Fractional', field: 'allowsFractionalUnits', width: 100, cellRenderer: BooleanCellRenderer },
    { headerName: 'Real-time', field: 'isRealTimeConnection', width: 100, cellRenderer: BooleanCellRenderer },
    { headerName: 'Reporting', field: 'hasReporting', width: 100, cellRenderer: BooleanCellRenderer },
    { headerName: 'Status', field: 'maintenanceMode', width: 110, cellRenderer: StatusCellRenderer, sortable: false },
    {
      headerName: '',
      width: 100,
      sortable: false,
      filter: false,
      cellRenderer: (params: ICellRendererParams<Broker>) => {
        const broker = params.data
        if (!broker) return null
        const connected = broker.slug ? connectedSlugs.has(broker.slug) : false
        return (
          <button
            className={`matrix-connect-btn${connected ? ' connected' : ''}`}
            disabled={isConnecting || broker.maintenanceMode || broker.enabled === false}
            onClick={(e) => { e.stopPropagation(); onConnect(broker.slug || undefined) }}
          >
            {connected ? 'Add' : 'Connect'}
          </button>
        )
      },
    },
  ], [onConnect, connectedSlugs, isConnecting])

  return (
    <div className="ag-theme-quartz brokerage-matrix-container">
      <AgGridReact
        rowData={brokers}
        columnDefs={columnDefs}
        defaultColDef={{ sortable: true, resizable: true }}
        domLayout="autoHeight"
        animateRows={true}
        suppressCellFocus={true}
        rowStyle={{ cursor: 'pointer' }}
        onRowClicked={(event) => {
          if (event.data && !event.data.maintenanceMode && event.data.enabled !== false) {
            onConnect(event.data.slug || undefined)
          }
        }}
      />
    </div>
  )
}
