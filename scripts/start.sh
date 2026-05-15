#!/bin/bash

set -e

echo "Starting OpsLens with Docker Compose..."

docker compose up -d

echo "Waiting for services to start...."
# Waits for 5 seconds
# This gives spring boot and next.js time to start before running the health check
sleep 5

echo "Running health check..."
# Runs the health-check.sh
./scripts/health-check.sh

echo "OpsLens is running."
echo "Frontend: http://localhost:3000"
echo "Backend:  http://localhost:8080"