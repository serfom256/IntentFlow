#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: intentflow-examples/run.sh [testcontainers|postgres|mysql]"
  echo "  testcontainers  ephemeral PostgreSQL in a container (default)"
  echo "  postgres        PostgreSQL from docker-compose.yml"
  echo "  mysql           MySQL from docker-compose.yml"
}

MODE="${1:-testcontainers}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API_PORT="${API_PORT:-8080}"
LOG="$ROOT/intentflow-examples/intentflow-shop-api/build/run.log"
cd "$ROOT"

case "$MODE" in
  testcontainers) API_TASK=(:intentflow-shop-api:bootTestRun) ;;
  postgres)
    docker compose up -d --wait postgres
    API_TASK=(:intentflow-shop-api:bootRun)
    ;;
  mysql)
    docker compose up -d --wait mysql
    API_TASK=(:intentflow-shop-api:bootRun --args=--spring.profiles.active=mysql)
    ;;
  -h|--help) usage; exit 0 ;;
  *) usage; exit 1 ;;
esac

if curl -s -o /dev/null "http://localhost:$API_PORT"; then
  echo "port $API_PORT is already in use; stop that process or set API_PORT" >&2
  exit 1
fi

mkdir -p "$(dirname "$LOG")"
API_PID=""
stop() {
  if [ -n "$API_PID" ] && kill -0 "$API_PID" 2>/dev/null; then
    echo "stopping the API"
    kill "$API_PID" 2>/dev/null || true
    wait "$API_PID" 2>/dev/null || true
  fi
}
trap stop EXIT INT TERM

echo "starting the API ($MODE), log: $LOG"
SERVER_PORT="$API_PORT" ./gradlew --console=plain "${API_TASK[@]}" > "$LOG" 2>&1 &
API_PID=$!

for _ in $(seq 1 180); do
  if curl -s -o /dev/null "http://localhost:$API_PORT/api/products?size=1"; then
    break
  fi
  if ! kill -0 "$API_PID" 2>/dev/null; then
    echo "the API failed to start:" >&2
    tail -30 "$LOG" >&2
    exit 1
  fi
  sleep 2
done
echo "API ready on http://localhost:$API_PORT"
echo "catalog admin on http://localhost:$API_PORT/intentflow-admin/"
echo "UI starting on http://localhost:5173 (Ctrl+C stops both)"

SHOP_API_URL="http://localhost:$API_PORT" ./gradlew --console=plain :intentflow-shop-ui:dev
