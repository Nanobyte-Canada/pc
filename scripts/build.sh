#!/bin/bash
set -e

echo "Building Portfolio Construction Application..."

# Build backend
echo "Building backend..."
cd backend
./gradlew build -x test
cd ..

# Build frontend
echo "Building frontend..."
cd frontend
npm ci
npm run build
cd ..

echo "Build complete!"
