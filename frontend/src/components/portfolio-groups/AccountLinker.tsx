import type { LinkedAccount } from '../../types/portfolioGroup'
import type { BrokerConnection } from '../../types/broker'
import { formatCurrency } from '../../services/brokerService'

interface AccountLinkerProps {
  linkedAccounts: LinkedAccount[]
  availableConnections: BrokerConnection[]
  onLink: (connectionId: number) => void
  onUnlink: (connectionId: number) => void
  isLinking: boolean
}

export function AccountLinker({
  linkedAccounts,
  availableConnections,
  onLink,
  onUnlink,
  isLinking
}: AccountLinkerProps) {
  const linkedIds = new Set(linkedAccounts.map(a => a.connectionId))

  return (
    <div className="account-linker">
      <h4>Linked Accounts</h4>
      {linkedAccounts.length === 0 ? (
        <p className="text-muted">No accounts linked yet.</p>
      ) : (
        <div className="linked-accounts-list">
          {linkedAccounts.map(account => (
            <div key={account.connectionId} className="linked-account-item">
              <div className="linked-account-info">
                <span className="linked-account-name">{account.accountName || 'Unknown Account'}</span>
                <span className="linked-account-detail">
                  {account.accountNumber} | {account.accountType} | {formatCurrency(account.totalValue)}
                </span>
              </div>
              <button
                className="unlink-btn"
                onClick={() => onUnlink(account.connectionId)}
                disabled={isLinking}
              >
                Unlink
              </button>
            </div>
          ))}
        </div>
      )}

      {availableConnections.filter(c => !linkedIds.has(c.id) && c.status === 'ACTIVE').length > 0 && (
        <>
          <h4 style={{ marginTop: '1rem' }}>Available Accounts</h4>
          <div className="available-accounts-list">
            {availableConnections
              .filter(c => !linkedIds.has(c.id) && c.status === 'ACTIVE')
              .map(conn => (
                <div key={conn.id} className="available-account-item">
                  <div className="linked-account-info">
                    <span className="linked-account-name">{conn.accountName || 'Unknown Account'}</span>
                    <span className="linked-account-detail">
                      {conn.accountNumber} | {conn.accountType} | {formatCurrency(conn.totalValue)}
                    </span>
                  </div>
                  <button
                    className="link-btn"
                    onClick={() => onLink(conn.id)}
                    disabled={isLinking}
                  >
                    Link
                  </button>
                </div>
              ))}
          </div>
        </>
      )}
    </div>
  )
}
