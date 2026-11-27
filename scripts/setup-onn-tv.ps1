<#
.SYNOPSIS
    All-in-one Onn 4K / Google TV retro gaming + media streaming setup script.
    1. Debloats streaming/ad bloatware (keeps Plex & Jellyfin)
    2. Sideloads Cartridge for Android, RetroArch (32-bit or 64-bit auto-detected), Yarr.It TV, and Jellyfin TV
    3. Deploys 4-player Xbox controller RetroArch config
    4. Pre-provisions Cartridge with Local LAN IP + Remote fallback for RomM & ROMarr
.EXAMPLE
    .\setup-onn-tv.ps1 -DeviceIp 192.168.0.198 -RommToken "rmm_..."
#>
param(
    [Parameter(Mandatory=$true)]
    [string]$DeviceIp,
    [int]$Port = 5555,
    [string]$RommLocalUrl = "http://192.168.0.94:8080",
    [string]$RommRemoteUrl = "https://romm.moveweight.com",
    [string]$RomarrLocalUrl = "http://192.168.0.182:7878",
    [string]$RomarrRemoteUrl = "https://romarr.moveweight.com",
    [string]$RommToken = "",
    [string]$CartridgeApk = "C:\MoveWeight\RommForXbox\android\app\build\outputs\apk\release\app-release.apk",
    [string]$RetroArchApk = "C:\MoveWeight\_tools\apks\RetroArch_ra32.apk",
    [string]$YarritApk = "C:\MoveWeight\_tools\apks\Yarr.It-tv-1.0.1.apk",
    [string]$JellyfinApk = "C:\MoveWeight\_tools\apks\jellyfin-androidtv.apk",
    [string]$AdbPath = "C:\Android\sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Continue"
$target = "${DeviceIp}:${Port}"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "=== 1. Connecting & Debloating $target ===" -ForegroundColor Cyan
& "$scriptDir\debloat-onn-tv.ps1" -DeviceIp $DeviceIp -Port $Port -AdbPath $AdbPath

Write-Host "`n=== 2. Installing Apps ===" -ForegroundColor Cyan
foreach ($apk in @($YarritApk, $JellyfinApk, $CartridgeApk)) {
    if (Test-Path $apk) {
        Write-Host "Installing $(Split-Path -Leaf $apk)..." -ForegroundColor Yellow
        & $AdbPath -s $target install -r $apk
    }
}
if (Test-Path $RetroArchApk) {
    Write-Host "Pushing & installing $(Split-Path -Leaf $RetroArchApk)..." -ForegroundColor Yellow
    & $AdbPath -s $target push $RetroArchApk /data/local/tmp/retroarch.apk
    & $AdbPath -s $target shell "pm install -r /data/local/tmp/retroarch.apk && rm -f /data/local/tmp/retroarch.apk"
}

Write-Host "`n=== 3. Configuring 4-Player Xbox RetroArch Profile ===" -ForegroundColor Cyan
$cfgLocal = Join-Path $scriptDir "retroarch_onn4k_4p_xbox.cfg"
if (Test-Path $cfgLocal) {
    & $AdbPath -s $target shell "mkdir -p /sdcard/Android/data/com.retroarch.ra32/files /sdcard/Download/Cartridge/n64 /sdcard/Download/Cartridge/snes /sdcard/Download/Cartridge/nes /sdcard/Download/Cartridge/gba"
    & $AdbPath -s $target push $cfgLocal /sdcard/Android/data/com.retroarch.ra32/files/retroarch.cfg
}

Write-Host "`n=== 4. Provisioning Cartridge (LAN Direct + Remote Fallback) ===" -ForegroundColor Cyan
$profiles = @(
    @{ id = "home_romm"; name = "Home RomM"; localUrl = $RommLocalUrl; remoteUrl = $RommRemoteUrl; token = $RommToken; authMode = "token" },
    @{ id = "home_romarr"; name = "Home ROMarr"; localUrl = $RomarrLocalUrl; remoteUrl = $RomarrRemoteUrl; token = ""; authMode = "open" }
) | ConvertTo-Json -Compress

& $AdbPath -s $target shell am start -n org.moveweight.cartridge/.MainActivity `
    --es server_name "Home RomM" `
    --es local_url "$RommLocalUrl" `
    --es remote_url "$RommRemoteUrl" `
    --es auth_token "$RommToken" `
    --es profiles_json "'$profiles'"

Write-Host "`n=== Setup Complete on $target! ===" -ForegroundColor Green
