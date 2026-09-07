#!/usr/bin/env sh
set -eu

DEVELOPMENT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ARG_COUNT=$#
set -- --project-directory "$DEVELOPMENT_DIR" -f "$DEVELOPMENT_DIR/docker-compose.prod.yaml" "$@"
if [ ! -f "$DEVELOPMENT_DIR/.env.prod" ]; then
  echo "Copy development/.env.prod.example to development/.env.prod and set passwords first." >&2
  exit 1
fi
if [ -f "$DEVELOPMENT_DIR/.env.prod" ]; then
  set -- --env-file "$DEVELOPMENT_DIR/.env.prod" "$@"
fi
# With no Compose command, start services and wait for healthchecks.
if [ "$ARG_COUNT" -eq 0 ]; then
  set -- "$@" up -d --wait --wait-timeout 180
fi
exec docker compose "$@"
