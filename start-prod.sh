#!/usr/bin/env bash
# Builds and starts the backend (prod profile) and frontend preview server in the background.
# Assumes the TLS keystore has already been set up - see README.md, "Running in production".
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"
mkdir -p logs

# See start-dev.sh: checking the port a service actually listens on is what's observable and
# correct here, unlike a saved PID from mvnw/npm's own background job.
is_port_listening() {
  local port="$1"
  if command -v taskkill >/dev/null 2>&1; then
    netstat -ano 2>/dev/null | grep -qE ":$port[[:space:]]+.*LISTENING"
  elif command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"$port" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null
  fi
}

if is_port_listening 8443; then
  echo "Prod backend already running on https://localhost:8443"
else
  ./mvnw clean package
  SPRING_PROFILES_ACTIVE=prod java -jar target/energytracker-0.0.1-SNAPSHOT.jar > logs/prod-backend.log 2>&1 &
  echo "Prod backend starting on https://localhost:8443 (logs/prod-backend.log)"
fi

if [ ! -d frontend/node_modules ]; then
  (cd frontend && npm install)
fi

if is_port_listening 4173; then
  echo "Prod frontend already running on http://localhost:4173"
else
  (cd frontend && npm run build)
  (cd frontend && VITE_API_PROXY_TARGET=https://localhost:8443 npm run preview > ../logs/prod-frontend.log 2>&1 &)
  echo "Prod frontend preview starting on http://localhost:4173 (logs/prod-frontend.log)"
fi
