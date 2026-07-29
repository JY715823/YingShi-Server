#!/bin/bash
# ============================================================
# YingShi Unified Backup Script
# ============================================================
# 统一备份：PostgreSQL + MinIO + 配置文件 + 加密 + 异地上传
#
# 备份内容：
#   1. PostgreSQL pg_dump (custom format)
#   2. MinIO mc mirror (全部 bucket)
#   3. 配置文件打包（.env, nginx conf, docker-compose）
#   4. GPG 加密打包
#   5. 异地上传（rsync/scp）
#
# 用法：
#   ./backup.sh [backup-root]
#   ./backup.sh /opt/backups/yingshi
#
# 环境变量：
#   POSTGRES_USER     (default: yingshi)
#   POSTGRES_DB       (default: yingshi)
#   MINIO_ROOT_USER   (required)
#   MINIO_ROOT_PASSWORD (required)
#   MINIO_BUCKET      (default: yingshi-media)
#   BACKUP_ENCRYPTION_KEY  (optional, GPG recipient)
#   BACKUP_REMOTE_TARGET   (optional, rsync target)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_ROOT="${1:-$PROJECT_DIR/backups}"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-yingshi-postgres}"
POSTGRES_USER="${POSTGRES_USER:-yingshi}"
POSTGRES_DB="${POSTGRES_DB:-yingshi}"
MINIO_CONTAINER="${MINIO_CONTAINER:-yingshi-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-yingshi-media}"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.prod.yml"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_SET_DIR="$BACKUP_ROOT/backup-${TIMESTAMP}"
PG_DUMP_FILE="$BACKUP_SET_DIR/postgres/${POSTGRES_DB}-${TIMESTAMP}.dump"
MINIO_BACKUP_DIR="$BACKUP_SET_DIR/minio-${MINIO_BUCKET}"
CONFIG_TARBALL="$BACKUP_SET_DIR/configs.tar.gz"
MANIFEST_FILE="$BACKUP_SET_DIR/backup-manifest.txt"
ENCRYPTED_FILE="$BACKUP_ROOT/yingshi-backup-${TIMESTAMP}.enc"

mkdir -p "$BACKUP_SET_DIR/postgres" "$MINIO_BACKUP_DIR"

echo "=============================================="
echo "YingShi Unified Backup"
echo "Backup set: $BACKUP_SET_DIR"
echo "Timestamp:  $TIMESTAMP"
echo "=============================================="

# ---- 1. PostgreSQL backup ----
echo ""
echo "[1/5] PostgreSQL pg_dump..."
if ! docker inspect "$POSTGRES_CONTAINER" > /dev/null 2>&1; then
    echo "ERROR: Container '$POSTGRES_CONTAINER' not found."
    exit 1
fi
docker exec "$POSTGRES_CONTAINER" pg_dump \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    --format=custom \
    --file="/tmp/yingshi-backup-${TIMESTAMP}.dump"
docker cp "${POSTGRES_CONTAINER}:/tmp/yingshi-backup-${TIMESTAMP}.dump" "$PG_DUMP_FILE"
docker exec "$POSTGRES_CONTAINER" rm -f "/tmp/yingshi-backup-${TIMESTAMP}.dump"
echo "    ✅ PG dump: $(du -h "$PG_DUMP_FILE" | cut -f1)"

# ---- 2. MinIO mirror ----
echo ""
echo "[2/5] MinIO mc mirror..."
MINIO_ROOT_USER="${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
MINIO_INTERNAL_ENDPOINT="http://minio:9000"

DOCKER_NETWORK=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$MINIO_CONTAINER" 2>/dev/null || echo "yingshi-server_yingshi-net")

docker run --rm \
    --network "$DOCKER_NETWORK" \
    -v "${MINIO_BACKUP_DIR}:/backup" \
    --entrypoint /bin/sh \
    minio/mc:RELEASE.2025-04-16T18-13-26Z \
    -c "mc alias set local ${MINIO_INTERNAL_ENDPOINT} ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD} >/dev/null && mc mirror --overwrite local/${MINIO_BUCKET} /backup"

MINIO_SIZE=$(du -sh "$MINIO_BACKUP_DIR" | cut -f1)
echo "    ✅ MinIO mirror: $MINIO_SIZE"

# ---- 3. Config files packaging ----
echo ""
echo "[3/5] Config files packaging..."
tar czf "$CONFIG_TARBALL" \
    -C "$PROJECT_DIR" \
    .env \
    docker-compose.prod.yml \
    nginx/nginx-8443.conf \
    2>/dev/null || true
echo "    ✅ Configs: $(du -h "$CONFIG_TARBALL" | cut -f1)"

# ---- 4. Manifest ----
echo ""
echo "[4/5] Writing manifest..."
{
    echo "YingShi Backup Manifest"
    echo "Timestamp: $(date -Iseconds)"
    echo "Backup set: $BACKUP_SET_DIR"
    echo "---"
    echo "PostgreSQL:"
    echo "  container: $POSTGRES_CONTAINER"
    echo "  database: $POSTGRES_DB"
    echo "  dump: $PG_DUMP_FILE"
    echo "  size: $(du -h "$PG_DUMP_FILE" | cut -f1)"
    echo "MinIO:"
    echo "  bucket: $MINIO_BUCKET"
    echo "  mirror: $MINIO_BACKUP_DIR"
    echo "  size: $MINIO_SIZE"
    echo "Configs:"
    echo "  tarball: $CONFIG_TARBALL"
    echo "  size: $(du -h "$CONFIG_TARBALL" | cut -f1)"
} > "$MANIFEST_FILE"
echo "    ✅ Manifest: $MANIFEST_FILE"

# ---- 5. Encrypt & offsite upload ----
echo ""
echo "[5/5] Encrypt & offsite upload..."
if tar czf - -C "$BACKUP_ROOT" "backup-${TIMESTAMP}" | gpg --batch --yes --cipher-algo AES256 \
        ${BACKUP_ENCRYPTION_KEY:+--recipient "$BACKUP_ENCRYPTION_KEY"} \
        --output "$ENCRYPTED_FILE" 2>/dev/null; then
    echo "    ✅ Encrypted: $(du -h "$ENCRYPTED_FILE" | cut -f1) → $ENCRYPTED_FILE"
else
    echo "    ⚠️  GPG encryption failed or no key configured; skipping encryption."
    echo "    Raw backup remains at: $BACKUP_SET_DIR"
    ENCRYPTED_FILE=""
fi

if [ -n "$ENCRYPTED_FILE" ] && [ -n "${BACKUP_REMOTE_TARGET:-}" ]; then
    echo "    Uploading to $BACKUP_REMOTE_TARGET..."
    if rsync -azP "$ENCRYPTED_FILE" "$BACKUP_REMOTE_TARGET/" 2>/dev/null || \
       scp "$ENCRYPTED_FILE" "$BACKUP_REMOTE_TARGET/" 2>/dev/null; then
        echo "    ✅ Offsite upload complete"
    else
        echo "    ⚠️  Offsite upload failed; backup remains local at $ENCRYPTED_FILE"
    fi
fi

# ---- Cleanup old backups (keep 7 days) ----
find "$BACKUP_ROOT" -name "backup-*" -type d -mtime +7 -exec rm -rf {} \; 2>/dev/null || true
find "$BACKUP_ROOT" -name "yingshi-backup-*.enc" -mtime +7 -delete 2>/dev/null || true

echo ""
echo "=============================================="
echo "Backup Complete"
echo "Backup set: $BACKUP_SET_DIR"
echo "=============================================="
