<#
.SYNOPSIS
    Debloat an Onn 4K / Google TV streaming box for fast retro gaming & media streaming.
    Keeps Plex, Jellyfin, Bluetooth (Xbox controllers), Play Store, and core Android TV services.
.EXAMPLE
    .\debloat-onn-tv.ps1 -DeviceIp 192.168.0.198
#>
param(
    [Parameter(Mandatory=$true)]
    [string]$DeviceIp,
    [int]$Port = 5555,
    [string]$AdbPath = "C:\Android\sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Continue"
$target = "${DeviceIp}:${Port}"

Write-Host "Connecting to $target..." -ForegroundColor Cyan
& $AdbPath connect $target

# Protected packages that must NEVER be removed
$keep = @(
    "com.plexapp.android",
    "org.jellyfin.androidtv",
    "org.moveweight.cartridge",
    "com.retroarch.ra32",
    "com.retroarch.aarch64",
    "com.retroarch"
)

# Streaming bloatware, telemetry, ad services, and unused OEM packages (reversible via 'cmd package install-existing')
$bloat = @(
    "com.amazon.amazonvideo.livingroom",
    "com.apple.atve.androidtv.appletv",
    "com.bamnetworks.mobile.android.gameday.atbat",
    "com.cbs.ott",
    "com.crunchyroll.crunchyroid",
    "com.discovery.discoveryplus.mobile",
    "com.disney.disneyplus",
    "com.espn.score_center",
    "com.gotv.nflgamecenter.us.lite",
    "com.hulu.livingroomplus",
    "com.nbaimd.gametime.nba2011",
    "com.netflix.ninja",
    "com.netflix.tokenmanager",
    "com.peacocktv.peacockandroid",
    "com.spotify.tv.android",
    "com.tubitv",
    "com.wbd.stream",
    "com.google.android.youtube.tvunplugged",
    "com.google.android.youtube.tvmusic",
    "com.google.android.play.games",
    "com.google.android.feedback",
    "com.google.android.marvin.talkback",
    "com.google.android.partnersetup",
    "com.google.android.videos",
    "com.android.tv.feedbackconsent",
    "com.android.adservices.api",
    "com.android.ondevicepersonalization.services",
    "com.android.federatedcompute.services",
    "com.android.dreams.basic",
    "com.android.printspooler",
    "com.onn.updatenotification",
    "com.google.android.tvrecommendations"
)

$installed = & $AdbPath -s $target shell "pm list packages" | ForEach-Object { $_.Trim() -replace '^package:', '' }

$removed = 0
foreach ($pkg in $bloat) {
    if ($keep -contains $pkg) { continue }
    if ($installed -contains $pkg) {
        $res = & $AdbPath -s $target shell "pm uninstall --user 0 $pkg" 2>&1
        if ($res -match "Success") {
            Write-Host "  [REMOVED] $pkg" -ForegroundColor Green
            $removed++
        } else {
            # Fallback: disable for user 0 if system protected
            $res2 = & $AdbPath -s $target shell "pm disable-user --user 0 $pkg" 2>&1
            Write-Host "  [DISABLED] $pkg ($res2)" -ForegroundColor Yellow
            $removed++
        }
    }
}

Write-Host "`nApplying snappier TV UI animation scales..." -ForegroundColor Cyan
& $AdbPath -s $target shell "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"

Write-Host "`nDebloat complete! Removed/disabled $removed bloat packages." -ForegroundColor Green
Write-Host "Remaining free storage:" -ForegroundColor Cyan
& $AdbPath -s $target shell "df -h /data"
