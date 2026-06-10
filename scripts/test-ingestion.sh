#!/bin/bash

set -e

BACKEND_URL=${BACKEND_URL:-http://localhost:8081}

if [ ! -f ".env" ]; then
  echo "Error: .env file not found."
  exit 1
fi

API_KEY_VALUE=$(grep '^API_KEY=' .env | cut -d '=' -f2-)

if [ -z "$API_KEY_VALUE" ]; then
  echo "Error: API_KEY is missing in .env."
  exit 1
fi

echo "Sending test log to OpsLens..."

curl -X POST "$BACKEND_URL/logs" \
  -H "Content-Type: application/json" \
  -H "x-api-key: $API_KEY_VALUE" \
  -d '{
    "timestamp": "2026-05-11T15:00:00",
    "level": "ERROR",
    "project": "opslens",
    "environment": "dev",
    "service": "test-script",
    "message": "Test log sent from Bash script"
  }'

echo
echo "Test log sent."