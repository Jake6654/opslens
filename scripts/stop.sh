#!/bin/bash

set -e

echo "Stopping OpsLens..."

# Stops and removes the running containers
docker compose down

echo "OpsLens stopped."