#!/bin/bash

set -e

SERVICE=$1

# if a service is empty
if [ -z "$SERVICE" ]; then
  echo "Showing logs for all services..."
  docker compose logs -f
else
  # if service is not empty prints selected service name and shows logs only for the selected service
  echo "Showing logs for service: $SERVICE"
  docker compose logs -f "$SERVICE"
fi