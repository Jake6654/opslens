#!/bin/bash

# if any command fails, stop the script immediately 
set -e

# Use BACKEND_URL if it exists, otherwis use localhost8080
# creates variables
BACKEND_URL=${BACKEND_URL:-http://localhost:8081}
FRONTEND_URL=${FRONTEND_URL:-http://localhost:3001}


echo "Checking OpsLens sevices...."

echo "Checking backend health at $BACKEND_URL/health"
# curl sends an HTTP request to the backend health endpoint
# > /dev/null hides the response body
curl -f "$BACKEND_URL/health" > /dev/null

echo "Backend is healthy."

echo "Checking frontend at $FRONTEND_URL"
curl -f "$FRONTEND_URL" > /dev/null

echo "Frontend is reachable."

echo "OpsLens health check passed."