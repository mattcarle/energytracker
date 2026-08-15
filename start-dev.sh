#!/usr/bin/env bash
# Starts the backend (dev profile) and frontend dev server in the background.
# See README.md, "Running in development".
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"
mkdir -p logs

# PIDs from mvnw/npm's own background job (bash `$!`) don't reliably identify the real backend/
# frontend process on Windows - both spawn a child process (Spring Boot's forked JVM, Vite under
# npm) that ends up under a different native PID bash's job control never sees. Checking the port
# they actually listen on is what's observable and correct on every platform.
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

if is_port_listening 8080; then
  echo "Dev backend already running on http://localhost:8080"
else
  ./mvnw spring-boot:run > logs/dev-backend.log 2>&1 &
  echo "Dev backend starting on http://localhost:8080 (logs/dev-backend.log)"
fi

if [ ! -d frontend/node_modules ]; then
  (cd frontend && npm install)
fi

if is_port_listening 5173; then
  echo "Dev frontend already running on http://localhost:5173"
else
  (cd frontend && npm run dev > ../logs/dev-frontend.log 2>&1 &)
  echo "Dev frontend starting on http://localhost:5173 (logs/dev-frontend.log)"
fi
