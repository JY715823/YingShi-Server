#!/bin/bash
# ============================================================
# YingShi Restore Script (Isolated Environment)
# ============================================================
# 隔离环境恢复：解密 + PG pg_restore + MinIO mc cp + 配置恢复 + 服务启动
#
# 用法：
#   ./restore.sh <encrypted-backup-file>
#   ./restore.sh /opt/backups/yingshi-backup-20260101-030000.enc
#
# 警告：此脚本会覆盖目标数据库和存储 bucket，仅在隔离/演练环境使用！
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKUP_FILE="${1:?Usage: restore.sh <encrypted-backup-file>}"
RESTORE_ROOT="${RESTORE_ROOT:-$PROJECT_DIR/restore-tmp}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-yingshi-postgres}"
POSTGRES_USER="${POSTGRES_USER:-yingshi}"
POSTGRES_DB="${POSTGRES_DB:-yingshi}"
MINIO_CONTAINER="${MINIO_CONTAINER:-yingshi-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-yingshi-media}"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"

STORAGE_ENDPOINT="${STORAGE_ENDPOINT:-http://minio:9000}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: Backup file not found: $BACKUP_FILE"
    exit 1
fi

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESTORE_DIR="$RESTORE_ROOT/restore-${TIMESTAMP}"
mkdir -p "$RESTORE_DIR"

echo "=============================================="
echo "YingShi Restore (Isolated Environment)"
echo "Backup file: $BACKUP_FILE"
echo "Restore dir: $RESTORE_DIR"
echo "=============================================="

# ---- 1. Decrypt & extract ----
echo ""
echo "[1/6] Decrypt & extract backup..."
if ! gpg --batch --yes --decrypt "$BACKUP_FILE" 2>/dev/null | tar xzf - -C "$RESTORE_DIR"; then
    echo "ERROR: Decryption/extraction failed. Check GPG key and file integrity."
    exit 1
fi

BACKUP_SET_DIR=$(find "$RESTORE_DIR" -maxdepth 1 -type d -name "backup-*" | head -1)
if [ -z "$BACKUP_SET_DIR" ]; then
    echo "ERROR: No backup set found in extracted archive."
    exit 1
fi
echo "    ✅ Extracted to: $BACKUP_SET_DIR"

# ---- 2. Verify containers running ----
echo ""
echo "[2/6] Verifying containers..."
for c in "$POSTGRES_CONTAINER" "$MINIO_CONTAINER"; do
    if ! docker inspect "$c" > /dev/null 2>&1; then
        echo "ERROR: Container '$c' not found. Start services first:"
        echo "  docker compose -f $COMPOSE_FILE up -d postgres minio minio-init"
        exit 1
    fi
done
echo "    ✅ Containers running"

# ---- 3. PostgreSQL restore ----
echo ""
echo "[3/6] PostgreSQL pg_restore..."
PG_DUMP=$(find "$BACKUP_SET_DIR" -name "*.dump" -type f | head -1)
if [ -z "$PG_DUMP" ]; then
    echo "ERROR: No .dump file found in backup set."
    exit 1
fi

# Copy dump into container
CONTAINER_DUMP="/tmp/yingshi-restore-${TIMESTAMP}.dump"
docker cp "$PG_DUMP" "${POSTGRES_CONTAINER}:${CONTAINER_DUMP}"

# Drop and recreate database
docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS ${POSTGRES_DB};"
docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE ${POSTGRES_DB};"

# Restore
docker exec "$POSTGRES_CONTAINER" pg_restore \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    --no-owner --no-privileges \
    "$CONTAINER_DUMP" || true

docker exec "$POSTGRES_CONTAINER" rm -f "$CONTAINER_DUMP"
echo "    ✅ PostgreSQL restored"

# ---- 4. MinIO restore ----
echo ""
echo "[4/6] MinIO mc cp (restore bucket)..."
MINIO_BACKUP=$(find "$BACKUP_SET_DIR" -type d -name "minio-*" | head -1)
if [ -n "$MINIO_BACKUP" ]; then
    DOCKER_NETWORK=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$MINIO_CONTAINER" 2>/dev/null || echo "yingshi-server_yingshi-net")
    ABSOLUTE_MINIO_BACKUP=$(cd "$MINIO_BACKUP" && pwd)

    docker run --rm \
        --network "$DOCKER_NETWORK" \
        -v "${ABSOLUTE_MINIO_BACKUP}:/backup" \
        --entrypoint /bin/sh \
        minio/mc:RELEASE.2025-04-16T18-13-26Z \
        -c "mc alias set local ${STORAGE_ENDPOINT} ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} >/dev/null && mc mb --ignore-existing local/${MINIO_BUCKET} && mc mirror --overwrite /backup local/${MINIO_BUCKET}"
    echo "    ✅ MinIO restored"
else
    echo "    ⚠️  No MinIO backup found; skipping storage restore"
fi

# ---- 5. Config restore ----
echo ""
echo "[5/6] Config restore..."
CONFIG_TARBALL="$BACKUP_SET_DIR/configs.tar.gz"
if [ -f "$CONFIG_TARBALL" ]; then
    mkdir -p "$RESTORE_DIR/configs"
    tar xzf "$CONFIG_TARBALL" -C "$RESTORE_DIR/configs"
    echo "    ✅ Configs extracted to: $RESTORE_DIR/configs"
    echo "    Manual review required before applying to production."
else
    echo "    ⚠️  No config tarball found"
fi

# ---- 6. Checksum sample verification ----
echo ""
echo "[6/6] Checksum sample verification..."
SAMPLE_COUNT=0
PASS_COUNT=0
if [ -n "$MINIO_BACKUP" ] && [ -d "$MINIO_BACKUP" ]; then
    for sample in $(find "$MINIO_BACKUP" -type f | head -5); do
        SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
        LOCAL_HASH=$(sha256sum "$sample" | cut -d' ' -f1)
        REL_PATH=${sample#$MINIO_BACKUP/}
        REMOTE_HASH=$(docker run --rm \
            --network "$DOCKER_NETWORK" \
            --entrypoint /bin/sh \
            minio/mc:RELEASE.2025-04-16T18-13-26Z \
            -c "mc alias set local ${STORAGE_ENDPOINT} ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} >/dev/null && mc cat local/${MINIO_BUCKET}/${REL_PATH} 2>/dev/null | sha256sum | cut -d' ' -f1" 2>/dev/null || echo "failed")

        if [ "$LOCAL_HASH" = "$REMOTE_HASH" ]; then
            echo "    ✅ [${SAMPLE_COUNT}] $REL_PATH: checksum match"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo "    ❌ [${SAMPLE_COUNT}] $REL_PATH: checksum mismatch"
        fi
    done
fi

echo ""
echo "=============================================="
echo "Restore Complete"
echo "Checksum: ${PASS_COUNT}/${SAMPLE_COUNT} samples verified"
echo "=============================================="
echo ""
echo "Next steps:"
echo "  1. Review configs at $RESTORE_DIR/configs"
echo "  2. Start server: docker compose -f $COMPOSE_FILE up -d server"
echo "  3. Health check: curl http://localhost:8080/api/health"
