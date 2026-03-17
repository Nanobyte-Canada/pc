import { Suspense } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import './WidgetWrapper.css'

interface WidgetWrapperProps {
  title: string
  columnSpan: number
  children: React.ReactNode
  className?: string
  headerAction?: React.ReactNode
  accentColor?: string
  noPadding?: boolean
}

function WidgetLoadingSkeleton() {
  return (
    <div className="widget-loading">
      <Skeleton style={{ height: '1rem', width: '75%' }} />
      <Skeleton style={{ height: '2rem', width: '50%' }} />
      <Skeleton style={{ height: '1rem', width: '100%' }} />
    </div>
  )
}

export function WidgetWrapper({ title, columnSpan, children, className, headerAction, accentColor, noPadding }: WidgetWrapperProps) {
  return (
    <div className={cn(
      'widget-card widget-wrapper',
      columnSpan === 2 ? 'widget-col-span-1 widget-col-span-2' :
      columnSpan === 3 ? 'widget-col-span-1 widget-col-span-2 widget-col-span-3' :
      'widget-col-span-1',
      className
    )}>
      {accentColor && (
        <div className="widget-accent-bar" style={{ background: accentColor }} />
      )}
      <div className="widget-header">
        <h3 className="widget-title">{title}</h3>
        {headerAction}
      </div>
      <div className={noPadding ? 'widget-body-no-padding' : 'widget-body'}>
        <Suspense fallback={<WidgetLoadingSkeleton />}>
          {children}
        </Suspense>
      </div>
    </div>
  )
}
