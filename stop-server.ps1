# 一键停止所有服务（数据卷保留，不丢数据）
docker compose -f docker-compose.yml -f docker-compose.cloudflare.yml down
Write-Host "全部服务已停止，数据库和文件数据已保留" -ForegroundColor Yellow
