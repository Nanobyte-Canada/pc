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

export function WidgetWrapper({ title, columnSpan, children, className, headerAction, noPadding }: WidgetWrapperProps) {
  return (
    <div className={cn(
      'widget-card widget-wrapper',
      columnSpan === 4 ? 'widget-col-span-1 widget-col-span-2 widget-col-span-4' :
      columnSpan === 3 ? 'widget-col-span-1 widget-col-span-2 widget-col-span-3' :
      columnSpan === 2 ? 'widget-col-span-1 widget-col-span-2' :
      'widget-col-span-1',
      className
    )}>
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
