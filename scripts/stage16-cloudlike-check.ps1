[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$PostgresContainer = "yingshi-postgres",
    [string]$PostgresUser = "yingshi",
    [string]$PostgresDatabase = "yingshi",
    [switch]$RunSmoke,
    [switch]$RunTests,
    [switch]$SkipObjectAudit,
    [switch]$SkipLocalStorageDryRun
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-Stage16Check {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    try {
        $result = & $Action
        if ([string]::IsNullOrWhiteSpace([string]$result)) {
            $result = "ok"
        }
        Write-Host "[PASS] $Name - $result" -ForegroundColor Green
    }
    catch {
        Write-Host "[FAIL] $Name - $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function Test-DockerContainerRunning {
    param([string]$Name)

    $state = docker inspect -f "{{.State.Status}}" $Name 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "container '$Name' was not found"
    }
    if ($state -ne "running") {
        throw "container '$Name' is $state"
    }
    return $state
}

Write-Host "Stage16 cloudlike check" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl"
Write-Host ""

Invoke-Stage16Check "docker postgres" {
    Test-DockerContainerRunning -Name $PostgresContainer
}

Invoke-Stage16Check "docker minio" {
    Test-DockerContainerRunning -Name "yingshi-minio"
}

Invoke-Stage16Check "server health" {
    $health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -TimeoutSec 10
    if ($health.data.status -ne "UP") {
        throw "expected UP, got $($health.data.status)"
    }
    "status=$($health.data.status), profiles=$($health.data.activeProfiles -join ',')"
}

Invoke-Stage16Check "flyway history" {
    $sql = "select installed_rank, version, description, type, success from flyway_schema_history order by installed_rank;"
    $output = docker exec -i $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -c $sql
    if ($LASTEXITCODE -ne 0) {
        throw "flyway_schema_history is not readable"
    }
    $text = ($output | Out-String)
    if ($text -notmatch "Flyway Baseline" -and $text -notmatch "initial schema" -and $text -notmatch "\|\s*t\s*$") {
        throw "no successful Flyway row found"
    }
    "schema history present"
}

if (-not $SkipObjectAudit) {
    Invoke-Stage16Check "object audit" {
        & "$PSScriptRoot\stage16-object-audit.ps1"
        if ($LASTEXITCODE -ne 0) {
            throw "object audit failed"
        }
        "passed"
    }
}

if (-not $SkipLocalStorageDryRun) {
    Invoke-Stage16Check "local-storage dry-run" {
        & "$PSScriptRoot\stage16-retire-local-storage.ps1"
        if ($LASTEXITCODE -ne 0) {
            throw "local-storage dry-run failed"
        }
        "checked"
    }
}

if ($RunSmoke) {
    Invoke-Stage16Check "cloudlike smoke" {
        & "$PSScriptRoot\stage16-cloudlike-smoke.ps1" -BaseUrl $BaseUrl
        if ($LASTEXITCODE -ne 0) {
            throw "smoke failed"
        }
        "passed"
    }
}
else {
    Write-Host "[SKIP] cloudlike smoke - add -RunSmoke to upload a tiny test image" -ForegroundColor Yellow
}

if ($RunTests) {
    Invoke-Stage16Check "maven tests" {
        Push-Location (Join-Path $PSScriptRoot "..")
        try {
            & .\mvnw.cmd test
            if ($LASTEXITCODE -ne 0) {
                throw "mvnw test failed"
            }
        }
        finally {
            Pop-Location
        }
        "passed"
    }
}
else {
    Write-Host "[SKIP] maven tests - add -RunTests for full Server tests" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Stage16 cloudlike check completed." -ForegroundColor Green
