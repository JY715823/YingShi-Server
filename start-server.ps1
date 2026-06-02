# 一键启动后端 + 数据库 + 存储 + Cloudflare 隧道
$env:HTTP_PROXY = "http://127.0.0.1:7897"
$env:HTTPS_PROXY = "http://127.0.0.1:7897"
$env:NO_PROXY = "localhost,127.0.0.1"

docker compose -f docker-compose.yml -f docker-compose.cloudflare.yml up -d --build

Write-Host ""
Write-Host "=== 服务状态 ===" -ForegroundColor Green
docker ps --format "table {{.Names}`t{{.Status}}"
Write-Host ""
Write-Host "API 地址: https://api.yingshi92.xyz" -ForegroundColor Cyan
Write-Host "健康检查: https://api.yingshi92.xyz/api/health" -ForegroundColor Cyan
