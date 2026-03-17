import { cn } from '@/lib/utils'
import './separator.css'

interface SeparatorProps {
  className?: string
  orientation?: 'horizontal' | 'vertical'
}

export function Separator({ className, orientation = 'horizontal' }: SeparatorProps) {
  return (
    <div
      className={cn(
        'separator',
        orientation === 'horizontal' ? 'separator-horizontal' : 'separator-vertical',
        className
      )}
    />
  )
}
