import * as React from "react"
import { cn } from "@/lib/utils"
import { X } from "lucide-react"
import './dialog.css'

interface DialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  children: React.ReactNode
}

function Dialog({ open, onOpenChange, children }: DialogProps) {
  if (!open) return null

  return (
    <div className="dialog-backdrop">
      <div className="dialog-overlay" onClick={() => onOpenChange(false)} />
      <div className="dialog-positioner">
        {children}
      </div>
    </div>
  )
}

const DialogContent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement> & { onClose?: () => void }>(
  ({ className, children, onClose, ...props }, ref) => (
    <div
      ref={ref}
      className={cn("dialog-content", className)}
      {...props}
    >
      {children}
      {onClose && (
        <button className="dialog-close" onClick={onClose}>
          <X style={{ height: '1rem', width: '1rem' }} />
        </button>
      )}
    </div>
  )
)
DialogContent.displayName = "DialogContent"

function DialogHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("dialog-header", className)} {...props} />
}

function DialogTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h2 className={cn("dialog-title", className)} {...props} />
}

function DialogDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("dialog-description", className)} {...props} />
}

export { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription }
