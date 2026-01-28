import { useState, useEffect } from 'react';
import {
  triggerFullIngestion,
  triggerUniverseRefresh,
  triggerStockIngestion,
  triggerEtfIngestion,
  triggerStockEnrichment,
  triggerEtfEnrichment,
  getIngestionRuns,
  getRecentErrors,
  type IngestionRun,
  type IngestionError,
  type TriggerIngestionResponse,
} from '../../services/adminService';
import './AdminPage.css';

interface ActionButtonProps {
  label: string;
  description: string;
  onTrigger: () => Promise<TriggerIngestionResponse>;
}

function ActionButton({ label, description, onTrigger }: ActionButtonProps) {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);

  const handleClick = async () => {
    setLoading(true);
    setResult(null);

    try {
      const response = await onTrigger();
      setResult({
        success: true,
        message: `Run #${response.runId} started - ${response.status}`,
      });
    } catch (error) {
      setResult({
        success: false,
        message: error instanceof Error ? error.message : 'Unknown error',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="action-card">
      <h3 className="action-title">{label}</h3>
      <p className="action-description">{description}</p>
      <button
        className="action-button"
        onClick={handleClick}
        disabled={loading}
      >
        {loading ? 'Running...' : 'Trigger'}
      </button>
      {result && (
        <div className={`action-result ${result.success ? 'success' : 'error'}`}>
          {result.message}
        </div>
      )}
    </div>
  );
}

export function AdminPage() {
  const [runs, setRuns] = useState<IngestionRun[]>([]);
  const [errors, setErrors] = useState<IngestionError[]>([]);
  const [runsLoading, setRunsLoading] = useState(true);
  const [errorsLoading, setErrorsLoading] = useState(true);

  const fetchData = async () => {
    try {
      setRunsLoading(true);
      const runsData = await getIngestionRuns(10);
      setRuns(runsData);
    } catch (error) {
      console.error('Failed to fetch runs:', error);
    } finally {
      setRunsLoading(false);
    }

    try {
      setErrorsLoading(true);
      const errorsData = await getRecentErrors(20);
      setErrors(errorsData);
    } catch (error) {
      console.error('Failed to fetch errors:', error);
    } finally {
      setErrorsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const formatDate = (dateString: string | null) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  };

  const getStatusClass = (status: string) => {
    switch (status.toUpperCase()) {
      case 'COMPLETED':
      case 'SUCCESS':
        return 'status-success';
      case 'FAILED':
      case 'ERROR':
        return 'status-error';
      case 'RUNNING':
      case 'PENDING':
        return 'status-pending';
      default:
        return '';
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-header">
        <h1 className="admin-title">Admin Dashboard</h1>
        <button className="refresh-button" onClick={fetchData}>
          Refresh Data
        </button>
      </div>

      {/* Ingestion Actions */}
      <section className="admin-section">
        <h2 className="section-title">Ingestion Actions</h2>
        <div className="actions-grid">
          <ActionButton
            label="Full Ingestion"
            description="Run complete ingestion pipeline (universe + stocks + ETFs + enrichment)"
            onTrigger={triggerFullIngestion}
          />
          <ActionButton
            label="Universe Refresh"
            description="Refresh universe data from EODHD"
            onTrigger={triggerUniverseRefresh}
          />
          <ActionButton
            label="Stock Ingestion"
            description="Fetch raw stock data from Alpha Vantage"
            onTrigger={triggerStockIngestion}
          />
          <ActionButton
            label="ETF Ingestion"
            description="Fetch raw ETF data from Alpha Vantage"
            onTrigger={triggerEtfIngestion}
          />
          <ActionButton
            label="Stock Enrichment"
            description="Parse and map stored stock raw payload"
            onTrigger={triggerStockEnrichment}
          />
          <ActionButton
            label="ETF Enrichment"
            description="Parse and map stored ETF raw payload"
            onTrigger={triggerEtfEnrichment}
          />
        </div>
      </section>

      {/* Recent Runs */}
      <section className="admin-section">
        <h2 className="section-title">Recent Ingestion Runs</h2>
        {runsLoading ? (
          <div className="loading-message">Loading runs...</div>
        ) : runs.length === 0 ? (
          <div className="empty-message">No ingestion runs found</div>
        ) : (
          <div className="table-container">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th>Completed</th>
                  <th>Progress</th>
                  <th>Errors</th>
                </tr>
              </thead>
              <tbody>
                {runs.map((run) => (
                  <tr key={run.id}>
                    <td>{run.id}</td>
                    <td>{run.runType}</td>
                    <td>
                      <span className={`status-badge ${getStatusClass(run.status)}`}>
                        {run.status}
                      </span>
                    </td>
                    <td>{formatDate(run.startedAt)}</td>
                    <td>{formatDate(run.completedAt)}</td>
                    <td>
                      {run.completedSteps}/{run.totalSteps} steps
                    </td>
                    <td>
                      {run.errorCount > 0 ? (
                        <span className="error-count">{run.errorCount}</span>
                      ) : (
                        '-'
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Recent Errors */}
      <section className="admin-section">
        <h2 className="section-title">Recent Errors</h2>
        {errorsLoading ? (
          <div className="loading-message">Loading errors...</div>
        ) : errors.length === 0 ? (
          <div className="empty-message">No recent errors</div>
        ) : (
          <div className="table-container">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Run</th>
                  <th>Step</th>
                  <th>Type</th>
                  <th>Message</th>
                  <th>Time</th>
                </tr>
              </thead>
              <tbody>
                {errors.map((error) => (
                  <tr key={error.id}>
                    <td>#{error.runId}</td>
                    <td>{error.stepName}</td>
                    <td>{error.errorType}</td>
                    <td className="error-message-cell" title={error.errorMessage}>
                      {error.errorMessage}
                    </td>
                    <td>{formatDate(error.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
