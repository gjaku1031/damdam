#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${DEPLOY_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$PROJECT_ROOT"

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Engine and Docker Compose v2 are required." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo 'ERROR: The current account cannot access the Docker daemon.' >&2
  exit 1
fi
if [ ! -f .env ]; then
  echo "ERROR: Copy .env.example to .env and configure credentials before deploying." >&2
  exit 1
fi

docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
