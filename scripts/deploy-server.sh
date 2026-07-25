#!/bin/bash
# ============================================================
# YingShi 后端一键部署脚本
# ============================================================
# 功能：打包项目 → 上传服务器 → 重建容器 → 健康检查
#
# 用法（在 YingShi-Server 目录下）：
#   bash scripts/deploy-server.sh
#
# 前置条件：
#   1. ~/.ssh/config 已配置 Host yingshi（指向 119.91.225.106，使用密钥登录）
#   2. 服务器已安装 Docker + Docker Compose
#   3. /opt/yingshi/.env 已配置好生产环境变量
#   4. SSL 证书已申请（/opt/yingshi/nginx/certs/live/yingshi92.xyz/）
#
# 首次部署额外步骤见 docs/deployment/tencent-cloud-production.md
# ============================================================
set -e

SERVER_HOST="${YINGSHI_SSH_HOST:-yingshi}"
REMOTE_DIR="${YINGSHI_REMOTE_DIR:-/opt/yingshi}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "============================================================"
echo "  YingShi 后端部署"
echo "  服务器: $SERVER_HOST"
echo "  远程目录: $REMOTE_DIR"
echo "============================================================"

# ---- 1. 打包项目（排除不需要的文件）----
echo ""
echo "[1/4] 打包项目..."
cd "$PROJECT_DIR"
TARBALL="/tmp/yingshi-server-$(date +%s).tar.gz"
tar czf "$TARBALL" \
    --exclude='.git' \
    --exclude='logs' \
    --exclude='target' \
    --exclude='*.log' \
    --exclude='.env' \
    --exclude='nginx/certs' \
    --exclude='nginx/www' \
    --exclude='build_output.log' \
    src pom.xml Dockerfile mvnw mvnw.cmd .mvn \
    docker-compose.yml docker-compose.prod.yml .env.example \
    nginx/nginx-8443.conf nginx/download 2>/dev/null
echo "    打包完成: $(du -h "$TARBALL" | cut -f1)"

# ---- 2. 上传到服务器 ----
echo ""
echo "[2/4] 上传到服务器..."
scp -q "$TARBALL" "$SERVER_HOST:/tmp/yingshi-server.tar.gz"
ssh "$SERVER_HOST" "mkdir -p $REMOTE_DIR/nginx/certs $REMOTE_DIR/nginx/www && \
    tar xzf /tmp/yingshi-server.tar.gz -C $REMOTE_DIR && \
    rm /tmp/yingshi-server.tar.gz && \
    cp -f nginx/nginx-8443.conf nginx/nginx.conf"
echo "    上传完成"

# ---- 3. 重建并启动容器 ----
echo ""
echo "[3/4] 重建容器（首次可能需要 3-5 分钟编译）..."
ssh "$SERVER_HOST" "cd $REMOTE_DIR && docker compose -f docker-compose.prod.yml up -d --build" 2>&1 | tail -20

# ---- 4. 健康检查 ----
echo ""
echo "[4/4] 健康检查..."
echo "    等待服务启动（20秒）..."
sleep 20

# 优先用 Docker 健康状态检查（不依赖端口映射）
HEALTH_STATUS=$(ssh "$SERVER_HOST" "docker inspect --format='{{.State.Health.Status}}' yingshi-server 2>/dev/null || echo 'no-healthcheck'")
if [ "$HEALTH_STATUS" = "healthy" ]; then
    echo "    ✅ 服务健康: healthy"
elif [ "$HEALTH_STATUS" = "starting" ]; then
    echo "    ⏳ 服务正在启动，再等 20 秒..."
    sleep 20
    HEALTH_STATUS=$(ssh "$SERVER_HOST" "docker inspect --format='{{.State.Health.Status}}' yingshi-server 2>/dev/null || echo 'unknown'")
    if [ "$HEALTH_STATUS" = "healthy" ]; then
        echo "    ✅ 服务健康: healthy"
    else
        echo "    ⚠️  健康检查: $HEALTH_STATUS"
        echo "    手动检查: ssh $SERVER_HOST 'docker logs yingshi-server --tail 50'"
    fi
else
    # 回退到 curl 检查（兼容无 healthcheck 的旧部署）
    HEALTH=$(ssh "$SERVER_HOST" "curl -sf http://localhost:8080/actuator/health 2>/dev/null || echo 'FAILED'")
    if echo "$HEALTH" | grep -q '"status":"UP"'; then
        echo "    ✅ 服务健康: UP"
    else
        echo "    ⚠️  健康检查未通过 ($HEALTH_STATUS)"
        echo "    手动检查: ssh $SERVER_HOST 'docker logs yingshi-server --tail 50'"
    fi
fi

# ---- 清理 ----
rm -f "$TARBALL"

echo ""
echo "============================================================"
echo "  部署完成！"
echo "  API 地址: https://yingshi92.xyz:8443/"
echo "  下载页:   https://yingshi92.xyz:8443/"
echo "  健康检查: https://yingshi92.xyz:8443/api/health"
echo "============================================================"
