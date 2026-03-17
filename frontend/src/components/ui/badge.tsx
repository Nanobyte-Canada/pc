import * as React from "react"
import { cn } from "@/lib/utils"
import './badge.css'

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning'
}

function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return <div className={cn('badge', `badge-${variant}`, className)} {...props} />
}

export { Badge }
