# Pre-release safety checks for Inkvol.
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\check-release-safety.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\check-release-safety.ps1 -SkipRemote
# Exit code 1 when a blocking issue is found.
# Keep this file ASCII-only (PowerShell 5.1 reads scripts in ANSI).

param(
    [switch]$SkipRemote,
    [switch]$AllowDirty
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# apksigner needs a working JDK; fall back to the local install used for builds.
$jdkCandidates = @(@($env:JAVA_HOME, 'D:\Android\jdk17_tmp\jdk-17.0.20+8') |
    Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) })
if ($jdkCandidates.Count -gt 0) {
    $env:JAVA_HOME = $jdkCandidates[0]
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
}

$script:failures = @()
$script:warnings = @()

function Pass($msg) { Write-Host ('[ ok ] ' + $msg) -ForegroundColor Green }
function Warn($msg) {
    Write-Host ('[warn] ' + $msg) -ForegroundColor Yellow
    $script:warnings += $msg
}
function Fail($msg) {
    Write-Host ('[FAIL] ' + $msg) -ForegroundColor Red
    $script:failures += $msg
}

Write-Host ''
Write-Host '=== Inkvol release safety checks ===' -ForegroundColor Cyan

# ---------------------------------------------------------------- 1. git state
Write-Host ''
Write-Host '-- 1. repository state' -ForegroundColor Cyan
$porcelain = git status --porcelain
if ([string]::IsNullOrWhiteSpace($porcelain)) {
    Pass 'working tree is clean'
} elseif ($AllowDirty) {
    Warn 'working tree is dirty (-AllowDirty given)'
} else {
    Warn 'working tree has uncommitted changes; commit or stash before tagging'
}

# ------------------------------------------------- 2. tracked sensitive files
Write-Host ''
Write-Host '-- 2. tracked sensitive files' -ForegroundColor Cyan
$tracked = git ls-files
$badPath = $tracked | Select-String -Pattern '^(keystore|dist|logs|backups?|test_samples|tools|screenshots)/|\.(jks|keystore|p12|pem|key|db|hprof)$|(^|/)local\.properties$'
if ($badPath) {
    $badPath | ForEach-Object { Fail ('tracked sensitive path: ' + $_.Line) }
} else {
    Pass 'no keystore / build output / book data tracked'
}

# ------------------------------------------------------- 3. literal secrets
Write-Host ''
Write-Host '-- 3. literal secrets in history' -ForegroundColor Cyan
$secretHits = @()
$secretHits += git grep -n -I -E 'storePassword[[:space:]]*=[[:space:]]*["''][^"'']+["'']' -- . 2>$null
$secretHits += git grep -n -I -E 'keyPassword[[:space:]]*=[[:space:]]*["''][^"'']+["'']' -- . 2>$null
$secretHits += git grep -n -I -E 'BEGIN (RSA |EC )?PRIVATE KEY' -- . 2>$null
$secretHits += git grep -n -I -E 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}' -- . 2>$null
$secretHits = $secretHits | Where-Object { $_ -and $_.Trim() -ne '' }
if ($secretHits) {
    $secretHits | ForEach-Object { Fail ('possible secret: ' + $_) }
} else {
    Pass 'no literal passwords / private keys / tokens in tracked files'
}

# ---------------------------------------------------------- 4. signing material
Write-Host ''
Write-Host '-- 4. signing material' -ForegroundColor Cyan
if (Test-Path 'keystore\inkvol-release.jks') { Pass 'keystore file present locally' } else { Fail 'keystore\inkvol-release.jks missing' }
if (Test-Path 'keystore.properties') { Pass 'keystore.properties present locally' } else { Fail 'keystore.properties missing' }
if ($tracked | Select-String -Pattern '^keystore\.properties$') { Fail 'keystore.properties is tracked by git' }

# ------------------------------------------------------------- 5. .gitignore
Write-Host ''
Write-Host '-- 5. gitignore coverage' -ForegroundColor Cyan
$mustIgnore = @(
    'keystore/inkvol-release.jks',
    'keystore.properties',
    'local.properties',
    'dist/release/x.apk',
    'logs/x.png',
    'test_samples/x.epub',
    'backups/x.json',
    'build_log_sections.txt',
    'crash-20260101-000000.txt'
)
foreach ($p in $mustIgnore) {
    git check-ignore -q -- $p
    if ($LASTEXITCODE -eq 0) { Pass ('ignored: ' + $p) } else { Fail ('NOT ignored: ' + $p) }
}

# --------------------------------------------------------------- 6. versions
Write-Host ''
Write-Host '-- 6. version and tag' -ForegroundColor Cyan
$props = Get-Content 'gradle.properties'
$codeLine = $props | Select-String -Pattern '^INKVOL_VERSION_CODE=(.+)$'
$nameLine = $props | Select-String -Pattern '^INKVOL_VERSION_NAME=(.+)$'
if (-not $codeLine -or -not $nameLine) {
    Fail 'INKVOL_VERSION_CODE / INKVOL_VERSION_NAME not found in gradle.properties'
} else {
    $code = [int]$codeLine.Matches.Groups[1].Value.Trim()
    $name = $nameLine.Matches.Groups[1].Value.Trim()
    Pass ('version = ' + $name + ' (' + $code + ')')

    $expectedCode = $null
    if ($name -match '^(\d+)\.(\d+)\.(\d+)$') {
        $expectedCode = [int]$Matches[1] * 10000 + [int]$Matches[2] * 100 + [int]$Matches[3]
        if ($expectedCode -eq $code) { Pass 'versionCode matches MAJOR*10000+MINOR*100+PATCH' }
        else { Fail ('versionCode ' + $code + ' != formula ' + $expectedCode) }
    } else {
        Fail ('versionName is not semver: ' + $name)
    }

    $tag = 'v' + $name
    git rev-parse -q --verify ('refs/tags/' + $tag) | Out-Null
    if ($LASTEXITCODE -eq 0) { Fail ('tag already exists: ' + $tag) } else { Pass ('tag is new: ' + $tag) }

    $prevTag = git tag --list 'v*' --sort=-v:refname | Select-Object -First 1
    if ($prevTag) {
        $prevProps = git show ($prevTag + ':gradle.properties') 2>$null
        $prevCodeLine = $prevProps | Select-String -Pattern '^INKVOL_VERSION_CODE=(.+)$'
        if ($prevCodeLine) {
            $prevCode = [int]$prevCodeLine.Matches.Groups[1].Value.Trim()
            if ($code -gt $prevCode) { Pass ('versionCode greater than ' + $prevTag + ' (' + $prevCode + ')') }
            else { Fail ('versionCode ' + $code + ' is not greater than ' + $prevTag + ' (' + $prevCode + ')') }
        }
    }
}

# ------------------------------------------------------------- 7. artifacts
Write-Host ''
Write-Host '-- 7. release artifacts' -ForegroundColor Cyan
$apk = 'app\build\outputs\apk\release\app-release.apk'
$aab = 'app\build\outputs\bundle\release\app-release.aab'
$mapping = 'app\build\outputs\mapping\release\mapping.txt'
if (-not (Test-Path $apk)) { Fail 'release APK not built' }
if (-not (Test-Path $aab)) { Warn 'release AAB not built (Play upload needs it)' }
if (-not (Test-Path $mapping)) { Fail 'mapping.txt missing (crash deobfuscation impossible)' }

if (Test-Path $apk) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { 'D:\Android\sdk' }
    $btRoot = Join-Path $sdk 'build-tools'
    $bt = $null
    if (Test-Path $btRoot) {
        $bt = Get-ChildItem $btRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
    }
    if (-not $bt) {
        Warn 'build-tools not found, skip APK inspection'
    } else {
        $aapt = Join-Path $bt.FullName 'aapt2.exe'
        $signer = Join-Path $bt.FullName 'apksigner.bat'
        $badging = & $aapt dump badging $apk 2>$null
        if ($badging -match 'application-debuggable') { Fail 'APK is debuggable' } else { Pass 'APK is not debuggable' }
        $pkgLine = $badging | Select-String -Pattern '^package:'
        if ($pkgLine) {
            Pass ('APK ' + $pkgLine.Line)
            if ($pkgLine.Line -notmatch ('versionCode=''' + $code + '''')) { Fail 'APK versionCode does not match gradle.properties' }
            if ($pkgLine.Line -notmatch ('versionName=''' + [regex]::Escape($name) + '''')) { Fail 'APK versionName does not match gradle.properties' }
        }
        if (Test-Path $signer) {
            $verify = & $signer verify --print-certs $apk 2>&1
            $verifyExit = $LASTEXITCODE
            if ($verifyExit -eq 0) {
                Pass 'APK signature verifies'
            } else {
                Fail 'APK signature verification failed'
                $verify | Select-Object -Last 5 | ForEach-Object { Write-Host ('       ' + $_) }
            }
            $verify | Select-String -Pattern 'SHA-256 digest' | ForEach-Object { Write-Host ('       ' + $_.Line.Trim()) }
        } else {
            Warn 'apksigner not found, skip signature verify'
        }
        $perms = $badging | Select-String -Pattern '^uses-permission:'
        $permFile = 'dist\release\v' + $name + '\permissions.txt'
        New-Item -ItemType Directory -Force -Path (Split-Path $permFile) | Out-Null
        $perms | ForEach-Object { $_.Line } | Set-Content $permFile -Encoding ASCII
        Write-Host ('       permissions written to ' + $permFile)
        $prevPerm = 'dist\release\' + $prevTag + '\permissions.txt'
        if ($prevTag -and (Test-Path $prevPerm)) {
            $diff = Compare-Object (Get-Content $prevPerm) (Get-Content $permFile)
            if ($diff) { $diff | ForEach-Object { Warn ('permission change: ' + $_.SideIndicator + ' ' + $_.InputObject) } }
            else { Pass ('permission set unchanged vs ' + $prevTag) }
        }
    }
}

if ($prevTag) {
    $prevMapping = 'dist\release\' + $prevTag + '\mapping-' + $prevTag.TrimStart('v') + '.txt'
    $prevMappingAlt = 'dist\release\' + $prevTag + '\mapping.txt'
    if ((Test-Path $prevMapping) -or (Test-Path $prevMappingAlt)) {
        Pass ('previous mapping archived: ' + $prevTag)
    } else {
        Warn ('mapping for ' + $prevTag + ' not archived')
    }
}
$newMapping = 'dist\release\v' + $name + '\mapping-' + $name + '.txt'
if (Test-Path $mapping) {
    New-Item -ItemType Directory -Force -Path (Split-Path $newMapping) | Out-Null
    Copy-Item $mapping $newMapping -Force
    Pass ('private mapping archive: ' + $newMapping)
}

# --------------------------------------------------------- 8. remote / release
Write-Host ''
Write-Host '-- 8. remote release' -ForegroundColor Cyan
if ($SkipRemote) {
    Warn 'remote checks skipped (-SkipRemote)'
} else {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh) {
        Warn 'gh CLI not found, skip remote checks'
    } else {
        $remoteTag = 'v' + $name
        gh release view $remoteTag 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { Fail ('release already published: ' + $remoteTag + ' (immutable release policy)') }
        else { Pass ('release not published yet: ' + $remoteTag) }

        $remoteMain = git ls-remote origin refs/heads/main 2>$null
        $localHead = git rev-parse HEAD
        if ($remoteMain -match ([regex]::Escape($localHead))) { Pass 'local HEAD is pushed to origin/main' }
        else { Warn 'local HEAD differs from origin/main (push before tagging)' }
    }
}

# ---------------------------------------------------------------- summary
Write-Host ''
Write-Host '=== summary ===' -ForegroundColor Cyan
Write-Host ('failures: ' + $script:failures.Count + '   warnings: ' + $script:warnings.Count)
if ($script:failures.Count -gt 0) {
    Write-Host 'BLOCKING: fix the [FAIL] items before tagging.' -ForegroundColor Red
    exit 1
}
Write-Host 'All blocking checks passed.' -ForegroundColor Green
exit 0
