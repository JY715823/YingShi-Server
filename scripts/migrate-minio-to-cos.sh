#!/bin/bash
# ============================================================
# MinIO → 腾讯云 COS 数据迁移脚本
# ============================================================
# 功能：从本地 MinIO 导出所有对象，上传到腾讯云 COS
#
# 用法（在服务器上执行）：
#   bash scripts/migrate-minio-to-cos.sh
#
# 前置条件：
#   1. 服务器已部署 YingShi（MinIO 容器运行中）
#   2. 已开通腾讯云 COS 并创建 bucket（见 docs/deployment/cos-setup.md）
#   3. 已安装 docker（用于运行迁移工具）
#
# 迁移原理：
#   使用 mc (MinIO Client) 列出所有对象 → 逐个复制到 COS
#   mc 原生支持 S3 协议，可直接操作 COS（COS 兼容 S3）
# ============================================================
set -e

# ---- 配置（按你的实际情况修改）----
# MinIO 源
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-yingshi_minio_access}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-yingshi_minio_secret}"
MINIO_BUCKET="${MINIO_BUCKET:-yingshi-media}"

# COS 目标（必须修改为你自己的）
COS_ENDPOINT="${COS_ENDPOINT:-https://cos.ap-guangzhou.myqcloud.com}"
COS_ACCESS_KEY="${COS_ACCESS_KEY:-YOUR_COS_SecretId}"
COS_SECRET_KEY="${COS_SECRET_KEY:-YOUR_COS_SecretKey}"
COS_BUCKET="${COS_BUCKET:-yingshi-prod-1258000000}"
COS_REGION="${COS_REGION:-ap-guangzhou}"

# ---- 校验配置 ----
if [[ "$COS_ACCESS_KEY" == "YOUR_COS_SecretId" ]] || [[ "$COS_SECRET_KEY" == "YOUR_COS_SecretKey" ]]; then
    echo "❌ 请先设置 COS_ACCESS_KEY 和 COS_SECRET_KEY 环境变量"
    echo "   export COS_ACCESS_KEY=AKIDxxxxxxxxxxxx"
    echo "   export COS_SECRET_KEY=xxxxxxxxxxxxxxxx"
    echo "   export COS_BUCKET=yingshi-prod-1258000000"
    echo "   然后重新运行此脚本"
    exit 1
fi

echo "============================================================"
echo "  MinIO → COS 数据迁移"
echo "  源: $MINIO_ENDPOINT/$MINIO_BUCKET"
echo "  目标: $COS_ENDPOINT/$COS_BUCKET"
echo "============================================================"

# ---- 1. 启动 mc 容器（与 MinIO 同网络）----
echo ""
echo "[1/4] 准备迁移工具..."
MC_CONTAINER="yingshi-mc-migrate"
docker rm -f "$MC_CONTAINER" 2>/dev/null || true

# 找到 YingShi 的 docker 网络
NETWORK=$(docker network ls --filter "name=yingshi" --format "{{.Name}}" | head -1)
if [ -z "$NETWORK" ]; then
    echo "⚠️  未找到 yingshi docker 网络，使用 host 网络"
    NETWORK_ARG="--network host"
    MINIO_ENDPOINT="http://localhost:9000"
else
    NETWORK_ARG="--network $NETWORK"
fi

docker run -d --name "$MC_CONTAINER" $NETWORK_ARG \
    minio/mc:latest sh -c "tail -f /dev/null" >/dev/null

echo "    mc 容器已启动: $MC_CONTAINER"

# ---- 2. 配置 alias ----
echo ""
echo "[2/4] 配置源(MinIO)和目标(COS)..."
docker exec "$MC_CONTAINER" mc alias set src \
    "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" 2>&1 | tail -3

# COS 用 S3 兼容模式，path style 关闭
docker exec "$MC_CONTAINER" mc alias set dst \
    "$COS_ENDPOINT" "$COS_ACCESS_KEY" "$COS_SECRET_KEY" 2>&1 | tail -3

# ---- 3. 执行迁移 ----
echo ""
echo "[3/4] 开始迁移（按对象逐个复制，可中断续传）..."
echo "    源对象总数: $(docker exec "$MC_CONTAINER" mc ls --recursive "src/$MINIO_BUCKET" 2>/dev/null | wc -l)"

# 使用 mc mirror 批量复制（带覆盖=不强覆盖，避免重复上传浪费流量）
START_TIME=$(date +%s)
docker exec "$MC_CONTAINER" mc mirror \
    --overwrite \
    --retry 3 \
    "src/$MINIO_BUCKET" \
    "dst/$COS_BUCKET" 2>&1 | tee /tmp/migrate.log | tail -20

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# ---- 4. 验证 ----
echo ""
echo "[4/4] 验证迁移结果..."
SRC_COUNT=$(docker exec "$MC_CONTAINER" mc ls --recursive "src/$MINIO_BUCKET" 2>/dev/null | wc -l)
DST_COUNT=$(docker exec "$MC_CONTAINER" mc ls --recursive "dst/$COS_BUCKET" 2>/dev/null | wc -l)

echo "    源对象数: $SRC_COUNT"
echo "    目标对象数: $DST_COUNT"
echo "    迁移耗时: ${DURATION}秒"

if [ "$SRC_COUNT" -eq "$DST_COUNT" ]; then
    echo "    ✅ 迁移成功！对象数量一致"
else
    echo "    ⚠️  对象数量不一致，请检查 /tmp/migrate.log"
    echo "    可重新运行脚本，mc mirror 会自动跳过已上传的对象"
fi

# ---- 清理 ----
docker rm -f "$MC_CONTAINER" >/dev/null 2>&1

echo ""
echo "============================================================"
echo "  迁移完成！下一步："
echo "  1. 修改 /opt/yingshi/.env.prod 的 STORAGE_PROVIDER=cos"
echo "  2. 重启服务: docker compose -f docker-compose.prod.yml up -d server"
echo "  3. 用 App 验证能正常访问历史图片"
echo "  4. 验证无误后，可停止 MinIO 容器（保留7天作为备份）"
echo "     docker compose -f docker-compose.prod.yml stop minio"
echo "============================================================"
