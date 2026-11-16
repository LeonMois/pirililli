#!/usr/bin/env bash
# Starts the database (if needed), builds the app (if needed) and launches the voting machine web server.
set -euo pipefail
cd "$(dirname "$0")"

if docker compose version >/dev/null 2>&1; then COMPOSE="docker compose"; else COMPOSE="docker-compose"; fi
$COMPOSE up -d --wait

if [ ! -f target/pirililli.jar ] || [ -n "$(find src pom.xml -newer target/pirililli.jar 2>/dev/null | head -1)" ]; then
  ./mvnw -q package -DskipTests
fi

exec java -jar target/pirililli.jar "$@"
