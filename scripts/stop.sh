#!/bin/bash

set -e

echo "Stopping OpsLens..."

docker compose down

echo "OpsLens stopped."