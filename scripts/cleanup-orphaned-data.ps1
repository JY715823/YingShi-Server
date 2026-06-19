# ============================================================
# Complete cleanup script for orphaned MinIO objects + DB records
# Requires: Docker running, mc available in minio container
# Usage: .\scripts\cleanup-orphaned-data.ps1
# ============================================================

$ErrorActionPreference = "Stop"

Write-Host "=== Step 1: Setup MinIO client alias ==="
docker exec yingshi-minio mc alias set local http://localhost:9000 yingshi_minio_access yingshi_minio_secret 2>&1

Write-Host "`n=== Step 2: Find orphaned MinIO originals (in MinIO but NOT in DB media) ==="

# Get all MinIO originals
$minioOriginals = docker exec yingshi-minio mc ls --recursive "local/yingshi-media/originals/" 2>&1 |
    Where-Object { $_ -match '\d{4}-\d{2}-\d{2}' } |
    ForEach-Object { ($_ -split '\s+')[-1] }

# Get all DB media storage_paths
$dbPaths = docker exec yingshi-postgres psql -U yingshi -d yingshi -t -c "SELECT storage_path FROM media WHERE deleted_at IS NULL;" 2>&1 |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }

Write-Host "MinIO originals: $($minioOriginals.Count)"
Write-Host "DB media records: $($dbPaths.Count)"

$orphanedOriginals = $minioOriginals | Where-Object { $_ -notin $dbPaths }
Write-Host "Orphaned MinIO originals: $($orphanedOriginals.Count)"

Write-Host "`n=== Step 3: Find orphaned MinIO previews ==="
$minioPreviews = docker exec yingshi-minio mc ls --recursive "local/yingshi-media/previews/" 2>&1 |
    Where-Object { $_ -match '\d{4}-\d{2}-\d{2}' } |
    ForEach-Object { ($_ -split '\s+')[-1] }

$dbPreviews = docker exec yingshi-postgres psql -U yingshi -d yingshi -t -c "SELECT preview_object_key FROM media WHERE preview_object_key IS NOT NULL UNION SELECT cover_object_key FROM media WHERE cover_object_key IS NOT NULL;" 2>&1 |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }

$orphanedPreviews = $minioPreviews | Where-Object { $_ -notin $dbPreviews }
Write-Host "MinIO previews: $($minioPreviews.Count)"
Write-Host "DB preview/cover refs: $($dbPreviews.Count)"
Write-Host "Orphaned MinIO previews: $($orphanedPreviews.Count)"

Write-Host "`n=== Step 4: Delete orphaned MinIO objects ==="
$allOrphans = @($orphanedOriginals) + @($orphanedPreviews)
if ($allOrphans.Count -eq 0) {
    Write-Host "No orphaned objects found. MinIO is clean!"
} else {
    Write-Host "Deleting $($allOrphans.Count) orphaned objects..."
    foreach ($obj in $allOrphans) {
        Write-Host "  Deleting: $obj"
        docker exec yingshi-minio mc rm "local/yingshi-media/$obj" 2>&1
    }
}

Write-Host "`n=== Step 5: Clean up dangling upload_tasks ==="
Write-Host "Dangling SUCCESS tasks (stored_path but no matching media):"
docker exec yingshi-postgres psql -U yingshi -d yingshi -c "SELECT COUNT(*) FROM upload_tasks WHERE stored_path IS NOT NULL AND stored_path NOT IN (SELECT storage_path FROM media WHERE deleted_at IS NULL);"

Write-Host "`nExpired WAITING tasks:"
docker exec yingshi-postgres psql -U yingshi -d yingshi -c "SELECT COUNT(*) FROM upload_tasks WHERE state = 'WAITING' AND expire_at < NOW();"

Write-Host "`nDeleting dangling SUCCESS tasks..."
docker exec yingshi-postgres psql -U yingshi -d yingshi -c "DELETE FROM upload_tasks WHERE stored_path IS NOT NULL AND stored_path NOT IN (SELECT storage_path FROM media WHERE deleted_at IS NULL);"

Write-Host "`nDeleting expired WAITING tasks..."
docker exec yingshi-postgres psql -U yingshi -d yingshi -c "DELETE FROM upload_tasks WHERE state = 'WAITING' AND expire_at < NOW();"

Write-Host "`nDeleting cancelled tasks..."
docker exec yingshi-postgres psql -U yingshi -d yingshi -c "DELETE FROM upload_tasks WHERE state = 'CANCELLED';"

Write-Host "`n=== Step 6: Final verification ==="
$finalMinioOriginal = (docker exec yingshi-minio mc ls --recursive "local/yingshi-media/originals/" 2>&1 | Where-Object { $_ -match '\d{4}-\d{2}-\d{2}' }).Count
$finalMinioPreview = (docker exec yingshi-minio mc ls --recursive "local/yingshi-media/previews/" 2>&1 | Where-Object { $_ -match '\d{4}-\d{2}-\d{2}' }).Count
$finalMedia = docker exec yingshi-postgres psql -U yingshi -d yingshi -t -c "SELECT COUNT(*) FROM media WHERE deleted_at IS NULL;" 2>&1 | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
$finalTasks = docker exec yingshi-postgres psql -U yingshi -d yingshi -t -c "SELECT COUNT(*) FROM upload_tasks;" 2>&1 | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }

Write-Host "MinIO originals: $finalMinioOriginal"
Write-Host "MinIO previews: $finalMinioPreview"
Write-Host "DB media (active): $finalMedia"
Write-Host "DB upload_tasks: $finalTasks"

Write-Host "`nCleanup complete!"
