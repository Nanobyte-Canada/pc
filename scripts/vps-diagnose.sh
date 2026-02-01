#!/bin/bash
# VPS Deployment Diagnostic Script
# Run this on the VPS to diagnose deployment issues
# Usage: ssh user@vps 'bash -s' < scripts/vps-diagnose.sh

set -e

echo "============================================"
echo "VPS Deployment Diagnostic Tool"
echo "============================================"
echo ""

DEPLOY_PATH="/opt/portfolio"

echo "1. Checking directory structure..."
echo "-----------------------------------"
echo "Contents of $DEPLOY_PATH:"
ls -la $DEPLOY_PATH 2>/dev/null || echo "  ERROR: $DEPLOY_PATH does not exist"
echo ""

if [ -d "$DEPLOY_PATH/current" ]; then
    echo "Contents of $DEPLOY_PATH/current:"
    ls -la $DEPLOY_PATH/current
    echo ""
else
    echo "  WARNING: $DEPLOY_PATH/current does not exist"
fi

echo ""
echo "2. Checking for docker-compose files..."
echo "----------------------------------------"
find $DEPLOY_PATH -name "docker-compose*.yml" 2>/dev/null || echo "  No docker-compose files found"
echo ""

echo "3. Checking for tarball..."
echo "--------------------------"
if [ -f "$DEPLOY_PATH/deploy.tar.gz" ]; then
    echo "  Found deploy.tar.gz ($(ls -lh $DEPLOY_PATH/deploy.tar.gz | awk '{print $5}'))"
    echo "  Tarball contents preview:"
    tar -tzf $DEPLOY_PATH/deploy.tar.gz | head -20
    echo ""
    echo "  NOTE: Tarball exists but wasn't extracted. You may need to manually extract it."
else
    echo "  No deploy.tar.gz found (already extracted or never uploaded)"
fi
echo ""

echo "4. Checking Docker status..."
echo "----------------------------"
docker --version 2>/dev/null || echo "  ERROR: Docker not installed"
docker compose version 2>/dev/null || echo "  ERROR: Docker Compose not installed"
echo ""

echo "5. Checking running containers..."
echo "---------------------------------"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "  No containers running"
echo ""

echo "6. Checking docker-compose project status..."
echo "--------------------------------------------"
if [ -f "$DEPLOY_PATH/current/docker-compose.vps.yml" ]; then
    cd $DEPLOY_PATH/current
    docker compose -f docker-compose.vps.yml ps 2>/dev/null || echo "  Compose project not running"
else
    echo "  Cannot check: docker-compose.vps.yml not found"
fi
echo ""

echo "7. Checking .env file..."
echo "------------------------"
if [ -f "$DEPLOY_PATH/current/.env" ]; then
    echo "  .env file exists ($(wc -l < $DEPLOY_PATH/current/.env) lines)"
    echo "  Keys defined:"
    grep -oP '^[A-Z_]+=' $DEPLOY_PATH/current/.env | sort
else
    echo "  WARNING: .env file not found"
fi
echo ""

echo "8. Health check..."
echo "------------------"
if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
    echo "  Backend is healthy: $(curl -s http://localhost:8080/health)"
else
    echo "  Backend health check failed or not running"
fi

if curl -sf http://localhost:3000 > /dev/null 2>&1; then
    echo "  Frontend is responding on port 3000"
else
    echo "  Frontend not responding on port 3000"
fi
echo ""

echo "============================================"
echo "Diagnostic complete"
echo "============================================"
