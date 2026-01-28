#!/bin/bash
set -e

echo "Starting local development environment..."

# Load environment variables
if [ -f .env.local ]; then
    export $(cat .env.local | grep -v '^#' | xargs)
fi

# Start services with docker-compose
docker-compose up -d

echo "Waiting for services to be healthy..."
sleep 5

# Check health
echo "Checking backend health..."
until curl -s http://localhost:8080/health > /dev/null 2>&1; do
    echo "Waiting for backend..."
    sleep 2
done

echo ""
echo "Services are running!"
echo "  - Frontend: http://localhost:3000"
echo "  - Backend:  http://localhost:8080"
echo "  - Health:   http://localhost:8080/health"
echo "  - Version:  http://localhost:8080/api/v1/version"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop:      docker-compose down"
