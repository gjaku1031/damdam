#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# Bootstrap operator credentials and required terms; existing catalog is never reseeded.
docker compose run --rm api seed
