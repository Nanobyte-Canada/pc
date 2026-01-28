#!/bin/bash
set -e

# Deploy script for manual deployments
# Typically CI/CD handles this, but useful for local testing

ENVIRONMENT=${1:-dev}
PROJECT_ID=${2:-portfolio-$ENVIRONMENT}
REGION=${3:-us-central1}

echo "Deploying to $ENVIRONMENT environment..."
echo "Project: $PROJECT_ID"
echo "Region: $REGION"

# Verify gcloud auth
gcloud auth print-access-token > /dev/null 2>&1 || {
    echo "Error: Not authenticated with gcloud. Run 'gcloud auth login' first."
    exit 1
}

# Set project
gcloud config set project $PROJECT_ID

# Build and push backend image
echo "Building and pushing backend image..."
docker build -t $REGION-docker.pkg.dev/$PROJECT_ID/portfolio/portfolio-backend:latest ./backend
docker push $REGION-docker.pkg.dev/$PROJECT_ID/portfolio/portfolio-backend:latest

# Deploy to Cloud Run
echo "Deploying to Cloud Run..."
gcloud run deploy portfolio-backend \
    --image $REGION-docker.pkg.dev/$PROJECT_ID/portfolio/portfolio-backend:latest \
    --region $REGION \
    --platform managed

# Build and deploy frontend
echo "Building frontend..."
cd frontend
VITE_API_URL=https://api-$ENVIRONMENT.portfolio.example.com npm run build

echo "Uploading to Cloud Storage..."
gcloud storage cp -r dist/* gs://portfolio-frontend-$ENVIRONMENT/

echo ""
echo "Deployment complete!"
