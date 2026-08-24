#!/usr/bin/env bash
# Stops both containers.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

docker compose stop
