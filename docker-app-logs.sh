#!/usr/bin/env bash
# Follows the app container's logs (Ctrl-C to stop).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

docker compose logs -f app
