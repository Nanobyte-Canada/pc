import * as React from "react"
import { cn } from "@/lib/utils"
import './button.css'

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'
  size?: 'default' | 'sm' | 'lg' | 'icon'
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'default', size = 'default', ...props }, ref) => (
    <button
      className={cn(
        'btn',
        `btn-${variant}`,
        size !== 'default' && `btn-${size}`,
        className
      )}
      ref={ref}
      {...props}
    />
  )
)
Button.displayName = "Button"

export { Button }
