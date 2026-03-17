import { useRefreshAll } from '@/hooks/useDashboardWidgets'
import { Button } from '@/components/ui/button'
import { RefreshCw } from 'lucide-react'
import './RefreshButtonWidget.css'

export default function RefreshButtonWidget(_props: { connectionId?: number }) {
  const { mutate: refresh, isPending } = useRefreshAll()

  return (
    <div className="rb-wrapper">
      <Button
        onClick={() => refresh()}
        disabled={isPending}
        style={{ width: '100%' }}
      >
        <RefreshCw className={isPending ? 'animate-spin' : ''} style={{ height: '1rem', width: '1rem', marginRight: '0.5rem' }} />
        {isPending ? 'Refreshing...' : 'Refresh All Data'}
      </Button>
      {isPending && (
        <p className="rb-hint">Fetching latest data from brokers...</p>
      )}
    </div>
  )
}
