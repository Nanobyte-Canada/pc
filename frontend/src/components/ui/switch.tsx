import * as React from "react"
import { cn } from "@/lib/utils"
import './switch.css'

interface SwitchProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'onChange'> {
  checked?: boolean
  onCheckedChange?: (checked: boolean) => void
}

const Switch = React.forwardRef<HTMLButtonElement, SwitchProps>(
  ({ className, checked = false, onCheckedChange, ...props }, ref) => (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      ref={ref}
      className={cn(
        'switch',
        checked && 'switch-checked',
        className
      )}
      onClick={() => onCheckedChange?.(!checked)}
      {...props}
    >
      <span className="switch-thumb" />
    </button>
  )
)
Switch.displayName = "Switch"

export { Switch }
