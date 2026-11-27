<#
.SYNOPSIS
    MoveWeight 1-Click Android TV / Onn 4K Box Installer (Windows)
    Turns any Android TV / Google TV / Onn 4K box into a ready-to-play:
      - Yarr.It TV streaming box (+ TV Bro)
      - Cartridge + Argosy Launcher (RomM & ROMarr with LAN-direct + Remote fallback)
      - RetroArch (auto-detected 32-bit/64-bit) with 4-Player Xbox Controller + Netplay config
      - Debloated TV OS keeping your choice of Top 10 Streaming Apps (Plex, Jellyfin, Netflix, Prime, Paramount+, etc.)
#>
param(
    [string]$DeviceIp = "",
    [string]$RommLocalUrl = "http://192.168.0.94:8080",
    [string]$RommRemoteUrl = "https://romm.moveweight.com",
    [string]$RomarrLocalUrl = "http://192.168.0.182:7878",
    [string]$RomarrRemoteUrl = "https://romarr.moveweight.com",
    [string]$RommToken = "",
    [string[]]$KeepTopApps = @("plex", "jellyfin", "youtube")
)

$ErrorActionPreference = "Continue"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workDir = Join-Path $env:TEMP "MoveWeight-AndroidTV-Installer"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  MoveWeight Android TV 1-Click Installer (Windows)" -ForegroundColor Cyan
Write-Host "  Yarr.It + Cartridge + Argosy + RetroArch 4P + Top 10 Apps" -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan

# 1. Locate or download ADB automatically
function Get-AdbPath {
    $candidates = @(
        "C:\Android\sdk\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        (Join-Path $workDir "platform-tools\adb.exe")
    )
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    Write-Host "Downloading official Android Platform Tools (ADB)..." -ForegroundColor Yellow
    $zip = Join-Path $workDir "platform-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $workDir -Force
    Remove-Item $zip -Force
    return (Join-Path $workDir "platform-tools\adb.exe")
}

$adb = Get-AdbPath
Write-Host "Using ADB: $adb" -ForegroundColor Green

# 2. Pick Android TV IP
if ([string]::IsNullOrWhiteSpace($DeviceIp)) {
    Write-Host "`nTip: On your Android TV / Onn 4K box:" -ForegroundColor Yellow
    Write-Host "  1. Settings -> System -> About -> Android TV OS build (click OK 7 times)" -ForegroundColor Gray
    Write-Host "  2. Settings -> System -> Developer options -> USB debugging = ON" -ForegroundColor Gray
    Write-Host "  3. Settings -> System -> About -> Status -> IP address`n" -ForegroundColor Gray
    $DeviceIp = Read-Host "Enter your Android TV IP address (e.g. 192.168.0.198)"
}
$target = "${DeviceIp}:5555"

Write-Host "`nConnecting to $target (accept 'Always allow' on your TV screen if prompted)..." -ForegroundColor Cyan
& $adb connect $target
Start-Sleep -Seconds 2
$state = (& $adb -s $target get-state 2>&1)
if ($state -notmatch "device") {
    Read-Host "Click 'Always allow' on the TV screen, then press ENTER to continue"
    & $adb connect $target
}

# Detect ABI (32-bit Onn 4K vs 64-bit Shield/Pro)
$abi = (& $adb -s $target shell getprop ro.product.cpu.abilist).Trim()
$is64 = $abi -match "arm64-v8a"
$model = (& $adb -s $target shell getprop ro.product.model).Trim()
Write-Host "Connected: $model | ABI: $abi | 64-bit: $is64" -ForegroundColor Green

# Disable Play Protect ADB prompt so sideloading never stalls
& $adb -s $target shell "settings put global verifier_verify_adb_installs 0; settings put global package_verifier_enable 0"

# 3. Top 10 Streaming Apps selection + Smart Debloat
$top10Map = @{
    "plex"      = "com.plexapp.android"
    "jellyfin"  = "org.jellyfin.androidtv"
    "netflix"   = "com.netflix.ninja"
    "amazon"    = "com.amazon.amazonvideo.livingroom"
    "paramount" = "com.cbs.ott"
    "disney"    = "com.disney.disneyplus"
    "hulu"      = "com.hulu.livingroomplus"
    "youtube"   = "com.google.android.youtube.tv"
    "max"       = "com.wbd.stream"
    "appletv"   = "com.apple.atve.androidtv.appletv"
}

$protectedPkgs = @("org.moveweight.cartridge", "com.moveweight.stream", "com.phlox.tvwebbrowser", "com.retroarch.ra32", "com.retroarch.aarch64")
foreach ($k in $KeepTopApps) {
    $lk = $k.ToLower()
    if ($top10Map.ContainsKey($lk)) { $protectedPkgs += $top10Map[$lk] }
}

$bloatCandidates = @(
    "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
    "com.bamnetworks.mobile.android.gameday.atbat", "com.cbs.ott",
    "com.crunchyroll.crunchyroid", "com.discovery.discoveryplus.mobile",
    "com.disney.disneyplus", "com.espn.score_center", "com.gotv.nflgamecenter.us.lite",
    "com.hulu.livingroomplus", "com.nbaimd.gametime.nba2011", "com.netflix.ninja",
    "com.netflix.tokenmanager", "com.peacocktv.peacockandroid", "com.spotify.tv.android",
    "com.tubitv", "com.wbd.stream", "com.google.android.youtube.tvunplugged",
    "com.google.android.youtube.tvmusic", "com.google.android.play.games",
    "com.google.android.feedback", "com.google.android.marvin.talkback",
    "com.google.android.partnersetup", "com.android.tv.feedbackconsent",
    "com.android.adservices.api", "com.android.ondevicepersonalization.services",
    "com.android.federatedcompute.services", "com.android.dreams.basic",
    "com.android.printspooler", "com.onn.updatenotification", "com.google.android.tvrecommendations"
)

Write-Host "`n=== Step 1: Debloating (keeping: $($KeepTopApps -join ', ')) ===" -ForegroundColor Cyan
$installed = & $adb -s $target shell "pm list packages" | ForEach-Object { $_.Trim() -replace '^package:', '' }
foreach ($pkg in $bloatCandidates) {
    if ($protectedPkgs -contains $pkg) { continue }
    if ($installed -contains $pkg) {
        & $adb -s $target shell "pm uninstall --user 0 $pkg" | Out-Null
        Write-Host "  [REMOVED] $pkg" -ForegroundColor DarkGray
    }
}
& $adb -s $target shell "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"

# 4. Download & Sideload APKs
function Download-And-Install($name, $url, $fileName) {
    $path = Join-Path $workDir $fileName
    if (!(Test-Path $path)) {
        Write-Host "  Downloading $name..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $url -OutFile $path
    }
    Write-Host "  Installing $name..." -ForegroundColor Green
    & $adb -s $target push $path "/data/local/tmp/$fileName" | Out-Null
    & $adb -s $target shell "pm install -r --bypass-low-target-sdk-block /data/local/tmp/$fileName && rm -f /data/local/tmp/$fileName"
}

Write-Host "`n=== Step 2: Installing Yarr.It TV, TV Bro, Jellyfin, Argosy, Cartridge & RetroArch ===" -ForegroundColor Cyan
Download-And-Install "Yarr.It TV" "https://github.com/BlizzHacker/yarr-it/releases/download/v1.0.1/Yarr.It-tv-1.0.1.apk" "yarrit-tv.apk"

$tvbroRel = Invoke-RestMethod -Uri "https://api.github.com/repos/truefedex/tv-bro/releases/latest"
$tvbroUrl = ($tvbroRel.assets | Where-Object { $_.name -match "\.apk$" } | Select-Object -First 1).browser_download_url
if ($tvbroUrl) { Download-And-Install "TV Bro Browser" $tvbroUrl "tvbro.apk" }

if ($KeepTopApps -contains "jellyfin") {
    $jfRel = Invoke-RestMethod -Uri "https://api.github.com/repos/jellyfin/jellyfin-androidtv/releases/latest"
    $jfUrl = ($jfRel.assets | Where-Object { $_.name -match "release\.apk$" } | Select-Object -First 1).browser_download_url
    if ($jfUrl) { Download-And-Install "Jellyfin Android TV" $jfUrl "jellyfin-tv.apk" }
}

$argosyRel = Invoke-RestMethod -Uri "https://api.github.com/repos/rommapp/argosy-launcher/releases/latest"
$argosyPattern = if ($is64) { "arm64\.apk$" } else { "arm32\.apk$" }
$argosyUrl = ($argosyRel.assets | Where-Object { $_.name -match $argosyPattern } | Select-Object -First 1).browser_download_url
if ($argosyUrl) { Download-And-Install "Argosy Launcher" $argosyUrl "argosy.apk" }

$localCartridgeApk = Join-Path $scriptDir "..\android\app\build\outputs\apk\release\app-release.apk"
if (Test-Path $localCartridgeApk) {
    Write-Host "  Installing local Cartridge APK..." -ForegroundColor Green
    & $adb -s $target install -r $localCartridgeApk
} else {
    $cartRel = Invoke-RestMethod -Uri "https://api.github.com/repos/BlizzHacker/Cartridge/releases/latest"
    $cartUrl = ($cartRel.assets | Where-Object { $_.name -match "\.apk$" } | Select-Object -First 1).browser_download_url
    if ($cartUrl) { Download-And-Install "Cartridge for Android" $cartUrl "cartridge.apk" }
}

# RetroArch (32-bit vs 64-bit)
$raPkg = if ($is64) { "com.retroarch.aarch64" } else { "com.retroarch.ra32" }
$raApkName = if ($is64) { "RetroArch_aarch64.apk" } else { "RetroArch_ra32.apk" }
$raHtml = (Invoke-WebRequest -Uri "https://buildbot.libretro.com/stable/" -UseBasicParsing).Content
$raVer = ([regex]::Matches($raHtml, 'href="/stable/([0-9]+\.[0-9]+\.[0-9]+)/"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object { [version]$_ } -Descending | Select-Object -First 1)
Download-And-Install "RetroArch $raVer ($raApkName)" "https://buildbot.libretro.com/stable/$raVer/android/$raApkName" $raApkName

# 5. Configure 4-Player Xbox Controller + Netplay RetroArch Profile
Write-Host "`n=== Step 3: Deploying 4-Player Xbox + Netplay Config ===" -ForegroundColor Cyan
& $adb -s $target shell "appops set $raPkg MANAGE_EXTERNAL_STORAGE allow; mkdir -p /sdcard/Android/data/$raPkg/files /sdcard/RetroArch/downloads /sdcard/Download/Cartridge"
$cfgFile = Join-Path $scriptDir "retroarch_onn4k_4p_xbox.cfg"
if (Test-Path $cfgFile) {
    & $adb -s $target push $cfgFile "/sdcard/Android/data/$raPkg/files/retroarch.cfg"
}

# 6. Provision Cartridge with LAN Direct + Remote Fallback
Write-Host "`n=== Step 4: Provisioning Cartridge (LAN Direct + Remote Fallback) ===" -ForegroundColor Cyan
$profiles = @(
    @{ id = "home_romm"; name = "Home RomM"; localUrl = $RommLocalUrl; remoteUrl = $RommRemoteUrl; token = $RommToken; authMode = (if ($RommToken) { "token" } else { "password" }) },
    @{ id = "home_romarr"; name = "Home ROMarr"; localUrl = $RomarrLocalUrl; remoteUrl = $RomarrRemoteUrl; token = ""; authMode = "open" }
) | ConvertTo-Json -Compress

@"
am force-stop org.moveweight.cartridge
am start -n org.moveweight.cartridge/.MainActivity \
  --es server_name "Home RomM" \
  --es local_url "$RommLocalUrl" \
  --es remote_url "$RommRemoteUrl" \
  --es auth_token "$RommToken" \
  --es profiles_json '$profiles'
"@ | & $adb -s $target shell sh

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "  ALL DONE! Your Android TV box ($DeviceIp) is ready to play!" -ForegroundColor Green
Write-Host "  Xbox Controller: L3+R3 quits game | Start+Select = Menu" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
