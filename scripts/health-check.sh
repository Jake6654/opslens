#!/bin/bash

# if any command fails, stop the script immediately 
set -e

# Use BACKEND_URL if it exists, otherwis use localhost8080
# Bash variable
BACKEND_URL=${BACKEND_URL:-http://localhost:8080}
FRONTEND_URL=${FRONTEND_URL:-http://localhost:3000}


echo "Checking OpsLens sevices...."

echo "Checking backend health at $BACKEND_URL/health"
curl -f "$BACKEND_URL/health" > /dev/null

echo "Backend is healthy."

echo "Checking frontend at $FRONTEND_URL"
curl -f "$FRONTEND_URL" > /dev/null

echo "Frontend is reachable."

echo "OpsLens health check passed."