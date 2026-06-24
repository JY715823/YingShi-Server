param(
    [switch]$InstallMissingTools
)

$ErrorActionPreference = "Stop"

function Test-Command($Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Write-Check($Name, $Ok, $Hint = "") {
    if ($Ok) {
        Write-Host "[OK] $Name" -ForegroundColor Green
    } else {
        Write-Host "[MISSING] $Name" -ForegroundColor Yellow
        if ($Hint) {
            Write-Host "  $Hint" -ForegroundColor DarkYellow
        }
    }
}

$hasWinget = Test-Command "winget"
$hasJava = Test-Command "java"
$hasDocker = Test-Command "docker"
$adb = Get-Command "adb" -ErrorAction SilentlyContinue
if (-not $adb) {
    $knownAdb = "E:\Soft\Android Studio SDK\platform-tools\adb.exe"
    if (Test-Path $knownAdb) {
        $adb = Get-Item $knownAdb
    }
}

Write-Host "=== YingShi verification tool check ===" -ForegroundColor Cyan
Write-Check "winget" $hasWinget "Install winget from Microsoft App Installer if needed."
Write-Check "Java/JDK" $hasJava "Use: winget install EclipseAdoptium.Temurin.17.JDK"
Write-Check "Docker CLI/Desktop" $hasDocker "Use: winget install Docker.DockerDesktop"
Write-Check "ADB" ($null -ne $adb) "Install Android Studio platform-tools or add adb.exe to PATH."

if ($InstallMissingTools) {
    if (-not $hasWinget) {
        throw "winget is missing, so this script cannot install tools automatically."
    }
    if (-not $hasJava) {
        winget install --id EclipseAdoptium.Temurin.17.JDK --accept-package-agreements --accept-source-agreements
    }
    if (-not $hasDocker) {
        winget install --id Docker.DockerDesktop --accept-package-agreements --accept-source-agreements
        Write-Host "Docker Desktop was requested. Open Docker Desktop once and wait until it says it is running." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "If Docker Desktop is installed but inaccessible, open Docker Desktop -> Settings -> Resources -> WSL integration, then enable the current WSL distro." -ForegroundColor Cyan
