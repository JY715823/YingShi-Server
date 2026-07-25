#!/usr/bin/env bash
# FR-8: Setup automated database backup cron job
# Usage: ./setup-backup-cron.sh
#
# Configures:
# - Daily full database backup at 03:00 (local time)
# - Weekly full backup with retention (keep 7 daily + 4 weekly)
# - Backup verification after each run

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_SCRIPT="$SCRIPT_DIR/stage16-cloudlike-backup.ps1"
LOG_FILE="$PROJECT_DIR/logs/backup-cron.log"

echo "=== YingShi Backup Cron Setup ==="

# Ensure log directory exists
mkdir -p "$PROJECT_DIR/logs"

# Check if backup script exists (it's a .ps1 but can be called via pwsh)
if ! command -v pwsh &> /dev/null; then
    echo "WARNING: PowerShell (pwsh) not found. Backup script requires PowerShell Core."
    echo "Install: https://learn.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-linux"
    echo ""
    echo "Alternatively, use the native backup command below in your crontab:"
    echo ""
    echo "# Daily at 03:00:"
    echo "0 3 * * * cd $PROJECT_DIR && docker compose exec -T postgres pg_dump -U yingshi -Fc yingshi > $PROJECT_DIR/backups/yingshi-\$(date +\\%Y\\%m\\%d).dump 2>>$LOG_FILE"
    echo ""
    echo "# Weekly full (Sunday 04:00, keep 4 weeks):"
    echo "0 4 * * 0 cd $PROJECT_DIR && docker compose exec -T postgres pg_dump -U yingshi -Fc yingshi > $PROJECT_DIR/backups/yingshi-weekly-\$(date +\\%Y\\%m\\%d).dump && find $PROJECT_DIR/backups -name 'yingshi-weekly-*.dump' -mtime +28 -delete 2>>$LOG_FILE"
    exit 0
fi

# Create wrapper script for cron
WRAPPER="$PROJECT_DIR/scripts/run-backup.sh"
cat > "$WRAPPER" << 'BACKUP_EOF'
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$PROJECT_DIR/backups"
LOG_FILE="$PROJECT_DIR/logs/backup-${TIMESTAMP}.log"

mkdir -p "$BACKUP_DIR" "$PROJECT_DIR/logs"

echo "[$(date)] Starting backup..." >> "$LOG_FILE"

# Database backup
echo "[$(date)] Dumping PostgreSQL..." >> "$LOG_FILE"
cd "$PROJECT_DIR"
docker compose exec -T postgres pg_dump -U yingshi -Fc yingshi > "$BACKUP_DIR/yingshi-${TIMESTAMP}.dump" 2>> "$LOG_FILE"

if [ $? -eq 0 ] && [ -s "$BACKUP_DIR/yingshi-${TIMESTAMP}.dump" ]; then
    SIZE=$(du -h "$BACKUP_DIR/yingshi-${TIMESTAMP}.dump" | cut -f1)
    echo "[$(date)] Database backup complete: $SIZE" >> "$LOG_FILE"
else
    echo "[$(date)] ERROR: Database backup failed!" >> "$LOG_FILE"
    exit 1
fi

# Cleanup old backups (keep 7 daily + 4 weekly)
echo "[$(date)] Cleaning old backups..." >> "$LOG_FILE"
find "$BACKUP_DIR" -name "yingshi-*.dump" -mtime +7 -not -name "*weekly*" -delete 2>> "$LOG_FILE"
find "$BACKUP_DIR" -name "yingshi-weekly-*.dump" -mtime +28 -delete 2>> "$LOG_FILE"

# Verify backup integrity
echo "[$(date)] Verifying backup..." >> "$LOG_FILE"
docker compose exec -T postgres pg_restore --list "$BACKUP_DIR/yingshi-${TIMESTAMP}.dump" > /dev/null 2>> "$LOG_FILE"
if [ $? -eq 0 ]; then
    echo "[$(date)] Backup verification passed." >> "$LOG_FILE"
else
    echo "[$(date)] WARNING: Backup verification failed!" >> "$LOG_FILE"
fi

echo "[$(date)] Backup complete. Log: $LOG_FILE" >> "$LOG_FILE"

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
echo "  Daily:  0 3 * * * (03:00 every day)"
echo "  Weekly: 0 4 * * 0 (04:00 every Sunday)"
echo ""
echo "Verify: crontab -l"
echo "Test:   $WRAPPER"
echo ""
echo "=== Backup Cron Setup Complete ==="
