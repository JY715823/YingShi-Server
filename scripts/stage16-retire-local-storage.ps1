[CmdletBinding()]
param(
    [string]$LocalStorageRoot = "local-storage",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

function Format-ByteSize {
    param([long]$Bytes)

    if ($Bytes -ge 1GB) {
        return "{0:N2} GB" -f ($Bytes / 1GB)
    }
    if ($Bytes -ge 1MB) {
        return "{0:N2} MB" -f ($Bytes / 1MB)
    }
    if ($Bytes -ge 1KB) {
        return "{0:N2} KB" -f ($Bytes / 1KB)
    }
    return "$Bytes B"
}

function Get-DirectoryStats {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{
            Files = 0
            Directories = 0
            Bytes = 0L
        }
    }

    $files = @(Get-ChildItem -LiteralPath $Path -Force -Recurse -File)
    $directories = @(Get-ChildItem -LiteralPath $Path -Force -Recurse -Directory)
    $bytes = ($files | Measure-Object -Property Length -Sum).Sum
    if ($null -eq $bytes) {
        $bytes = 0L
    }

    return [pscustomobject]@{
        Files = $files.Count
        Directories = $directories.Count
        Bytes = [long]$bytes
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRootFull = [System.IO.Path]::GetFullPath($repoRoot).TrimEnd('\', '/')
$rootFull = [System.IO.Path]::GetFullPath((Join-Path $repoRootFull $LocalStorageRoot)).TrimEnd('\', '/')
$comparison = [System.StringComparison]::OrdinalIgnoreCase

if (-not $rootFull.StartsWith($repoRootFull + [System.IO.Path]::DirectorySeparatorChar, $comparison)) {
    throw "Refusing to inspect a path outside the Server repository: $rootFull"
}

if ([System.IO.Path]::GetFileName($rootFull) -ne "local-storage") {
    throw "Refusing to clean a directory not named local-storage: $rootFull"
}

$knownTopLevelNames = @(
    "dev-db",
    "originals",
    "previews",
    "test",
    "tmp",
    "videos"
)

$targets = New-Object System.Collections.Generic.List[object]
foreach ($name in $knownTopLevelNames) {
    $targetPath = Join-Path $rootFull $name
    if (-not (Test-Path -LiteralPath $targetPath)) {
        continue
    }
    $targetFull = [System.IO.Path]::GetFullPath($targetPath).TrimEnd('\', '/')
    if (-not $targetFull.StartsWith($rootFull + [System.IO.Path]::DirectorySeparatorChar, $comparison)) {
        throw "Refusing to clean a path outside local-storage: $targetFull"
    }
    $stats = Get-DirectoryStats -Path $targetFull
    $targets.Add([pscustomobject]@{
        Name = $name
        Path = $targetFull
        Files = $stats.Files
        Directories = $stats.Directories
        Bytes = $stats.Bytes
        Size = Format-ByteSize -Bytes $stats.Bytes
    }) | Out-Null
}

Write-Host "Stage16 local-storage retirement" -ForegroundColor Cyan
Write-Host "Repository: $repoRootFull"
Write-Host "Target root: $rootFull"
Write-Host ""

if ($targets.Count -eq 0) {
    Write-Host "No known legacy local-storage directories found." -ForegroundColor Green
    exit 0
}

$targets | Format-Table Name, Files, Directories, Size, Path -AutoSize

$totalFiles = ($targets | Measure-Object -Property Files -Sum).Sum
$totalDirectories = ($targets | Measure-Object -Property Directories -Sum).Sum
$totalBytes = ($targets | Measure-Object -Property Bytes -Sum).Sum
if ($null -eq $totalFiles) { $totalFiles = 0 }
if ($null -eq $totalDirectories) { $totalDirectories = 0 }
if ($null -eq $totalBytes) { $totalBytes = 0L }

Write-Host ""
Write-Host ("Total: {0} files, {1} directories, {2}" -f $totalFiles, $totalDirectories, (Format-ByteSize -Bytes $totalBytes))

if (-not $Apply) {
    Write-Host ""
    Write-Host "Dry run only. Re-run with -Apply to delete these legacy local-storage directories." -ForegroundColor Yellow
    Write-Host "This does not touch PostgreSQL, MinIO, Docker volumes, .env, or backups."
    exit 0
}

Write-Host ""
Write-Host "Deleting known legacy local-storage directories..." -ForegroundColor Yellow
foreach ($target in $targets) {
    Remove-Item -LiteralPath $target.Path -Recurse -Force
    Write-Host "Deleted $($target.Name): $($target.Path)"
}

if (-not (Test-Path -LiteralPath $rootFull)) {
    New-Item -ItemType Directory -Path $rootFull | Out-Null
}

Write-Host ""
Write-Host "Legacy local-storage cleanup completed. PostgreSQL + MinIO data was not touched." -ForegroundColor Green
