#!/bin/bash

set -e

echo "Rebuilding OpsLens containers..."

docker compose down

# starts the containers again and rebulds the images
docker compose up --build -d

echo "Waiting for services to start..."
sleep 5

echo "Running health check..."
./scripts/health-check.sh

echo "OpsLens rebuilt and running."