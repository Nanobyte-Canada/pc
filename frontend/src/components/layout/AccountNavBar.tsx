import { useState } from 'react'
import { ChevronDown, X } from 'lucide-react'
import type { DashboardAccount } from '@/types/dashboard'
import './AccountNavBar.css'

export interface AccountNavBarProps {
  accounts: DashboardAccount[]
  selectedId: number | null  // null = "All Accounts"
  onSelect: (id: number | null) => void
}

function maskAccountNumber(num: string | null | undefined): string {
  if (!num) return ''
  const last4 = num.slice(-4)
  return `••${last4}`
}

function getBrokerBadge(brokerName: string): { letter: string; className: string } {
  const lower = brokerName.toLowerCase()
  if (lower.includes('questrade')) return { letter: 'Q', className: 'account-nav__broker-icon--questrade' }
  if (lower.includes('wealthsimple')) return { letter: 'W', className: 'account-nav__broker-icon--wealthsimple' }
  if (lower.includes('ibkr') || lower.includes('interactive')) return { letter: 'IB', className: 'account-nav__broker-icon--ibkr' }
  return { letter: brokerName.charAt(0).toUpperCase(), className: 'account-nav__broker-icon--questrade' }
}

export function AccountNavBar({ accounts, selectedId, onSelect }: AccountNavBarProps) {
  const [sheetOpen, setSheetOpen] = useState(false)

  const handleSelect = (id: number | null) => {
    onSelect(id)
    setSheetOpen(false)
  }

  const selectedAccount = selectedId
    ? accounts.find(a => a.connectionId === selectedId)
    : null

  const mobileLabel = selectedAccount
    ? (() => {
        const badge = getBrokerBadge(selectedAccount.brokerName)
        return {
          text: `${selectedAccount.accountType ?? 'Account'} ${maskAccountNumber(selectedAccount.accountNumber)}`,
          badge,
        }
      })()
    : null

  return (
    <div className="account-nav">
      {/* Desktop: pill tabs */}
      <div className="account-nav__pills">
        <button
          className={`account-nav__pill ${selectedId === null ? 'account-nav__pill--active' : ''}`}
          onClick={() => handleSelect(null)}
        >
          All Accounts
        </button>
        {accounts.map((account) => {
          const badge = getBrokerBadge(account.brokerName)
          return (
            <button
              key={account.connectionId}
              className={`account-nav__pill ${selectedId === account.connectionId ? 'account-nav__pill--active' : ''}`}
              onClick={() => handleSelect(account.connectionId)}
            >
              <span className={`account-nav__broker-icon ${badge.className}`}>
                {badge.letter}
              </span>
              <span className="account-nav__pill-type">
                {account.accountType ?? 'Account'}
              </span>
              <span className="account-nav__pill-number">
                {maskAccountNumber(account.accountNumber)}
              </span>
            </button>
          )
        })}
      </div>

      {/* Mobile: dropdown trigger + dots */}
      <div className="account-nav__mobile">
        <button
          className="account-nav__trigger"
          onClick={() => setSheetOpen(true)}
        >
          {mobileLabel ? (
            <>
              <span className={`account-nav__trigger-icon ${mobileLabel.badge.className}`}>
                {mobileLabel.badge.letter}
              </span>
              <span className="account-nav__trigger-label">{mobileLabel.text}</span>
            </>
          ) : (
            <span className="account-nav__trigger-label">ALL ACCOUNTS</span>
          )}
          <ChevronDown size={14} className="account-nav__trigger-chevron" />
        </button>
        <div className="account-nav__dots">
          <span
            className={`account-nav__dot ${selectedId === null ? 'account-nav__dot--active' : ''}`}
            onClick={() => handleSelect(null)}
          />
          {accounts.map(account => (
            <span
              key={account.connectionId}
              className={`account-nav__dot ${selectedId === account.connectionId ? 'account-nav__dot--active' : ''}`}
              onClick={() => handleSelect(account.connectionId)}
            />
          ))}
        </div>
      </div>

      {/* Mobile: bottom sheet */}
      {sheetOpen && (
        <div className="account-nav__sheet-overlay" onClick={() => setSheetOpen(false)}>
          <div className="account-nav__sheet" onClick={(e) => e.stopPropagation()}>
            <div className="account-nav__sheet-handle" />
            <div className="account-nav__sheet-header">
              <span className="account-nav__sheet-title">Select Account</span>
              <button className="account-nav__sheet-close" onClick={() => setSheetOpen(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <div className="account-nav__sheet-items">
              <button
                className={`account-nav__sheet-item ${selectedId === null ? 'account-nav__sheet-item--active' : ''}`}
                onClick={() => handleSelect(null)}
              >
                <span className="account-nav__sheet-item-label">All Accounts</span>
              </button>
              {accounts.map(account => {
                const badge = getBrokerBadge(account.brokerName)
                return (
                  <button
                    key={account.connectionId}
                    className={`account-nav__sheet-item ${selectedId === account.connectionId ? 'account-nav__sheet-item--active' : ''}`}
                    onClick={() => handleSelect(account.connectionId)}
                  >
                    <span className={`account-nav__broker-icon ${badge.className}`}>
                      {badge.letter}
                    </span>
                    <span className="account-nav__sheet-item-label">
                      {account.accountType ?? 'Account'} {maskAccountNumber(account.accountNumber)}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
