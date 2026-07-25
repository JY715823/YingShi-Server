#!/usr/bin/env bash
# FR-4: SSL certificate renewal script for YingShi Server
# Usage: ./renew-ssl.sh [--dry-run]
#
# Procedure:
# 1. Renew certificates via certbot
# 2. Reload nginx to pick up new certificates
# 3. Verify TLS connectivity
#
# Prerequisites:
# - certbot installed and configured
# - nginx running as Docker container

set -euo pipefail

DRY_RUN="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== YingShi SSL Certificate Renewal ==="

# Step 1: Renew certificates
echo "Step 1: Renewing certificates..."
if [ "$DRY_RUN" = "--dry-run" ]; then
    echo "  [DRY RUN] certbot renew --dry-run"
    certbot renew --dry-run
else
    certbot renew --quiet --no-random-sleep-on-renew
    echo "  Certificates renewed."
fi

# Step 2: Reload nginx container
echo "Step 2: Reloading nginx..."
cd "$PROJECT_DIR"
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec nginx nginx -s reload 2>/dev/null || {
    echo "  Warning: Could not reload nginx via docker exec, trying docker restart..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml restart nginx
}
echo "  Nginx reloaded."

# Step 3: Verify TLS
echo "Step 3: Verifying TLS connectivity..."
DOMAIN=$(grep -oP 'APP_CORS_ALLOWED_ORIGINS=\K[^#\s]+' "$PROJECT_DIR/.env" 2>/dev/null | head -1 | sed 's|https://||' || echo "")
if [ -n "$DOMAIN" ]; then
    DOMAIN=$(echo "$DOMAIN" | cut -d: -f1)
    if curl -sf "https://$DOMAIN" --max-time 10 > /dev/null 2>&1; then
        echo "  SUCCESS: TLS connection to $DOMAIN verified."
    else
        echo "  WARNING: Could not verify TLS connection to $DOMAIN"
    fi
else
    echo "  Skipped: Could not determine domain from .env"
fi

# Step 4: Show certificate expiry
echo "Step 4: Certificate info..."
CERT_DIR="/etc/letsencrypt/live"
if [ -d "$CERT_DIR" ]; then
    for cert_path in "$CERT_DIR"/*/fullchain.pem; do
        if [ -f "$cert_path" ]; then
            expiry=$(openssl x509 -enddate -noout -in "$cert_path" 2>/dev/null | cut -d= -f2)
            echo "  $cert_path: expires $expiry"
        fi
    done
else
    echo "  Certificate directory not found (certificates may be Docker-mounted)"
fi

echo "=== SSL Renewal Complete ==="
