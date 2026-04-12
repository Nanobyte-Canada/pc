import * as React from 'react'
import { cn } from '@/lib/utils'
import { X } from 'lucide-react'
import './sheet.css'

interface SheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  children: React.ReactNode
}

export function Sheet({ open, onOpenChange, children }: SheetProps) {
  React.useEffect(() => {
    if (open) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [open])

  if (!open) return null

  return (
    <div className="sheet-backdrop">
      <div className="sheet-overlay" onClick={() => onOpenChange(false)} />
      {children}
    </div>
  )
}

interface SheetContentProps extends React.HTMLAttributes<HTMLDivElement> {
  onClose?: () => void
  side?: 'left' | 'right'
}

export const SheetContent = React.forwardRef<HTMLDivElement, SheetContentProps>(
  ({ className, children, onClose, side = 'left', ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        'sheet-content',
        side === 'left' ? 'sheet-content-left' : 'sheet-content-right',
        className
      )}
      {...props}
    >
      {children}
      {onClose && (
        <button className="sheet-close" onClick={onClose}>
          <X style={{ height: '1rem', width: '1rem' }} />
        </button>
      )}
    </div>
  )
)
SheetContent.displayName = 'SheetContent'
