#!/bin/bash

set -e

YINGSHI_DIR="/opt/yingshi"
YINGSHI_DOMAIN="yingshi92.xyz"
YINGSHI_API_SUBDOMAIN="api"
YINGSHI_FULL_DOMAIN="${YINGSHI_API_SUBDOMAIN}.${YINGSHI_DOMAIN}"

echo "=============================================="
echo "  YingShi Server Deployment Script"
echo "  Domain: ${YINGSHI_FULL_DOMAIN}"
echo "  Target: ${YINGSHI_DIR}"
echo "=============================================="

echo ""
echo "[1/7] Installing system dependencies..."
echo "----------------------------------------"
apt-get update -y
apt-get upgrade -y
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    software-properties-common \
    nginx \
    certbot \
    python3-certbot-nginx \
    git

echo ""
echo "[2/7] Installing Docker..."
echo "----------------------------------------"
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable docker
systemctl start docker

echo ""
echo "[3/7] Creating YingShi directory structure..."
echo "----------------------------------------"
mkdir -p ${YINGSHI_DIR}
mkdir -p ${YINGSHI_DIR}/nginx
mkdir -p ${YINGSHI_DIR}/postgres-data
mkdir -p ${YINGSHI_DIR}/minio-data

echo ""
echo "[4/7] Configuring firewall..."
echo "----------------------------------------"
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo ""
echo "[5/7] Creating production .env file..."
echo "----------------------------------------"
cat > ${YINGSHI_DIR}/.env << EOF
POSTGRES_DB=yingshi
POSTGRES_USER=yingshi
POSTGRES_PASSWORD=$(openssl rand -hex 32)
POSTGRES_HOST_PORT=5432

MINIO_ROOT_USER=yingshi_minio_access
MINIO_ROOT_PASSWORD=$(openssl rand -hex 32)
MINIO_BUCKET=yingshi-media

SPRING_PROFILES_ACTIVE=docker,prod
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/yingshi
SPRING_DATASOURCE_USERNAME=yingshi
SPRING_DATASOURCE_PASSWORD=$(openssl rand -hex 32)
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_BASELINE_VERSION=1

STORAGE_PROVIDER=s3
STORAGE_BUCKET=yingshi-media
STORAGE_ENDPOINT=http://minio:9000
STORAGE_REGION=us-east-1
STORAGE_ACCESS_KEY=yingshi_minio_access
STORAGE_SECRET_KEY=$(openssl rand -hex 32)
STORAGE_CDN_DOMAIN=
STORAGE_CDN_AUTH_KEY=
STORAGE_CDN_SIGN_PARAM=sign
STORAGE_CDN_TIMESTAMP_PARAM=t
STORAGE_SIGNED_URL_TTL=PT15M
STORAGE_DIRECT_UPLOAD_ENABLED=true
STORAGE_FORCE_PATH_STYLE=true

APP_AUTH_JWT_SECRET=$(openssl rand -hex 32)
APP_PRODUCTION_SAFETY_ENABLED=true
APP_CORS_ALLOWED_ORIGINS=https://${YINGSHI_DOMAIN},https://www.${YINGSHI_DOMAIN}

SERVER_PORT=8080
SERVER_HOST_PORT=8080

AUTH_MAIL_ENABLED=true
AUTH_MAIL_HOST=smtp.qq.com
AUTH_MAIL_PORT=587
AUTH_MAIL_USERNAME=1085060329@qq.com
AUTH_MAIL_PASSWORD=replace-with-qq-smtp-auth-code
AUTH_MAIL_FROM_ADDRESS=1085060329@qq.com
AUTH_MAIL_FROM_NAME=映世
AUTH_MAIL_AUTH=true
AUTH_MAIL_STARTTLS=true

FCM_ENABLED=false
FCM_DRY_RUN=false
FCM_PROJECT_ID=
FCM_SERVICE_ACCOUNT_HOST_PATH=
FCM_SERVICE_ACCOUNT_PATH=
FCM_SERVICE_ACCOUNT_JSON_BASE64=
PUSH_SELF_FALLBACK_ENABLED=false

AMAP_GEOCODING_ENABLED=true
AMAP_GEOCODING_KEY=a658afca501f9337e964af99b3f2670f
AMAP_GEOCODING_ENDPOINT=https://restapi.amap.com/v3/geocode/regeo
AMAP_GEOCODING_TIMEOUT_MILLIS=3000

STORAGE_HOST_PATH=${YINGSHI_DIR}
EOF

echo ""
echo "[6/7] Creating Docker Compose files..."
echo "----------------------------------------"

cat > ${YINGSHI_DIR}/docker-compose.yml << 'EOF'
services:
  postgres:
    image: postgres:16-alpine
    container_name: yingshi-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-yingshi}
      POSTGRES_USER: ${POSTGRES_USER:-yingshi}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-yingshi_dev_password}
    ports:
      - "127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432"
    volumes:
      - ${STORAGE_HOST_PATH:-/opt/yingshi}/postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-yingshi} -d ${POSTGRES_DB:-yingshi}"]
      interval: 10s
      timeout: 5s
      retries: 10
    restart: unless-stopped

  minio:
    image: minio/minio:RELEASE.2025-04-22T22-12-26Z
    container_name: yingshi-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-yingshi_minio_access}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-yingshi_minio_secret}
    ports:
      - "127.0.0.1:${MINIO_API_PORT:-9000}:9000"
      - "127.0.0.1:9001:9001"
    volumes:
      - ${STORAGE_HOST_PATH:-/opt/yingshi}/minio-data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 10
    restart: unless-stopped

  minio-init:
    image: minio/mc:RELEASE.2025-04-16T18-13-26Z
    container_name: yingshi-minio-init
    depends_on:
      minio:
        condition: service_healthy
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-yingshi_minio_access}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-yingshi_minio_secret}
      MINIO_BUCKET: ${MINIO_BUCKET:-yingshi-media}
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 $$MINIO_ROOT_USER $$MINIO_ROOT_PASSWORD &&
      mc mb --ignore-existing local/$$MINIO_BUCKET &&
      mc anonymous set none local/$$MINIO_BUCKET
      "

  server:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: yingshi-server
    depends_on:
      postgres:
        condition: service_healthy
      minio-init:
        condition: service_completed_successfully
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-docker}
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/yingshi}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:-yingshi}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD:-yingshi_dev_password}
      SPRING_JPA_HIBERNATE_DDL_AUTO: ${SPRING_JPA_HIBERNATE_DDL_AUTO:-validate}
      SPRING_FLYWAY_ENABLED: ${SPRING_FLYWAY_ENABLED:-true}
      SPRING_FLYWAY_BASELINE_ON_MIGRATE: ${SPRING_FLYWAY_BASELINE_ON_MIGRATE:-true}
      SPRING_FLYWAY_BASELINE_VERSION: ${SPRING_FLYWAY_BASELINE_VERSION:-1}
      STORAGE_PROVIDER: ${STORAGE_PROVIDER:-s3}
      STORAGE_BUCKET: ${STORAGE_BUCKET:-yingshi-media}
      STORAGE_ENDPOINT: ${STORAGE_ENDPOINT:-http://minio:9000}
      STORAGE_REGION: ${STORAGE_REGION:-us-east-1}
      STORAGE_ACCESS_KEY: ${STORAGE_ACCESS_KEY:-yingshi_minio_access}
      STORAGE_SECRET_KEY: ${STORAGE_SECRET_KEY:-yingshi_minio_secret}
      STORAGE_CDN_DOMAIN: ${STORAGE_CDN_DOMAIN:-}
      STORAGE_CDN_AUTH_KEY: ${STORAGE_CDN_AUTH_KEY:-}
      STORAGE_CDN_SIGN_PARAM: ${STORAGE_CDN_SIGN_PARAM:-sign}
      STORAGE_CDN_TIMESTAMP_PARAM: ${STORAGE_CDN_TIMESTAMP_PARAM:-t}
      STORAGE_SIGNED_URL_TTL: ${STORAGE_SIGNED_URL_TTL:-PT15M}
      STORAGE_DIRECT_UPLOAD_ENABLED: ${STORAGE_DIRECT_UPLOAD_ENABLED:-true}
      STORAGE_DIRECT_UPLOAD_PUBLIC_ENDPOINT: ${STORAGE_DIRECT_UPLOAD_PUBLIC_ENDPOINT:-}
      STORAGE_FORCE_PATH_STYLE: ${STORAGE_FORCE_PATH_STYLE:-true}
      APP_AUTH_JWT_SECRET: ${APP_AUTH_JWT_SECRET:-change-me-to-a-long-dev-secret-at-least-32-characters}
      APP_CORS_ALLOWED_ORIGINS: ${APP_CORS_ALLOWED_ORIGINS:-}
      APP_PRODUCTION_SAFETY_ENABLED: ${APP_PRODUCTION_SAFETY_ENABLED:-false}
      AUTH_MAIL_ENABLED: ${AUTH_MAIL_ENABLED:-true}
      AUTH_MAIL_HOST: ${AUTH_MAIL_HOST:-smtp.qq.com}
      AUTH_MAIL_PORT: ${AUTH_MAIL_PORT:-587}
      AUTH_MAIL_USERNAME: ${AUTH_MAIL_USERNAME:-1085060329@qq.com}
      AUTH_MAIL_PASSWORD: ${AUTH_MAIL_PASSWORD:-}
      AUTH_MAIL_FROM_ADDRESS: ${AUTH_MAIL_FROM_ADDRESS:-1085060329@qq.com}
      AUTH_MAIL_FROM_NAME: ${AUTH_MAIL_FROM_NAME:-映世}
      AUTH_MAIL_AUTH: ${AUTH_MAIL_AUTH:-true}
      AUTH_MAIL_STARTTLS: ${AUTH_MAIL_STARTTLS:-true}
      AUTH_LOGIN_CODE_LENGTH: ${AUTH_LOGIN_CODE_LENGTH:-6}
      AUTH_LOGIN_CODE_TTL: ${AUTH_LOGIN_CODE_TTL:-PT5M}
      AUTH_LOGIN_CODE_RESEND_COOLDOWN: ${AUTH_LOGIN_CODE_RESEND_COOLDOWN:-PT60S}
      AUTH_LOGIN_CODE_RATE_LIMIT_WINDOW: ${AUTH_LOGIN_CODE_RATE_LIMIT_WINDOW:-PT30M}
      AUTH_LOGIN_CODE_MAX_SENDS_PER_WINDOW: ${AUTH_LOGIN_CODE_MAX_SENDS_PER_WINDOW:-5}
      AUTH_LOGIN_CODE_MAX_ATTEMPTS_PER_CHALLENGE: ${AUTH_LOGIN_CODE_MAX_ATTEMPTS_PER_CHALLENGE:-5}
      FCM_ENABLED: ${FCM_ENABLED:-false}
      FCM_DRY_RUN: ${FCM_DRY_RUN:-false}
      FCM_PROJECT_ID: ${FCM_PROJECT_ID:-}
      FCM_SERVICE_ACCOUNT_PATH: ${FCM_SERVICE_ACCOUNT_PATH:-}
      FCM_SERVICE_ACCOUNT_JSON_BASE64: ${FCM_SERVICE_ACCOUNT_JSON_BASE64:-}
      PUSH_SELF_FALLBACK_ENABLED: ${PUSH_SELF_FALLBACK_ENABLED:-false}
      AMAP_GEOCODING_ENABLED: ${AMAP_GEOCODING_ENABLED:-true}
      AMAP_GEOCODING_KEY: ${AMAP_GEOCODING_KEY:-a658afca501f9337e964af99b3f2670f}
      AMAP_GEOCODING_ENDPOINT: ${AMAP_GEOCODING_ENDPOINT:-https://restapi.amap.com/v3/geocode/regeo}
      AMAP_GEOCODING_TIMEOUT_MILLIS: ${AMAP_GEOCODING_TIMEOUT_MILLIS:-3000}
      HTTP_PROXY: ${HTTP_PROXY:-}
      HTTPS_PROXY: ${HTTPS_PROXY:-}
      NO_PROXY: ${NO_PROXY:-localhost,127.0.0.1,postgres,minio}
      JAVA_TOOL_OPTIONS: ${JAVA_TOOL_OPTIONS:-}
      SERVER_PORT: ${SERVER_PORT:-8080}
    ports:
      - "127.0.0.1:${SERVER_HOST_PORT:-8080}:8080"
    restart: unless-stopped
EOF

cat > ${YINGSHI_DIR}/docker-compose.prod.yml << EOF
services:
  nginx:
    image: nginx:1.27-alpine
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - ./nginx/certs:/etc/letsencrypt:ro
    depends_on:
      server:
        condition: service_healthy
    networks:
      - yingshi-net

  postgres:
    extends:
      file: docker-compose.yml
      service: postgres
    networks:
      - yingshi-net

  minio:
    extends:
      file: docker-compose.yml
      service: minio
    networks:
      - yingshi-net

  minio-init:
    extends:
      file: docker-compose.yml
      service: minio-init
    networks:
      - yingshi-net

  server:
    extends:
      file: docker-compose.yml
      service: server
    ports:
      - "127.0.0.1:${SERVER_HOST_PORT:-8080}:8080"
    networks:
      - yingshi-net

networks:
  yingshi-net:
    driver: bridge
EOF

echo ""
echo "[7/7] Creating Nginx configuration..."
echo "----------------------------------------"

cat > ${YINGSHI_DIR}/nginx/nginx.conf << EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${YINGSHI_FULL_DOMAIN};
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name ${YINGSHI_FULL_DOMAIN};

    ssl_certificate     /etc/letsencrypt/live/${YINGSHI_FULL_DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${YINGSHI_FULL_DOMAIN}/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    ssl_stapling on;
    ssl_stapling_verify on;
    resolver 8.8.8.8 8.8.4.4 valid=300s;
    resolver_timeout 5s;

    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    client_max_body_size 550M;

    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    proxy_connect_timeout 60s;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$remote_addr;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Connection "";

        proxy_set_header Accept-Encoding "";
    }

    location /actuator/ {
        allow 127.0.0.1;
        allow ::1;
        deny all;

        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }
}
EOF

mkdir -p ${YINGSHI_DIR}/nginx/certs

echo ""
echo "=============================================="
echo "  Deployment Script Generated Successfully!"
echo "=============================================="
echo ""
echo "Next Steps:"
echo "-----------"
echo "1. Upload YingShi-Server source code to ${YINGSHI_DIR}"
echo "2. Run 'certbot certonly --nginx -d ${YINGSHI_FULL_DOMAIN}' to get SSL certificate"
echo "3. Update AUTH_MAIL_PASSWORD in .env with your QQ SMTP auth code"
echo "4. Run 'docker compose -f docker-compose.prod.yml up -d' to start services"
echo "5. Verify with 'curl https://${YINGSHI_FULL_DOMAIN}/api/health'"
echo ""
echo "Environment file created at: ${YINGSHI_DIR}/.env"