param(
    [switch]$SkipBuild,
    [switch]$TailLogs
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-Step($Title, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
    & $Action
}

function Require-Command($Name, $InstallHint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is missing. $InstallHint"
    }
}

Require-Command "docker" "Install Docker Desktop and enable WSL integration."
Require-Command "java" "Install JDK 17, for example: winget install EclipseAdoptium.Temurin.17.JDK"

Invoke-Step "FCM and local fallback env" {
    Select-String -Path ".env" -Pattern "FCM_ENABLED|FCM_DRY_RUN|FCM_PROJECT_ID|FCM_SERVICE_ACCOUNT|PUSH_SELF_FALLBACK_ENABLED" |
        ForEach-Object { $_.Line }
    $secretLine = Select-String -Path ".env" -Pattern "^FCM_SERVICE_ACCOUNT_HOST_PATH=(.+)$" | Select-Object -First 1
    if ($secretLine -and $secretLine.Matches[0].Groups[1].Value) {
        $secretPath = $secretLine.Matches[0].Groups[1].Value.Trim()
        if (Test-Path $secretPath) {
            Write-Host "[OK] FCM service account file exists: $secretPath" -ForegroundColor Green
        } else {
            Write-Host "[MISSING] FCM service account file is not readable: $secretPath" -ForegroundColor Red
        }
    }
}

if (-not $SkipBuild) {
    Invoke-Step "Maven compile/package" {
        .\mvnw.cmd -q -DskipTests package
    }
}

Invoke-Step "Docker compose rebuild/restart" {
    docker compose -f docker-compose.yml up -d --build
}

Invoke-Step "Container status" {
    docker ps --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"
}

Invoke-Step "Health check" {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/health" -TimeoutSec 10
    $health | ConvertTo-Json -Depth 8
}

Invoke-Step "Push migration/table check" {
    docker exec yingshi-postgres psql -U yingshi -d yingshi -c "select version, success from flyway_schema_history where version in ('17','19') order by installed_rank;"
    docker exec yingshi-postgres psql -U yingshi -d yingshi -c "select count(*) as push_delivery_audit_rows from push_delivery_audits;"
}

Invoke-Step "Device token snapshot" {
    docker exec yingshi-postgres psql -U yingshi -d yingshi -c "select user_id, platform, enabled, left(token, 12) as token_prefix, to_timestamp(last_seen_at_millis / 1000.0) as last_seen from push_device_tokens order by updated_at desc limit 20;"
}

Invoke-Step "Recent push delivery audit" {
    docker exec yingshi-postgres psql -U yingshi -d yingshi -c "select created_at, module, category, status, reason, target_route, enabled_device_count, partner_device_count, target_device_count, attempted_count, successful_count, invalid_token_count, used_self_fallback from push_delivery_audits order by created_at desc limit 20;"
}

Invoke-Step "Recent push logs" {
    docker logs --tail 120 yingshi-server | Select-String -Pattern "push|FCM|Firebase|No target|self push|Sent photo|life_console.changed|Async push" -CaseSensitive:$false
}

if ($TailLogs) {
    Write-Host ""
    Write-Host "Tailing push logs. Press Ctrl+C to stop." -ForegroundColor Yellow
    docker logs -f yingshi-server | Select-String -Pattern "push|FCM|Firebase|No target|self push|Sent photo|life_console.changed|Async push" -CaseSensitive:$false
}
