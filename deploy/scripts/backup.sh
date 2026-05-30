#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Database Backup Script
# Backs up prod and UAT PostgreSQL databases.
# Run daily via cron:
#   0 3 * * * /opt/portfolio/scripts/backup.sh >> /opt/portfolio/backups/backup.log 2>&1
# ============================================================

BACKUP_DIR="/opt/portfolio/backups"
DATE=$(date +%Y-%m-%d)
DAY_OF_WEEK=$(date +%u)  # 1=Monday, 7=Sunday
DAILY_RETENTION=7
WEEKLY_RETENTION=30

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

backup_db() {
    local env="$1"
    local port="$2"
    local db_user="$3"
    local db_name="$4"
    local container="${env}-postgres"
    local target_dir="${BACKUP_DIR}/${env}"

    mkdir -p "${target_dir}/daily" "${target_dir}/weekly"

    local daily_file="${target_dir}/daily/${db_name}-${DATE}.sql.gz"

    log "Backing up ${env} database (${db_name})..."

    docker exec "${container}" pg_dump -U "${db_user}" "${db_name}" | gzip > "${daily_file}"

    local size
    size=$(du -h "${daily_file}" | cut -f1)
    log "  Created: ${daily_file} (${size})"

    # Weekly backup on Sundays
    if [ "${DAY_OF_WEEK}" -eq 7 ]; then
        local weekly_file="${target_dir}/weekly/${db_name}-${DATE}.sql.gz"
        cp "${daily_file}" "${weekly_file}"
        log "  Weekly backup: ${weekly_file}"
    fi

    # Cleanup: remove daily backups older than retention period
    find "${target_dir}/daily" -name "*.sql.gz" -mtime +${DAILY_RETENTION} -delete
    log "  Cleaned daily backups older than ${DAILY_RETENTION} days"

    # Cleanup: remove weekly backups older than retention period
    find "${target_dir}/weekly" -name "*.sql.gz" -mtime +${WEEKLY_RETENTION} -delete
    log "  Cleaned weekly backups older than ${WEEKLY_RETENTION} days"
}

log "=== Starting database backups ==="

# Source env files to get credentials
if [ -f /opt/portfolio/prod/.env ]; then
    # shellcheck disable=SC1091
    source /opt/portfolio/prod/.env
    backup_db "prod" "15432" "${POSTGRES_USER}" "${POSTGRES_DB}"
else
    log "WARNING: /opt/portfolio/prod/.env not found, skipping prod backup"
fi

if [ -f /opt/portfolio/uat/.env ]; then
    # Save prod vars, load UAT
    PROD_USER="${POSTGRES_USER:-}"
    PROD_DB="${POSTGRES_DB:-}"
    # shellcheck disable=SC1091
    source /opt/portfolio/uat/.env
    backup_db "uat" "25432" "${POSTGRES_USER}" "${POSTGRES_DB}"
    # Restore prod vars
    POSTGRES_USER="${PROD_USER}"
    POSTGRES_DB="${PROD_DB}"
else
    log "WARNING: /opt/portfolio/uat/.env not found, skipping UAT backup"
fi

log "=== Backups complete ==="
