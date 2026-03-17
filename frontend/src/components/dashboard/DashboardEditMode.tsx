import { useState, useEffect } from 'react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import { WIDGET_REGISTRY, ZONE_A_WIDGETS, ZONE_B_WIDGETS } from './WidgetRegistry'
import type { WidgetPreference, WidgetKey } from '@/types/dashboard'
import './DashboardEditMode.css'

interface DashboardEditModeProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  preferences: WidgetPreference[]
  onSave: (preferences: WidgetPreference[]) => void
}

export function DashboardEditMode({ open, onOpenChange, preferences, onSave }: DashboardEditModeProps) {
  const [localPrefs, setLocalPrefs] = useState<WidgetPreference[]>(preferences)

  useEffect(() => {
    if (open) {
      setLocalPrefs(preferences)
    }
  }, [open, preferences])

  const handleToggle = (key: string, visible: boolean) => {
    setLocalPrefs(prev => prev.map(p => p.key === key ? { ...p, visible } : p))
  }

  const handleSave = () => {
    onSave(localPrefs)
    onOpenChange(false)
  }

  const cat1Prefs = localPrefs.filter(p => ZONE_A_WIDGETS.includes(p.key as WidgetKey))
  const cat2Prefs = localPrefs.filter(p => ZONE_B_WIDGETS.includes(p.key as WidgetKey))

  const renderItem = (pref: WidgetPreference) => {
    const widget = WIDGET_REGISTRY[pref.key as keyof typeof WIDGET_REGISTRY]
    if (!widget) return null
    return (
      <div key={pref.key} className="edit-mode-item">
        <span className="edit-mode-item-label">{widget.title}</span>
        <Switch
          checked={pref.visible}
          onCheckedChange={(checked) => handleToggle(pref.key, checked)}
        />
      </div>
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent onClose={() => onOpenChange(false)} style={{ maxWidth: '28rem' }}>
        <DialogHeader>
          <DialogTitle>Customize Dashboard</DialogTitle>
        </DialogHeader>
        <div className="edit-mode-list">
          {cat1Prefs.length > 0 && (
            <div>
              <p className="edit-mode-section-label">Portfolio Overview</p>
              {cat1Prefs.map(renderItem)}
            </div>
          )}
          {cat2Prefs.length > 0 && (
            <div>
              <p className="edit-mode-section-label">Activity &amp; Reports</p>
              {cat2Prefs.map(renderItem)}
            </div>
          )}
        </div>
        <div className="edit-mode-actions">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button onClick={handleSave}>Save</Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
