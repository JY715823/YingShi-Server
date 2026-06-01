[CmdletBinding()]
param(
    [string]$BackupRoot,
    [string]$PostgresContainer = "yingshi-postgres",
    [string]$PostgresUser = "yingshi",
    [string]$PostgresDatabase = "yingshi",
    [string]$MinioContainer = "yingshi-minio",
    [string]$MinioAlias = "local",
    [string]$MinioEndpoint = "http://minio:9000",
    [string]$MinioAccessKey = "yingshi_minio_access",
    [string]$MinioSecretKey = "yingshi_minio_secret",
    [string]$MinioBucket = "yingshi-media",
    [string]$ComposeNetwork = "yingshi-server_default",
    [switch]$IncludePlainSql,
    [switch]$SkipMinio
)

$ErrorActionPreference = "Stop"
$script:OriginalBoundParameters = @{} + $PSBoundParameters
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if (-not $script:OriginalBoundParameters.ContainsKey("BackupRoot")) {
    $BackupRoot = Join-Path $repoRoot "backups"
}

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }
        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }
        $key = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()
        $values[$key] = $value
    }

    return $values
}

function Use-DotEnvValue {
    param(
        [string]$ParameterName,
        [string[]]$EnvNames,
        [string]$CurrentValue
    )

    if ($script:OriginalBoundParameters.ContainsKey($ParameterName)) {
        return $CurrentValue
    }

    foreach ($envName in $EnvNames) {
        if ($script:DotEnv.ContainsKey($envName) -and -not [string]::IsNullOrWhiteSpace($script:DotEnv[$envName])) {
            return $script:DotEnv[$envName]
        }
    }

    return $CurrentValue
}

function Invoke-BackupStep {
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

function Resolve-ComposeNetwork {
    docker network inspect $ComposeNetwork *> $null
    if ($LASTEXITCODE -eq 0) {
        return $ComposeNetwork
    }

    $networks = docker inspect `
        -f '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' `
        $MinioContainer 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($networks | Out-String))) {
        throw "Could not find Docker network '$ComposeNetwork' or inspect MinIO container '$MinioContainer'."
    }

    return @($networks | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })[0]
}

function Remove-ContainerTempFile {
    param(
        [string]$ContainerName,
        [string]$Path
    )

    docker exec $ContainerName /bin/sh -c "rm -f '$Path'" | Out-Null
}

$script:DotEnv = Read-DotEnv (Join-Path $repoRoot ".env")
$PostgresUser = Use-DotEnvValue -ParameterName "PostgresUser" -EnvNames @("POSTGRES_USER", "SPRING_DATASOURCE_USERNAME") -CurrentValue $PostgresUser
$PostgresDatabase = Use-DotEnvValue -ParameterName "PostgresDatabase" -EnvNames @("POSTGRES_DB") -CurrentValue $PostgresDatabase
$MinioEndpoint = Use-DotEnvValue -ParameterName "MinioEndpoint" -EnvNames @("STORAGE_ENDPOINT") -CurrentValue $MinioEndpoint
$MinioAccessKey = Use-DotEnvValue -ParameterName "MinioAccessKey" -EnvNames @("MINIO_ROOT_USER", "STORAGE_ACCESS_KEY") -CurrentValue $MinioAccessKey
$MinioSecretKey = Use-DotEnvValue -ParameterName "MinioSecretKey" -EnvNames @("MINIO_ROOT_PASSWORD", "STORAGE_SECRET_KEY") -CurrentValue $MinioSecretKey
$MinioBucket = Use-DotEnvValue -ParameterName "MinioBucket" -EnvNames @("MINIO_BUCKET", "STORAGE_BUCKET") -CurrentValue $MinioBucket

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupSetDir = Join-Path $BackupRoot ("stage16-cloudlike-" + $timestamp)
$postgresBackupDir = Join-Path $backupSetDir "postgres"
$minioBackupDir = Join-Path $backupSetDir ("minio-" + $MinioBucket)
$manifestPath = Join-Path $backupSetDir "backup-manifest.json"
$customDumpFileName = "$PostgresDatabase-custom.dump"
$customDumpHostPath = Join-Path $postgresBackupDir $customDumpFileName
$customDumpContainerPath = "/tmp/$customDumpFileName"
$plainSqlFileName = "$PostgresDatabase.sql"
$plainSqlHostPath = Join-Path $postgresBackupDir $plainSqlFileName
$plainSqlContainerPath = "/tmp/$plainSqlFileName"

New-Item -ItemType Directory -Force -Path $postgresBackupDir | Out-Null
if (-not $SkipMinio) {
    New-Item -ItemType Directory -Force -Path $minioBackupDir | Out-Null
}

Write-Host "Stage16 cloudlike backup" -ForegroundColor Cyan
Write-Host "Backup set: $backupSetDir"
Write-Host ""

Invoke-BackupStep "docker postgres" {
    Test-DockerContainerRunning -Name $PostgresContainer
}

if (-not $SkipMinio) {
    Invoke-BackupStep "docker minio" {
        Test-DockerContainerRunning -Name $MinioContainer
    }
}
else {
    Write-Host "[SKIP] docker minio - add no switch to include bucket mirror backup" -ForegroundColor Yellow
}

try {
    Invoke-BackupStep "postgres custom dump" {
        Remove-ContainerTempFile -ContainerName $PostgresContainer -Path $customDumpContainerPath
        docker exec $PostgresContainer pg_dump `
            -U $PostgresUser `
            -d $PostgresDatabase `
            --format=custom `
            --file=$customDumpContainerPath
        if ($LASTEXITCODE -ne 0) {
            throw "pg_dump custom backup failed"
        }

        docker cp "${PostgresContainer}:$customDumpContainerPath" $customDumpHostPath
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $customDumpHostPath)) {
            throw "docker cp for custom dump failed"
        }

        "saved $(Split-Path -Leaf $customDumpHostPath)"
    }

    if ($IncludePlainSql) {
        Invoke-BackupStep "postgres plain sql" {
            Remove-ContainerTempFile -ContainerName $PostgresContainer -Path $plainSqlContainerPath
            docker exec $PostgresContainer pg_dump `
                -U $PostgresUser `
                -d $PostgresDatabase `
                --file=$plainSqlContainerPath
            if ($LASTEXITCODE -ne 0) {
                throw "pg_dump plain SQL backup failed"
            }

            docker cp "${PostgresContainer}:$plainSqlContainerPath" $plainSqlHostPath
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $plainSqlHostPath)) {
                throw "docker cp for plain SQL failed"
            }

            "saved $(Split-Path -Leaf $plainSqlHostPath)"
        }
    }
    else {
        Write-Host "[SKIP] postgres plain sql - add -IncludePlainSql for an inspectable SQL export" -ForegroundColor Yellow
    }

    if (-not $SkipMinio) {
        Invoke-BackupStep "minio mirror" {
            $resolvedNetwork = Resolve-ComposeNetwork
            $mirrorScript = "mc alias set $MinioAlias $MinioEndpoint $MinioAccessKey $MinioSecretKey >/dev/null && mc mb --ignore-existing $MinioAlias/$MinioBucket >/dev/null && mc mirror --overwrite $MinioAlias/$MinioBucket /backup"
            docker run --rm `
                --network $resolvedNetwork `
                -v "${minioBackupDir}:/backup" `
                --entrypoint /bin/sh `
                minio/mc:RELEASE.2025-04-16T18-13-26Z `
                -c $mirrorScript
            if ($LASTEXITCODE -ne 0) {
                throw "mc mirror failed"
            }

            "saved $(Split-Path -Leaf $minioBackupDir)"
        }
    }
    else {
        Write-Host "[SKIP] minio mirror - requested by -SkipMinio" -ForegroundColor Yellow
    }
}
finally {
    Remove-ContainerTempFile -ContainerName $PostgresContainer -Path $customDumpContainerPath
    if ($IncludePlainSql) {
        Remove-ContainerTempFile -ContainerName $PostgresContainer -Path $plainSqlContainerPath
    }
}

$manifest = [ordered]@{
    createdAt = (Get-Date).ToString("o")
    backupSetDirectory = $backupSetDir
    postgres = [ordered]@{
        container = $PostgresContainer
        database = $PostgresDatabase
        user = $PostgresUser
        customDump = $customDumpHostPath
        plainSql = if ($IncludePlainSql) { $plainSqlHostPath } else { $null }
    }
    minio = [ordered]@{
        skipped = [bool]$SkipMinio
        container = $MinioContainer
        endpoint = $MinioEndpoint
        bucket = $MinioBucket
        mirrorDirectory = if ($SkipMinio) { $null } else { $minioBackupDir }
    }
}

$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

Write-Host ""
Write-Host "Stage16 cloudlike backup completed." -ForegroundColor Green
Write-Host "Manifest: $manifestPath"
