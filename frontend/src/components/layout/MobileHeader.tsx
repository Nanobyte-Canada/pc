import { useState } from 'react'
import { Menu } from 'lucide-react'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import { AppSidebar } from './AppSidebar'
import './MobileHeader.css'

export function MobileHeader() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <header className="mobile-header">
        <button
          onClick={() => setOpen(true)}
          className="mobile-header-menu-btn"
          aria-label="Open navigation"
        >
          <Menu style={{ height: '1.25rem', width: '1.25rem' }} />
        </button>
        <span className="mobile-header-title">Portfolio Builder</span>
      </header>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent onClose={() => setOpen(false)}>
          <AppSidebar onNavigate={() => setOpen(false)} />
        </SheetContent>
      </Sheet>
    </>
  )
}
