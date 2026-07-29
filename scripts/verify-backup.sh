#!/bin/bash
# ============================================================
# YingShi Backup Verification Script
# ============================================================
# 备份完整性验证：PG pg_restore --list + MinIO mc ls + 配置存在性 + 抽样 checksum
#
# 用法：
#   ./verify-backup.sh <backup-set-dir>
#   ./verify-backup.sh /opt/backups/yingshi/backup-20260101-030000
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKUP_SET_DIR="${1:?Usage: verify-backup.sh <backup-set-dir>}"

if [ ! -d "$BACKUP_SET_DIR" ]; then
    echo "ERROR: Backup set directory not found: $BACKUP_SET_DIR"
    exit 1
fi

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-yingshi-postgres}"
MINIO_CONTAINER="${MINIO_CONTAINER:-yingshi-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-yingshi-media}"
STORAGE_ENDPOINT="${STORAGE_ENDPOINT:-http://minio:9000}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-}"

PASS=0
FAIL=0
WARN=0

echo "=============================================="
echo "YingShi Backup Verification"
echo "Backup set: $BACKUP_SET_DIR"
echo "=============================================="

# ---- 1. PostgreSQL: pg_restore --list ----
echo ""
echo "[1/4] PostgreSQL dump verification..."
PG_DUMP=$(find "$BACKUP_SET_DIR" -name "*.dump" -type f | head -1)
if [ -z "$PG_DUMP" ]; then
    echo "    ❌ No .dump file found"
    FAIL=$((FAIL + 1))
elif [ ! -s "$PG_DUMP" ]; then
    echo "    ❌ Dump file is empty: $PG_DUMP"
    FAIL=$((FAIL + 1))
elif docker inspect "$POSTGRES_CONTAINER" > /dev/null 2>&1; then
    CONTAINER_DUMP="/tmp/yingshi-verify-$(date +%s).dump"
    docker cp "$PG_DUMP" "${POSTGRES_CONTAINER}:${CONTAINER_DUMP}"
    if docker exec "$POSTGRES_CONTAINER" pg_restore --list "$CONTAINER_DUMP" > /dev/null 2>&1; then
        TABLE_COUNT=$(docker exec "$POSTGRES_CONTAINER" pg_restore --list "$CONTAINER_DUMP" 2>/dev/null | grep -c "TABLE DATA" || echo "0")
        echo "    ✅ Dump valid (${TABLE_COUNT} table data entries)"
        PASS=$((PASS + 1))
    else
        echo "    ❌ pg_restore --list failed (corrupt dump)"
        FAIL=$((FAIL + 1))
    fi
    docker exec "$POSTGRES_CONTAINER" rm -f "$CONTAINER_DUMP"
else
    echo "    ⚠️  PostgreSQL container not running; cannot verify dump contents"
    WARN=$((WARN + 1))
fi

# ---- 2. MinIO: mc ls ----
echo ""
echo "[2/4] MinIO mirror verification..."
MINIO_BACKUP=$(find "$BACKUP_SET_DIR" -type d -name "minio-*" | head -1)
if [ -z "$MINIO_BACKUP" ]; then
    echo "    ❌ No MinIO backup directory found"
    FAIL=$((FAIL + 1))
elif [ ! -d "$MINIO_BACKUP" ] || [ -z "$(ls -A "$MINIO_BACKUP" 2>/dev/null)" ]; then
    echo "    ❌ MinIO backup directory is empty"
    FAIL=$((FAIL + 1))
else
    FILE_COUNT=$(find "$MINIO_BACKUP" -type f | wc -l)
    TOTAL_SIZE=$(du -sh "$MINIO_BACKUP" | cut -f1)
    echo "    ✅ MinIO mirror: ${FILE_COUNT} files, ${TOTAL_SIZE}"
    PASS=$((PASS + 1))
fi

# ---- 3. Config files existence ----
echo ""
echo "[3/4] Config files verification..."
CONFIG_TARBALL="$BACKUP_SET_DIR/configs.tar.gz"
MANIFEST="$BACKUP_SET_DIR/backup-manifest.txt"

if [ -f "$CONFIG_TARBALL" ]; then
    echo "    ✅ Config tarball exists: $(du -h "$CONFIG_TARBALL" | cut -f1)"
    PASS=$((PASS + 1))
    # Verify tarball is readable
    if tar tzf "$CONFIG_TARBALL" > /dev/null 2>&1; then
        CONFIG_FILES=$(tar tzf "$CONFIG_TARBALL" | wc -l)
        echo "    ✅ Config tarball valid (${CONFIG_FILES} entries)"
    else
        echo "    ❌ Config tarball corrupt"
        FAIL=$((FAIL + 1))
    fi
else
    echo "    ❌ Config tarball missing"
    FAIL=$((FAIL + 1))
fi

if [ -f "$MANIFEST" ]; then
    echo "    ✅ Manifest exists"
    PASS=$((PASS + 1))
else
    echo "    ⚠️  Manifest missing"
    WARN=$((WARN + 1))
fi

# ---- 4. Sample media checksum ----
echo ""
echo "[4/4] Sample media checksum verification..."
if [ -n "$MINIO_BACKUP" ] && [ -d "$MINIO_BACKUP" ] && \
   [ -n "$MINIO_ROOT_USER" ] && [ -n "$MINIO_ROOT_PASSWORD" ] && \
   docker inspect "$MINIO_CONTAINER" > /dev/null 2>&1; then

    DOCKER_NETWORK=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$MINIO_CONTAINER" 2>/dev/null || echo "yingshi-server_yingshi-net")
    ABSOLUTE_MINIO_BACKUP=$(cd "$MINIO_BACKUP" && pwd)

    SAMPLE_COUNT=0
    SAMPLE_PASS=0
    for sample in $(find "$MINIO_BACKUP" -type f | head -5); do
        SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
        LOCAL_HASH=$(sha256sum "$sample" | cut -d' ' -f1)
        REL_PATH=${sample#$MINIO_BACKUP/}

        REMOTE_HASH=$(docker run --rm \
            --network "$DOCKER_NETWORK" \
            -v "${ABSOLUTE_MINIO_BACKUP}:/backup" \
            --entrypoint /bin/sh \
            minio/mc:RELEASE.2025-04-16T18-13-26Z \
            -c "mc alias set local ${STORAGE_ENDPOINT} ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} >/dev/null && mc cat local/${MINIO_BUCKET}/${REL_PATH} 2>/dev/null | sha256sum | cut -d' ' -f1" 2>/dev/null || echo "failed")

        if [ "$LOCAL_HASH" = "$REMOTE_HASH" ]; then
            echo "    ✅ [${SAMPLE_COUNT}] $REL_PATH: match"
            SAMPLE_PASS=$((SAMPLE_PASS + 1))
        else
            echo "    ❌ [${SAMPLE_COUNT}] $REL_PATH: mismatch (local=${LOCAL_HASH:0:16}... remote=${REMOTE_HASH:0:16}...)"
        fi
    done

    if [ $SAMPLE_COUNT -gt 0 ]; then
        echo "    Sample verification: ${SAMPLE_PASS}/${SAMPLE_COUNT} passed"
        if [ $SAMPLE_PASS -eq $SAMPLE_COUNT ]; then
            PASS=$((PASS + 1))
        else
            FAIL=$((FAIL + 1))
        fi
    fi
else
    echo "    ⚠️  Cannot verify checksums (MinIO credentials missing or container not running)"
    WARN=$((WARN + 1))
fi

# ---- Summary ----
echo ""
echo "=============================================="
echo "Verification Summary"
echo "=============================================="
echo "  PASS: $PASS"
echo "  WARN: $WARN"
echo "  FAIL: $FAIL"
echo "=============================================="

if [ $FAIL -gt 0 ]; then
    echo "RESULT: FAILED - backup has integrity issues"
    exit 1
elif [ $WARN -gt 0 ]; then
    echo "RESULT: PASSED WITH WARNINGS"
    exit 0
else
    echo "RESULT: ALL CHECKS PASSED"
    exit 0
fi
