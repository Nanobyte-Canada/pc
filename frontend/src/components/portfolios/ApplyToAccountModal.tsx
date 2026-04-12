import { useState } from 'react'
import { useBrokerConnections } from '@/hooks/useBrokerConnections'
import { useApplyModelToAccounts } from '@/hooks/useModelPortfolios'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'
import type { BrokerConnection } from '@/types/broker'
import './ApplyToAccountModal.css'

interface ApplyToAccountModalProps {
  modelId: number
  modelName: string
  isOpen: boolean
  onClose: () => void
}

export function ApplyToAccountModal({ modelId, modelName, isOpen, onClose }: ApplyToAccountModalProps) {
  const { data: connectionsData, isLoading: isLoadingConnections } = useBrokerConnections()
  const applyMutation = useApplyModelToAccounts()

  const [selectedConnectionIds, setSelectedConnectionIds] = useState<number[]>([])
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const connections = connectionsData?.connections ?? []
  const activeConnections = connections.filter(c => c.status === 'ACTIVE')

  const handleToggleConnection = (connectionId: number) => {
    setSelectedConnectionIds(prev =>
      prev.includes(connectionId)
        ? prev.filter(id => id !== connectionId)
        : [...prev, connectionId]
    )
  }

  const handleApply = async () => {
    if (selectedConnectionIds.length === 0) return
    setErrorMessage(null)

    try {
      await applyMutation.mutateAsync({ modelId, connectionIds: selectedConnectionIds })
      const count = selectedConnectionIds.length
      setSuccessMessage(`Model applied to ${count} account${count !== 1 ? 's' : ''}`)
      setSelectedConnectionIds([])

      // Close after showing success briefly
      setTimeout(() => {
        setSuccessMessage(null)
        onClose()
      }, 1500)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to apply model'
      setErrorMessage(message)
      console.error('Failed to apply model:', error)
    }
  }

  const handleClose = () => {
    if (!applyMutation.isPending) {
      setSelectedConnectionIds([])
      setSuccessMessage(null)
      setErrorMessage(null)
      onClose()
    }
  }

  const maskAccountNumber = (accountNumber: string | null): string => {
    if (!accountNumber) return '••••'
    const digits = accountNumber.replace(/\D/g, '')
    if (digits.length <= 4) return `••••${digits}`
    return `••••${digits.slice(-4)}`
  }

  const formatCurrency = (value: number | null): string => {
    if (value === null || value === undefined) return 'N/A'
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  const hasExistingModel = (connection: BrokerConnection): boolean => {
    return connection.modelPortfolioId != null
  }

  return (
    <Dialog open={isOpen} onOpenChange={handleClose}>
      <DialogContent onClose={handleClose}>
        <DialogHeader>
          <DialogTitle>Apply "{modelName}" to Accounts</DialogTitle>
          <DialogDescription>
            Select connected accounts to apply this model portfolio
          </DialogDescription>
        </DialogHeader>

        <div className="atam-content">
          {isLoadingConnections ? (
            <div className="atam-loading">
              <Loader2 className="atam-spinner" />
              <span>Loading accounts...</span>
            </div>
          ) : activeConnections.length === 0 ? (
            <div className="atam-empty">
              <AlertCircle size={32} />
              <p className="atam-empty__title">No connected accounts</p>
              <p className="atam-empty__subtitle">Connect a brokerage account first.</p>
              <Button onClick={() => window.location.href = '/brokers'}>
                Connect Brokerage
              </Button>
            </div>
          ) : successMessage ? (
            <div className="atam-success">
              <CheckCircle2 size={40} />
              <span>{successMessage}</span>
            </div>
          ) : (
            <>
              <div className="atam-accounts">
                {activeConnections.map(connection => {
                  const isSelected = selectedConnectionIds.includes(connection.id)
                  const existingModel = hasExistingModel(connection)

                  return (
                    <div
                      key={connection.id}
                      className={`atam-account ${isSelected ? 'atam-account--selected' : ''}`}
                      onClick={() => handleToggleConnection(connection.id)}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => handleToggleConnection(connection.id)}
                        className="atam-checkbox"
                        onClick={(e) => e.stopPropagation()}
                      />
                      <div className="atam-account__info">
                        <div className="atam-account__header">
                          <span className="atam-account__name">
                            {connection.accountName || 'Account'}
                          </span>
                          <span className="atam-account__value">
                            {formatCurrency(connection.totalValue)}
                          </span>
                        </div>
                        <div className="atam-account__meta">
                          <span className="atam-account__broker">
                            {connection.broker.name} · {maskAccountNumber(connection.accountNumber)}
                          </span>
                          {existingModel && (
                            <Badge variant="warning" className="atam-account__badge">
                              Has existing model
                            </Badge>
                          )}
                        </div>
                        {existingModel && (
                          <div className="atam-account__warning">
                            <AlertCircle size={14} />
                            <span>This will replace the current model</span>
                          </div>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>

              {errorMessage && (
                <div className="atam-error">
                  <AlertCircle size={16} />
                  <span>{errorMessage}</span>
                </div>
              )}

              <div className="atam-actions">
                <Button variant="outline" onClick={handleClose} disabled={applyMutation.isPending}>
                  Cancel
                </Button>
                <Button
                  onClick={handleApply}
                  disabled={selectedConnectionIds.length === 0 || applyMutation.isPending}
                >
                  {applyMutation.isPending ? (
                    <>
                      <Loader2 className="atam-btn-spinner" />
                      Applying...
                    </>
                  ) : (
                    `Apply Model (${selectedConnectionIds.length})`
                  )}
                </Button>
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
