#!/bin/bash
# PostgreSQL GUC Parameter Initialization
# Sets application-specific GUC parameters from environment variables
# These are used by Flyway migration V75 to seed the UAT test admin user
#
# This script runs automatically when the PostgreSQL container starts for the first time
# (via /docker-entrypoint-initdb.d/)

set -e

# Function to set a GUC parameter
set_guc() {
    local key="$1"
    local value="$2"

    if [ -n "$value" ]; then
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
            ALTER SYSTEM SET ${key} = '${value}';
EOSQL
        echo "Set GUC parameter: ${key}"
    else
        echo "Skipping GUC parameter: ${key} (value is empty)"
    fi
}

# Set GUC parameters from environment variables
# These will be available to Flyway migrations via current_setting()
set_guc "app.test_admin_email" "${APP_TEST_ADMIN_EMAIL}"
set_guc "app.test_admin_password_hash" "${APP_TEST_ADMIN_PASSWORD}"

# Reload configuration to apply changes
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT pg_reload_conf();
EOSQL

echo "GUC parameters initialized successfully"
