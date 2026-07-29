#!/bin/bash
# ============================================================
# YingShi Server Rollback Script (docker tag based)
# ============================================================
# 基于 docker tag 切换镜像 digest，无需替换宿主 JAR。
#
# 用法：
#   ./rollback.sh <image-digest-or-tag>
#
# 示例：
#   ./rollback.sh sha256:abc123...
#   ./rollback.sh yingshi-server:v1.2.3
#
# 前置条件：
#   - 目标镜像 digest 必须在本地 docker 中存在
#   - 数据库 schema 向后兼容（无破坏性迁移）
#   - docker-compose.prod.yml 的 server 服务已配置 image: yingshi-server:${SERVER_IMAGE_TAG:-latest}
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HEALTH_CHECK_URL="http://localhost:8080/actuator/health/liveness"
HEALTH_TIMEOUT=120
HEALTH_INTERVAL=5

TARGET_DIGEST="${1:?Usage: rollback.sh <image-digest-or-tag>}"

echo "=== YingShi Server Rollback ==="
echo "Project dir: $PROJECT_DIR"
echo "Target image: $TARGET_DIGEST"

# Step 1: Verify target image exists locally
echo ""
echo "[1/4] Verifying target image exists..."
if ! docker image inspect "$TARGET_DIGEST" > /dev/null 2>&1; then
    echo "ERROR: Image '$TARGET_DIGEST' not found in local docker."
    echo "Available yingshi-server images:"
    docker images --format "  {{.Repository}}:{{.Tag}} ({{.ID}}, {{.Size}})" | grep yingshi-server || echo "  (none)"
    exit 1
fi
echo "    ✅ Image found"

# Step 2: Tag target image as rollback-target
echo ""
echo "[2/4] Tagging target image as yingshi-server:rollback-target..."
docker tag "$TARGET_DIGEST" yingshi-server:rollback-target
echo "    ✅ Tagged"

# Step 3: Restart server with rollback image
echo ""
echo "[3/4] Restarting server with rollback image..."
cd "$PROJECT_DIR"
export SERVER_IMAGE_TAG="rollback-target"
docker compose -f docker-compose.prod.yml up -d --no-build server
echo "    ✅ Server restarted"

# Step 4: Health check
echo ""
echo "[4/4] Health check (timeout: ${HEALTH_TIMEOUT}s)..."
elapsed=0
while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
    if curl -sf "$HEALTH_CHECK_URL" > /dev/null 2>&1; then
        echo "    ✅ Server is healthy after rollback!"
        echo ""
        echo "=== Rollback Complete ==="
        echo "Active image: yingshi-server:rollback-target (from $TARGET_DIGEST)"
        exit 0
    fi
    sleep $HEALTH_INTERVAL
    elapsed=$((elapsed + HEALTH_INTERVAL))
    echo "    Waiting... (${elapsed}s/${HEALTH_TIMEOUT}s)"
done

echo ""
echo "ERROR: Server did not become healthy within ${HEALTH_TIMEOUT}s after rollback"
echo "Manual intervention required."
echo "  Check logs: docker compose -f docker-compose.prod.yml logs server --tail 50"
echo "  Roll forward: SERVER_IMAGE_TAG=latest docker compose -f docker-compose.prod.yml up -d --no-build server"
exit 1
