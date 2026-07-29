#!/usr/bin/env bash
# FR-8: Setup automated backup cron job
# Usage: ./setup-backup-cron.sh
#
# Configures:
# - Daily full backup at 03:00 (local time) via backup.sh
# - Weekly full backup with retention (keep 7 daily + 4 weekly)
# - Backup verification after each run via verify-backup.sh
# - Failure webhook alert via ALERT_WEBHOOK env var
#
# R0-C: Rewritten to use new backup.sh / verify-backup.sh (was stage16-cloudlike-backup.ps1)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_SCRIPT="$SCRIPT_DIR/backup.sh"
VERIFY_SCRIPT="$SCRIPT_DIR/verify-backup.sh"
LOG_FILE="$PROJECT_DIR/logs/backup-cron.log"

echo "=== YingShi Backup Cron Setup ==="

# Ensure log directory exists
mkdir -p "$PROJECT_DIR/logs"

# Verify new backup script exists
if [ ! -f "$BACKUP_SCRIPT" ]; then
    echo "ERROR: backup.sh not found at $BACKUP_SCRIPT"
    echo "Ensure R0-C backup scripts are deployed before running this setup."
    exit 1
fi

if [ ! -x "$BACKUP_SCRIPT" ]; then
    chmod +x "$BACKUP_SCRIPT" || true
fi

if [ ! -x "$VERIFY_SCRIPT" ]; then
    chmod +x "$VERIFY_SCRIPT" || true
fi

# Check required environment variables
MISSING_VARS=()
if [ -z "${PG_USER:-}" ]; then MISSING_VARS+=("PG_USER"); fi
if [ -z "${PG_DB:-}" ]; then MISSING_VARS+=("PG_DB"); fi
if [ -z "${MINIO_ALIAS:-}" ]; then MISSING_VARS+=("MINIO_ALIAS"); fi

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo "WARNING: The following environment variables are not set: ${MISSING_VARS[*]}"
    echo "backup.sh requires these to be exported in the cron environment."
    echo "Consider adding them to /etc/environment or a cron env file."
    echo ""
fi

# Create wrapper script for cron (calls new backup.sh + verify-backup.sh)
WRAPPER="$PROJECT_DIR/scripts/run-backup.sh"
cat > "$WRAPPER" << 'BACKUP_EOF'
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$PROJECT_DIR/logs/backup-${TIMESTAMP}.log"

mkdir -p "$PROJECT_DIR/logs"

echo "[$(date)] Starting backup via backup.sh..." >> "$LOG_FILE"

# R0-C: Call unified backup.sh (PG + MinIO + config + encrypt + upload)
if ! "$SCRIPT_DIR/backup.sh" >> "$LOG_FILE" 2>&1; then
    echo "[$(date)] ERROR: backup.sh failed!" >> "$LOG_FILE"
    # Send failure alert if webhook configured
    if [ -n "${ALERT_WEBHOOK:-}" ]; then
        curl -sf -X POST "$ALERT_WEBHOOK" \
            -H "Content-Type: application/json" \
            -d "{\"event\":\"backup_failed\",\"timestamp\":\"$(date -Iseconds)\",\"log\":\"$LOG_FILE\"}" \
            >> "$LOG_FILE" 2>&1 || true
    fi
    exit 1
fi

echo "[$(date)] Backup complete. Running verification..." >> "$LOG_FILE"

# R0-C: Verify backup integrity
if [ -x "$SCRIPT_DIR/verify-backup.sh" ]; then
    if ! "$SCRIPT_DIR/verify-backup.sh" >> "$LOG_FILE" 2>&1; then
        echo "[$(date)] WARNING: Backup verification failed!" >> "$LOG_FILE"
        if [ -n "${ALERT_WEBHOOK:-}" ]; then
            curl -sf -X POST "$ALERT_WEBHOOK" \
                -H "Content-Type: application/json" \
                -d "{\"event\":\"backup_verify_failed\",\"timestamp\":\"$(date -Iseconds)\",\"log\":\"$LOG_FILE\"}" \
                >> "$LOG_FILE" 2>&1 || true
        fi
    else
        echo "[$(date)] Backup verification passed." >> "$LOG_FILE"
    fi
else
    echo "[$(date)] WARNING: verify-backup.sh not found, skipping verification." >> "$LOG_FILE"
fi

echo "[$(date)] Backup pipeline complete. Log: $LOG_FILE" >> "$LOG_FILE"

# Rotate log files (keep last 30)
ls -t "$PROJECT_DIR/logs"/backup-*.log 2>/dev/null | tail -n +31 | xargs -r rm
BACKUP_EOF

chmod +x "$WRAPPER"
echo "Created backup wrapper: $WRAPPER"

# Install cron job
CRON_ENTRY="0 3 * * * $WRAPPER"
CRON_WEEKLY="0 4 * * 0 $WRAPPER --weekly"

# Check existing cron
EXISTING=$(crontab -l 2>/dev/null || echo "")
if echo "$EXISTING" | grep -q "run-backup.sh"; then
    echo "Backup cron already configured. Updating..."
    echo "$EXISTING" | grep -v "run-backup.sh" | crontab -
fi

# Add new cron entries
(crontab -l 2>/dev/null; echo "$CRON_ENTRY"; echo "$CRON_WEEKLY") | crontab -

echo ""
echo "Cron jobs installed:"
echo "  Daily:  0 3 * * * (03:00 every day) -> backup.sh + verify-backup.sh"
echo "  Weekly: 0 4 * * 0 (04:00 every Sunday) -> backup.sh --weekly + verify-backup.sh"
echo ""
echo "Required env vars (export in cron environment):"
echo "  PG_USER, PG_DB, MINIO_ALIAS"
echo "  Optional: ALERT_WEBHOOK (failure notification URL)"
echo ""
echo "Verify: crontab -l"
echo "Test:   $WRAPPER"
echo ""
echo "=== Backup Cron Setup Complete ==="
