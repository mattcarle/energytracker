#!/usr/bin/env bash
# Stops processes started by start-dev.sh by killing whatever is listening on their ports
# (see start-dev.sh for why port-based tracking is used instead of a saved PID).
set -euo pipefail

kill_port() {
  local port="$1" name="$2" pids=""
  if command -v taskkill >/dev/null 2>&1; then
    pids="$(netstat -ano 2>/dev/null | grep -E ":$port[[:space:]]+.*LISTENING" | awk '{print $NF}' | sort -u)"
  elif command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -ti tcp:"$port" 2>/dev/null)"
  fi

  if [ -z "$pids" ]; then
    echo "$name is not running (nothing listening on port $port)"
    return
  fi

  local pid
  for pid in $pids; do
    if command -v taskkill >/dev/null 2>&1; then
      if taskkill //PID "$pid" //F >/dev/null 2>&1; then
        echo "Stopped $name (pid $pid, port $port)"
      else
        echo "Could not stop $name (pid $pid, port $port)"
      fi
    else
      if kill "$pid" 2>/dev/null; then
        echo "Stopped $name (pid $pid, port $port)"
      else
        echo "Could not stop $name (pid $pid, port $port)"
      fi
    fi
  done
}

kill_port 8080 "dev backend"
kill_port 5173 "dev frontend"
