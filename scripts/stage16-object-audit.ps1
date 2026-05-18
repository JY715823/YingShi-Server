[CmdletBinding()]
param(
    [string]$PostgresContainer = "yingshi-postgres",
    [string]$PostgresUser = "yingshi",
    [string]$PostgresDatabase = "yingshi",
    [string]$MinioAlias = "local",
    [string]$MinioEndpoint = "http://minio:9000",
    [string]$MinioAccessKey = "yingshi_minio_access",
    [string]$MinioSecretKey = "yingshi_minio_secret",
    [string]$Bucket = "yingshi-media",
    [string]$ComposeNetwork = "yingshi-server_default",
    [string]$MinioContainer = "yingshi-minio"
)

$ErrorActionPreference = "Stop"

function Resolve-ComposeNetwork {
    docker network inspect $ComposeNetwork *> $null
    if ($LASTEXITCODE -eq 0) {
        return $ComposeNetwork
    }

    $networks = docker inspect `
        -f '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' `
        $MinioContainer 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($networks | Out-String))) {
        throw "Could not find Docker network '$ComposeNetwork' or inspect MinIO container '$MinioContainer'. Is docker compose running?"
    }

    return @($networks | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })[0]
}

function Invoke-PostgresCsv {
    param([string]$Sql)

    $command = "COPY ($Sql) TO STDOUT WITH CSV HEADER"
    $output = docker exec -i $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -c $command
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL query failed."
    }
    if ([string]::IsNullOrWhiteSpace(($output | Out-String))) {
        return @()
    }
    return $output | ConvertFrom-Csv
}

function Test-MinioObject {
    param([string]$ObjectKey)

    if ([string]::IsNullOrWhiteSpace($ObjectKey)) {
        return $false
    }
    $target = "$MinioAlias/$Bucket/$ObjectKey"
    $script = "mc alias set $MinioAlias $MinioEndpoint $MinioAccessKey $MinioSecretKey >/dev/null && mc stat '$target' >/dev/null 2>&1"
    docker run --rm --network $script:ResolvedNetwork --entrypoint /bin/sh minio/mc:RELEASE.2025-04-16T18-13-26Z -c $script | Out-Null
    return $LASTEXITCODE -eq 0
}

$script:ResolvedNetwork = Resolve-ComposeNetwork
Write-Host "Stage16 object audit: PostgreSQL=$PostgresContainer/$PostgresDatabase, bucket=$Bucket, network=$script:ResolvedNetwork" -ForegroundColor Cyan

$missingOriginalRows = Invoke-PostgresCsv @"
select id, deleted_at, storage_provider, bucket, storage_path, original_object_key
from media
where original_object_key is null or trim(original_object_key) = ''
order by imported_at_millis desc
"@

$urlLikeRows = Invoke-PostgresCsv @"
select id, deleted_at, storage_provider, bucket, original_object_key, preview_object_key, cover_object_key
from media
where coalesce(original_object_key, '') like 'http://%'
   or coalesce(original_object_key, '') like 'https://%'
   or coalesce(original_object_key, '') like 's3://%'
   or coalesce(original_object_key, '') like 'oss://%'
   or coalesce(original_object_key, '') like 'file://%'
   or coalesce(preview_object_key, '') like 'http://%'
   or coalesce(preview_object_key, '') like 'https://%'
   or coalesce(preview_object_key, '') like 's3://%'
   or coalesce(preview_object_key, '') like 'oss://%'
   or coalesce(preview_object_key, '') like 'file://%'
   or coalesce(cover_object_key, '') like 'http://%'
   or coalesce(cover_object_key, '') like 'https://%'
   or coalesce(cover_object_key, '') like 's3://%'
   or coalesce(cover_object_key, '') like 'oss://%'
   or coalesce(cover_object_key, '') like 'file://%'
order by imported_at_millis desc
"@

$objectRows = Invoke-PostgresCsv @"
select id, deleted_at, storage_provider, bucket, original_object_key, preview_object_key, cover_object_key
from media
where storage_provider in ('s3', 'minio')
  and bucket = '$Bucket'
order by imported_at_millis desc
"@

$missingObjects = New-Object System.Collections.Generic.List[string]
foreach ($row in $objectRows) {
    foreach ($field in @("original_object_key", "preview_object_key", "cover_object_key")) {
        $key = $row.$field
        if ([string]::IsNullOrWhiteSpace($key)) {
            continue
        }
        if (-not (Test-MinioObject -ObjectKey $key)) {
            $missingObjects.Add("$($row.id),$field,$key") | Out-Null
        }
    }
}

Write-Host ""
Write-Host "Missing original_object_key rows: $(@($missingOriginalRows).Count)" -ForegroundColor Yellow
$missingOriginalRows | Format-Table -AutoSize

Write-Host ""
Write-Host "URL-shaped object key rows: $(@($urlLikeRows).Count)" -ForegroundColor Yellow
$urlLikeRows | Format-Table -AutoSize

Write-Host ""
Write-Host "Missing MinIO objects referenced by media rows: $($missingObjects.Count)" -ForegroundColor Yellow
foreach ($item in $missingObjects) {
    Write-Host " - $item"
}

Write-Host ""
if (@($missingOriginalRows).Count -eq 0 -and @($urlLikeRows).Count -eq 0 -and $missingObjects.Count -eq 0) {
    Write-Host "Stage16 object audit passed." -ForegroundColor Green
    exit 0
}

Write-Host "Stage16 object audit found migration risks. This script made no data changes." -ForegroundColor Red
exit 1
