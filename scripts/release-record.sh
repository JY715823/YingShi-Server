#!/bin/bash
# ============================================================
# YingShi Release Record Script
# ============================================================
# 记录发布元信息到 releases/ 目录，便于审计和回滚。
#
# 记录内容：
#   - 时间戳
#   - Git SHA
#   - Docker 镜像 digest
#   - Flyway 数据库版本
#   - APK versionCode（如可获取）
#   - 操作人
#   - 服务器主机
#
# 用法：
#   ./release-record.sh [image-digest]
#   ./release-record.sh  # 自动从 docker inspect 获取
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASES_DIR="$PROJECT_DIR/releases"
SERVER_HOST="${YINGSHI_SSH_HOST:-yingshi}"
REMOTE_DIR="${YINGSHI_REMOTE_DIR:-/opt/yingshi}"

mkdir -p "$RELEASES_DIR"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RELEASE_FILE="$RELEASES_DIR/release-${TIMESTAMP}.txt"

# Git SHA
GIT_SHA=$(git -C "$PROJECT_DIR" rev-parse HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git -C "$PROJECT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

# Image digest (from argument or docker inspect)
IMAGE_DIGEST="${1:-}"
if [ -z "$IMAGE_DIGEST" ]; then
    IMAGE_DIGEST=$(ssh "$SERVER_HOST" "docker inspect --format='{{index .RepoDigests 0}}' yingshi-server 2>/dev/null || docker inspect --format='{{.Id}}' yingshi-server 2>/dev/null" 2>/dev/null || echo "unknown")
fi

# Flyway version (from remote server)
FLYWAY_VERSION=$(ssh "$SERVER_HOST" "cd $REMOTE_DIR && docker compose -f docker-compose.prod.yml exec -T server curl -sf http://localhost:8080/actuator/health 2>/dev/null | grep -o 'flyway[^,]*' || echo 'unknown'" 2>/dev/null || echo "unknown")

# APK versionCode (from latest release in DB)
APK_VERSION_CODE=$(ssh "$SERVER_HOST" "cd $REMOTE_DIR && docker compose -f docker-compose.prod.yml exec -T postgres psql -U yingshi -d yingshi -t -c 'SELECT version_code FROM app_releases WHERE published = true ORDER BY created_at DESC LIMIT 1;' 2>/dev/null | tr -d '[:space:]'" 2>/dev/null || echo "unknown")

# Operator
OPERATOR=$(whoami)
OPERATOR_HOST=$(hostname)

{
    echo "=============================================="
    echo "YingShi Server Release Record"
    echo "=============================================="
    echo "Timestamp:       $(date +%Y-%m-%dT%H:%M:%S%z)"
    echo "Operator:        ${OPERATOR}@${OPERATOR_HOST}"
    echo "Target Host:      ${SERVER_HOST}"
    echo "Remote Dir:       ${REMOTE_DIR}"
    echo "----------------------------------------------"
    echo "Git SHA:          ${GIT_SHA}"
    echo "Git Branch:       ${GIT_BRANCH}"
    echo "Image Digest:     ${IMAGE_DIGEST}"
    echo "Flyway Version:   ${FLYWAY_VERSION}"
    echo "APK VersionCode:  ${APK_VERSION_CODE}"
    echo "=============================================="
} > "$RELEASE_FILE"

echo "Release record saved to: $RELEASE_FILE"
cat "$RELEASE_FILE"
