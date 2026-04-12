import * as React from "react"
import { cn } from "@/lib/utils"
import './tooltip.css'

interface TooltipProps {
  content: React.ReactNode
  children: React.ReactNode
  side?: "top" | "bottom" | "left" | "right"
}

function Tooltip({ content, children, side = "top" }: TooltipProps) {
  const [isVisible, setIsVisible] = React.useState(false)

  return (
    <div
      className="tooltip-wrapper"
      onMouseEnter={() => setIsVisible(true)}
      onMouseLeave={() => setIsVisible(false)}
    >
      {children}
      {isVisible && (
        <div className={cn("tooltip-content", `tooltip-${side}`)}>
          {content}
        </div>
      )}
    </div>
  )
}

export { Tooltip }
