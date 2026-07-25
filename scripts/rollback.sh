#!/usr/bin/env bash
# FR-3: Rollback script for YingShi Server
# Usage: ./rollback.sh [previous_jar_path]
#
# Rollback procedure:
# 1. Stop current server container
# 2. Replace current jar with previous version
# 3. Restart and verify health
#
# Prerequisites:
# - Previous version jar must exist at the specified path
# - Database schema must be backward compatible (no destructive migrations)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PREVIOUS_JAR="${1:-$PROJECT_DIR/target/yingshi-server-previous.jar}"
HEALTH_URL="http://localhost:8080/api/health"
HEALTH_TIMEOUT=120
HEALTH_INTERVAL=5

echo "=== YingShi Server Rollback ==="
echo "Project dir: $PROJECT_DIR"
echo "Previous jar: $PREVIOUS_JAR"

# Step 1: Verify previous jar exists
if [ ! -f "$PREVIOUS_JAR" ]; then
    echo "ERROR: Previous jar not found at $PREVIOUS_JAR"
    echo "Usage: ./rollback.sh /path/to/previous/yingshi-server.jar"
    exit 1
fi

# Step 2: Backup current jar
CURRENT_JAR="$PROJECT_DIR/target/yingshi-server-0.0.1-SNAPSHOT.jar"
if [ -f "$CURRENT_JAR" ]; then
    BACKUP_JAR="$PROJECT_DIR/target/yingshi-server-rolled-back-$(date +%Y%m%d%H%M%S).jar"
    echo "Backing up current jar to $BACKUP_JAR"
    cp "$CURRENT_JAR" "$BACKUP_JAR"
fi

# Step 3: Stop current container (if using docker-compose)
echo "Stopping current server..."
cd "$PROJECT_DIR"
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop server 2>/dev/null || true

# Step 4: Replace jar
echo "Replacing jar with previous version..."
cp "$PREVIOUS_JAR" "$CURRENT_JAR"

# Step 5: Restart
echo "Restarting server with previous version..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d server

# Step 6: Health check
echo "Waiting for health check (timeout: ${HEALTH_TIMEOUT}s)..."
elapsed=0
while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
        echo "SUCCESS: Server is healthy after rollback!"
        curl -s "$HEALTH_URL" | python3 -m json.tool 2>/dev/null || curl -s "$HEALTH_URL"
        exit 0
    fi
    sleep $HEALTH_INTERVAL
    elapsed=$((elapsed + HEALTH_INTERVAL))
    echo "  Waiting... (${elapsed}s/${HEALTH_TIMEOUT}s)"
done

echo "ERROR: Server did not become healthy within ${HEALTH_TIMEOUT}s after rollback"
echo "Manual intervention required. Check logs: docker compose logs server"
exit 1
