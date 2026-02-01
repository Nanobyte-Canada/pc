#!/bin/bash
# VPS Deployment Fix Script
# Run this on the VPS to fix deployment issues
# Usage: ssh user@vps 'bash -s' < scripts/vps-fix-deployment.sh
#
# IMPORTANT: This script requires environment variables to be set.
# Either export them before running or pass them as arguments.

set -e

DEPLOY_PATH="/opt/portfolio"

echo "============================================"
echo "VPS Deployment Fix Script"
echo "============================================"
echo ""

cd $DEPLOY_PATH

# Step 1: Check if tarball exists and extract it
if [ -f "deploy.tar.gz" ]; then
    echo "Step 1: Found tarball, extracting..."

    # Backup current if exists
    if [ -d "current" ]; then
        echo "  Backing up current deployment..."
        rm -rf previous
        mv current previous
    fi

    mkdir -p current
    tar -xzf deploy.tar.gz -C current
    rm deploy.tar.gz
    echo "  Extraction complete"
else
    echo "Step 1: No tarball found, checking current directory..."
    if [ ! -d "current" ]; then
        echo "  ERROR: No current directory and no tarball. Re-run the deployment from GitHub Actions."
        exit 1
    fi
fi

cd current

# Step 2: Verify files
echo ""
echo "Step 2: Verifying files..."
echo "  Directory contents:"
ls -la

if [ ! -f "docker-compose.vps.yml" ]; then
    echo "  ERROR: docker-compose.vps.yml not found!"
    exit 1
fi

if [ ! -d "backend" ]; then
    echo "  ERROR: backend directory not found!"
    exit 1
fi

if [ ! -d "frontend" ]; then
    echo "  ERROR: frontend directory not found!"
    exit 1
fi

echo "  All required files present"

# Step 3: Check/create .env file
echo ""
echo "Step 3: Checking .env file..."
if [ -f ".env" ]; then
    echo "  .env file exists with $(wc -l < .env) lines"
    echo "  Keys defined:"
    grep -oP '^[A-Z_]+=' .env | sort || true
else
    echo "  WARNING: .env file not found!"
    echo ""
    echo "  You need to create .env with these variables:"
    echo "    POSTGRES_DB=portfolio"
    echo "    POSTGRES_USER=portfolio"
    echo "    POSTGRES_PASSWORD=<your-password>"
    echo "    APP_VERSION=manual"
    echo "    APP_ENVIRONMENT=dev"
    echo "    SPRING_PROFILES_ACTIVE=dev"
    echo "    JWT_SIGNING_KEY=<your-key>"
    echo "    EODHD_API_KEY=<your-key>"
    echo "    ALPHA_VANTAGE_API_KEY=<your-key>"
    echo "    BROKER_ENCRYPTION_KEY=<your-key>"
    echo "    QUESTRADE_CLIENT_ID=<your-id>"
    echo "    QUESTRADE_CLIENT_SECRET=<your-secret>"
    echo "    GOOGLE_CLIENT_ID=<your-id>"
    echo "    GOOGLE_CLIENT_SECRET=<your-secret>"
    echo ""
    echo "  Create .env file and re-run this script."
    exit 1
fi

# Step 4: Build and start containers
echo ""
echo "Step 4: Building and starting containers..."
echo "  Stopping existing containers..."
docker compose -f docker-compose.vps.yml down || true

echo "  Building containers (this may take a few minutes)..."
docker compose -f docker-compose.vps.yml build --no-cache

echo "  Starting containers..."
docker compose -f docker-compose.vps.yml up -d

echo ""
echo "Step 5: Checking container status..."
docker compose -f docker-compose.vps.yml ps

echo ""
echo "Step 6: Waiting for services to start (30 seconds)..."
sleep 30

echo ""
echo "Step 7: Health check..."
if curl -f http://localhost:8080/health; then
    echo ""
    echo "  Backend is healthy!"
else
    echo "  Backend health check failed"
    echo ""
    echo "  Backend logs:"
    docker compose -f docker-compose.vps.yml logs backend --tail=50
    exit 1
fi

echo ""
echo "============================================"
echo "Deployment fix complete!"
echo "============================================"
echo ""
echo "Verification:"
echo "  - Backend: curl http://localhost:8080/health"
echo "  - Frontend: https://devpc.nanobyte.ca"
echo ""
