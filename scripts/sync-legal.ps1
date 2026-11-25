# Sync legal documents into app assets.
# Source of truth: docs\privacy.md (published by GitHub Pages from /docs).
# App reads: app\src\main\assets\legal\privacy.md
# GPL full text is copied from the repository LICENSE.
# NOTE: keep this file ASCII-only (PowerShell 5.1 reads scripts in ANSI).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$legal = Join-Path $root 'app\src\main\assets\legal'
New-Item -ItemType Directory -Force -Path $legal | Out-Null

Copy-Item (Join-Path $root 'docs\privacy.md') (Join-Path $legal 'privacy.md') -Force
Copy-Item (Join-Path $root 'LICENSE') (Join-Path $legal 'gpl-3.0.txt') -Force

$policy = Join-Path $legal 'privacy.md'
$hash = (Get-FileHash $policy -Algorithm SHA256).Hash
Write-Host ('[legal] privacy.md synced, sha256=' + $hash) -ForegroundColor Cyan
Write-Host '[legal] remember to bump SettingsKeys.PRIVACY_POLICY_VERSION when the policy changes.'
