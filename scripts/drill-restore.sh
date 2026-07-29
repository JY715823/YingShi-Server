#!/bin/bash
# ============================================================
# YingShi Quarterly Restore Drill Script
# ============================================================
# 季度恢复演练：在隔离环境启动服务 + 抽样媒体下载 + checksum 比对 + 记录 RPO/RTO
#
# 用法：
#   ./drill-restore.sh <encrypted-backup-file>
#   ./drill-restore.sh /opt/backups/yingshi-backup-20260101-030000.enc
#
# 环境变量：
#   DRILL_NETWORK      (default: yingshi-drill)
#   DRILL_DB_PORT      (default: 15433)
#   DRILL_SERVER_PORT  (default: 18080)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKUP_FILE="${1:?Usage: drill-restore.sh <encrypted-backup-file>}"
DRILL_DIR="${DRILL_DIR:-$PROJECT_DIR/drill-$(date +%Y%m%d-%H%M%S)}"
DRILL_NETWORK="${DRILL_NETWORK:-yingshi-drill}"
DRILL_DB_PORT="${DRILL_DB_PORT:-15433}"
DRILL_SERVER_PORT="${DRILL_SERVER_PORT:-18080}"

RPO_START=$(date +%s)

if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: Backup file not found: $BACKUP_FILE"
    exit 1
fi

mkdir -p "$DRILL_DIR"

echo "=============================================="
echo "YingShi Quarterly Restore Drill"
echo "Backup file: $BACKUP_FILE"
echo "Drill dir:   $DRILL_DIR"
echo "=============================================="

# Record drill metadata
DRILL_LOG="$DRILL_DIR/drill-log.txt"
{
    echo "=============================================="
    echo "Restore Drill Log"
    echo "Date: $(date -Iseconds)"
    echo "Backup: $BACKUP_FILE"
    echo "Operator: $(whoami)@$(hostname)"
    echo "=============================================="
} > "$DRILL_LOG"

# ---- 1. Decrypt & extract ----
echo ""
echo "[1/7] Decrypt & extract backup..."
RTO_START=$(date +%s)
if ! gpg --batch --yes --decrypt "$BACKUP_FILE" 2>/dev/null | tar xzf - -C "$DRILL_DIR"; then
    echo "ERROR: Decryption failed."
    echo "Decrypt failed at $(date -Iseconds)" >> "$DRILL_LOG"
    exit 1
fi
BACKUP_SET_DIR=$(find "$DRILL_DIR" -maxdepth 1 -type d -name "backup-*" | head -1)
if [ -z "$BACKUP_SET_DIR" ]; then
    echo "ERROR: No backup set in archive."
    exit 1
fi
echo "    ✅ Extracted: $BACKUP_SET_DIR"

# ---- 2. Create isolated network ----
echo ""
echo "[2/7] Creating isolated network..."
docker network create "$DRILL_NETWORK" 2>/dev/null || true
echo "    ✅ Network: $DRILL_NETWORK"

# ---- 3. Start isolated PostgreSQL ----
echo ""
echo "[3/7] Starting isolated PostgreSQL..."
POSTGRES_USER="${POSTGRES_USER:-yingshi}"
POSTGRES_DB="${POSTGRES_DB:-yingshi}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-drill_password_temp}"

docker run -d --rm \
    --name drill-postgres \
    --network "$DRILL_NETWORK" \
    -e POSTGRES_DB="$POSTGRES_DB" \
    -e POSTGRES_USER="$POSTGRES_USER" \
    -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    -v "${DRILL_DIR}/pg-data:/var/lib/postgresql/data" \
    postgres:16-alpine

echo "    Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
    if docker exec drill-postgres pg_isready -U "$POSTGRES_USER" > /dev/null 2>&1; then
        echo "    ✅ PostgreSQL ready"
        break
    fi
    sleep 2
done

# ---- 4. Restore PostgreSQL ----
echo ""
echo "[4/7] Restoring PostgreSQL..."
PG_DUMP=$(find "$BACKUP_SET_DIR" -name "*.dump" -type f | head -1)
if [ -n "$PG_DUMP" ]; then
    CONTAINER_DUMP="/tmp/drill-restore.dump"
    docker cp "$PG_DUMP" "drill-postgres:${CONTAINER_DUMP}"
    docker exec drill-postgres pg_restore \
        -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
        --no-owner --no-privileges \
        "$CONTAINER_DUMP" || true
    docker exec drill-postgres rm -f "$CONTAINER_DUMP"
    echo "    ✅ PostgreSQL restored"
else
    echo "    ⚠️  No PG dump found"
fi

# ---- 5. Start isolated MinIO & restore ----
echo ""
echo "[5/7] Starting isolated MinIO & restoring..."
MINIO_ROOT_USER="${MINIO_ROOT_USER:-drill_minio_access}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-drill_minio_secret}"
MINIO_BUCKET="${MINIO_BUCKET:-yingshi-media}"

docker run -d --rm \
    --name drill-minio \
    --network "$DRILL_NETWORK" \
    -e MINIO_ROOT_USER="$MINIO_ROOT_USER" \
    -e MINIO_ROOT_PASSWORD="$MINIO_ROOT_PASSWORD" \
    -v "${DRILL_DIR}/minio-data:/data" \
    minio/minio:RELEASE.2025-04-22T22-12-26Z server /data --console-address ":9001"

echo "    Waiting for MinIO to be ready..."
for i in $(seq 1 30); do
    if docker exec drill-minio curl -sf http://localhost:9000/minio/health/live > /dev/null 2>&1; then
        echo "    ✅ MinIO ready"
        break
    fi
    sleep 2
done

MINIO_BACKUP=$(find "$BACKUP_SET_DIR" -type d -name "minio-*" | head -1)
if [ -n "$MINIO_BACKUP" ] && [ -d "$MINIO_BACKUP" ]; then
    ABSOLUTE_MINIO_BACKUP=$(cd "$MINIO_BACKUP" && pwd)
    docker run --rm \
        --network "$DRILL_NETWORK" \
        -v "${ABSOLUTE_MINIO_BACKUP}:/backup" \
        --entrypoint /bin/sh \
        minio/mc:RELEASE.2025-04-16T18-13-26Z \
        -c "mc alias set local http://drill-minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} >/dev/null && mc mb --ignore-existing local/${MINIO_BUCKET} && mc mirror --overwrite /backup local/${MINIO_BUCKET}"
    echo "    ✅ MinIO restored"
else
    echo "    ⚠️  No MinIO backup found"
fi

# ---- 6. Sample media checksum comparison ----
echo ""
echo "[6/7] Sample media checksum comparison..."
SAMPLE_COUNT=0
SAMPLE_PASS=0
if [ -n "$MINIO_BACKUP" ] && [ -d "$MINIO_BACKUP" ]; then
    for sample in $(find "$MINIO_BACKUP" -type f | head -5); do
        SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
        LOCAL_HASH=$(sha256sum "$sample" | cut -d' ' -f1)
        REL_PATH=${sample#$MINIO_BACKUP/}

        RESTORED_HASH=$(docker run --rm \
            --network "$DRILL_NETWORK" \
            --entrypoint /bin/sh \
            minio/mc:RELEASE.2025-04-16T18-13-26Z \
            -c "mc alias set local http://drill-minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} >/dev/null && mc cat local/${MINIO_BUCKET}/${REL_PATH} 2>/dev/null | sha256sum | cut -d' ' -f1" 2>/dev/null || echo "failed")

        if [ "$LOCAL_HASH" = "$RESTORED_HASH" ]; then
            echo "    ✅ [${SAMPLE_COUNT}] $REL_PATH: match"
            SAMPLE_PASS=$((SAMPLE_PASS + 1))
        else
            echo "    ❌ [${SAMPLE_COUNT}] $REL_PATH: mismatch"
        fi
    done
fi
echo "    Checksum: ${SAMPLE_PASS}/${SAMPLE_COUNT} passed"

RTO_END=$(date +%s)
RTO_SECONDS=$((RTO_END - RTO_START))
RTO_MINUTES=$((RTO_SECONDS / 60))

# ---- 7. Record RPO/RTO ----
echo ""
echo "[7/7] Recording RPO/RTO..."
BACKUP_TIMESTAMP=$(stat -c %Y "$BACKUP_FILE" 2>/dev/null || stat -f %m "$BACKUP_FILE" 2>/dev/null || echo 0)
if [ "$BACKUP_TIMESTAMP" -gt 0 ]; then
    RPO_SECONDS=$((RTO_START - BACKUP_TIMESTAMP))
    RPO_MINUTES=$((RPO_SECONDS / 60))
    RPO_HOURS=$((RPO_SECONDS / 3600))
else
    RPO_SECONDS=0
    RPO_MINUTES=0
    RPO_HOURS=0
fi

{
    echo ""
    echo "=============================================="
    echo "RPO/RTO Metrics"
    echo "=============================================="
    echo "RPO (Recovery Point Objective):"
    echo "  Backup created: $(date -d "@$BACKUP_TIMESTAMP" 2>/dev/null || date -r "$BACKUP_TIMESTAMP" 2>/dev/null || echo "unknown")"
    echo "  Drill started:  $(date -d "@$RTO_START" 2>/dev/null || date -r "$RTO_START" 2>/dev/null || echo "unknown")"
    echo "  RPO: ${RPO_HOURS}h ${RPO_MINUTES}m (${RPO_SECONDS}s)"
    echo ""
    echo "RTO (Recovery Time Objective):"
    echo "  Restore start: $(date -d "@$RTO_START" 2>/dev/null || date -r "$RTO_START" 2>/dev/null || echo "unknown")"
    echo "  Restore end:   $(date -d "@$RTO_END" 2>/dev/null || date -r "$RTO_END" 2>/dev/null || echo "unknown")"
    echo "  RTO: ${RTO_MINUTES}m ${RTO_SECONDS}s"
    echo ""
    echo "Checksum: ${SAMPLE_PASS}/${SAMPLE_COUNT} samples passed"
    echo "=============================================="
} >> "$DRILL_LOG"

echo ""
echo "=============================================="
echo "Drill Complete"
echo "  RPO: ${RPO_HOURS}h ${RPO_MINUTES}m"
echo "  RTO: ${RTO_MINUTES}m ${RTO_SECONDS}s"
echo "  Checksum: ${SAMPLE_PASS}/${SAMPLE_COUNT}"
echo "  Log: $DRILL_LOG"
echo "=============================================="

# ---- Cleanup ----
echo ""
echo "Cleaning up drill containers..."
docker stop drill-postgres drill-minio 2>/dev/null || true
docker network rm "$DRILL_NETWORK" 2>/dev/null || true
echo "Drill data preserved at: $DRILL_DIR"
echo "Remove with: rm -rf $DRILL_DIR"
