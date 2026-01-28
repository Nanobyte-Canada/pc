#!/bin/bash
set -e

echo "Running all tests..."

# Run backend tests
echo ""
echo "=== Backend Tests ==="
cd backend
./gradlew test
cd ..

# Run frontend tests
echo ""
echo "=== Frontend Tests ==="
cd frontend
npm ci
npm run test:run
cd ..

echo ""
echo "All tests passed!"
