#!/usr/bin/env bash
# Pulls the latest code and redeploys both containers.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

git pull
docker compose build
docker compose up -d
