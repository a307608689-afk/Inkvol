# Inkvol signed release build (APK + AAB).
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -SkipBundle
# Requires: keystore.properties + keystore\inkvol-release.jks (both gitignored).
# NOTE: keep this file ASCII-only (PowerShell 5.1 reads scripts in ANSI).

param(
    [switch]$SkipBundle
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$jdk = 'D:\Android\jdk17_tmp\jdk-17.0.20+8'
$sdk = 'D:\Android\sdk'
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
if (Test-Path $sdk) {
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
}
if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH }
if ($env:ANDROID_HOME) { $env:PATH = (Join-Path $env:ANDROID_HOME 'platform-tools') + ';' + $env:PATH }

$propsFile = Join-Path $root 'keystore.properties'
$jksFile = Join-Path $root 'keystore\inkvol-release.jks'
if (-not (Test-Path $propsFile)) {
    throw 'keystore.properties not found. Release must be signed with the private key.'
}
if (-not (Test-Path $jksFile)) {
    throw 'keystore\inkvol-release.jks not found. Restore it from your offline backup.'
}

$gradleArgs = @('assembleRelease')
if (-not $SkipBundle) { $gradleArgs += 'bundleRelease' }
Write-Host ('[build] gradlew ' + ($gradleArgs -join ' ')) -ForegroundColor Cyan
& (Join-Path $root 'gradlew.bat') @gradleArgs --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw ('Gradle failed with exit code ' + $LASTEXITCODE)
}

$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
$aab = Join-Path $root 'app\build\outputs\bundle\release\app-release.aab'
$mapping = Join-Path $root 'app\build\outputs\mapping\release\mapping.txt'

if (-not (Test-Path $apk)) {
    Write-Host '[build] release directory contents:' -ForegroundColor Yellow
    Get-ChildItem (Join-Path $root 'app\build\outputs\apk\release') -ErrorAction SilentlyContinue |
        ForEach-Object { Write-Host ('  ' + $_.Name) }
    throw 'Signed app-release.apk was not produced. Check signingConfig / keystore.properties.'
}

# Verify signature with the newest build-tools available.
$apksigner = $null
$btRoot = Join-Path $env:ANDROID_HOME 'build-tools'
if (Test-Path $btRoot) {
    $apksigner = Get-ChildItem $btRoot -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}
if ($apksigner) {
    Write-Host '[build] apksigner verify:' -ForegroundColor Cyan
    & $apksigner verify --print-certs $apk | Select-String -Pattern 'Verified|SHA-256 digest'
} else {
    Write-Host '[build] WARN: apksigner not found, skip signature verification.' -ForegroundColor Yellow
}

if (Test-Path $mapping) {
    $distDir = Join-Path $root ('dist\' + (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^INKVOL_VERSION_NAME=(.+)$').Matches.Groups[1].Value)
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    Copy-Item $mapping (Join-Path $distDir 'mapping.txt') -Force
    Write-Host ('[build] mapping archived to ' + $distDir) -ForegroundColor Cyan
}

foreach ($f in @($apk, $aab, $mapping)) {
    if (Test-Path $f) {
        $h = (Get-FileHash $f -Algorithm SHA256).Hash
        Write-Host ''
        Write-Host $f -ForegroundColor Green
        Write-Host ('  size   : ' + (Get-Item $f).Length + ' bytes')
        Write-Host ('  sha256 : ' + $h)
    }
}
