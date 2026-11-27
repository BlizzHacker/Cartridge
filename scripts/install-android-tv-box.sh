#!/usr/bin/env bash
# MoveWeight 1-Click Android TV / Onn 4K Box Installer (Linux / macOS)
# Installs Yarr.It TV, TV Bro, Jellyfin TV, Argosy Launcher, Cartridge, and RetroArch (4P Xbox + Netplay)
set -euo pipefail

DEVICE_IP="${1:-}"
ROMM_LOCAL="${ROMM_LOCAL:-http://192.168.0.94:8080}"
ROMM_REMOTE="${ROMM_REMOTE:-https://romm.moveweight.com}"
ROMARR_LOCAL="${ROMARR_LOCAL:-http://192.168.0.182:7878}"
ROMARR_REMOTE="${ROMARR_REMOTE:-https://romarr.moveweight.com}"
ROMM_TOKEN="${ROMM_TOKEN:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKDIR="${TMPDIR:-/tmp}/moveweight-androidtv-installer"
mkdir -p "$WORKDIR"

echo "============================================================"
echo "  MoveWeight Android TV 1-Click Installer (Linux/macOS)"
echo "  Yarr.It + Cartridge + Argosy + RetroArch 4P + Netplay"
echo "============================================================"

if ! command -v adb >/dev/null 2>&1; then
  echo "Installing adb is required (e.g. sudo apt install -y adb)."
  exit 1
fi

if [ -z "$DEVICE_IP" ]; then
  read -rp "Enter your Android TV / Onn 4K IP address (e.g. 192.168.0.198): " DEVICE_IP
fi
TARGET="${DEVICE_IP}:5555"

adb connect "$TARGET" || true
sleep 2
ABI="$(adb -s "$TARGET" shell getprop ro.product.cpu.abilist | tr -d '\r')"
MODEL="$(adb -s "$TARGET" shell getprop ro.product.model | tr -d '\r')"
echo "Connected: $MODEL | ABI: $ABI"

adb -s "$TARGET" shell "settings put global verifier_verify_adb_installs 0; settings put global package_verifier_enable 0"

# Safe debloat (keeps Plex, Jellyfin, YouTube, Bluetooth)
BLOAT=(
  com.amazon.amazonvideo.livingroom com.apple.atve.androidtv.appletv
  com.bamnetworks.mobile.android.gameday.atbat com.cbs.ott
  com.crunchyroll.crunchyroid com.discovery.discoveryplus.mobile
  com.disney.disneyplus com.espn.score_center com.gotv.nflgamecenter.us.lite
  com.hulu.livingroomplus com.nbaimd.gametime.nba2011 com.netflix.ninja
  com.netflix.tokenmanager com.peacocktv.peacockandroid com.spotify.tv.android
  com.tubitv com.wbd.stream com.google.android.youtube.tvunplugged
  com.google.android.youtube.tvmusic com.google.android.play.games
  com.google.android.feedback com.google.android.marvin.talkback
  com.google.android.partnersetup com.android.tv.feedbackconsent
  com.android.adservices.api com.android.ondevicepersonalization.services
  com.android.federatedcompute.services com.android.dreams.basic
  com.android.printspooler com.onn.updatenotification com.google.android.tvrecommendations
)

echo "=== Debloating $MODEL ==="
for pkg in "${BLOAT[@]}"; do
  adb -s "$TARGET" shell "pm uninstall --user 0 $pkg" >/dev/null 2>&1 || true
done
adb -s "$TARGET" shell "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"

install_url() {
  local name="$1" url="$2" fname="$3"
  local dest="$WORKDIR/$fname"
  [ -f "$dest" ] || curl -fL --progress-bar "$url" -o "$dest"
  echo "Installing $name..."
  adb -s "$TARGET" push "$dest" "/data/local/tmp/$fname" >/dev/null
  adb -s "$TARGET" shell "pm install -r --bypass-low-target-sdk-block /data/local/tmp/$fname && rm -f /data/local/tmp/$fname"
}

IS64=0
[[ "$ABI" == *"arm64-v8a"* ]] && IS64=1
RA_PKG="com.retroarch.ra32"
RA_APK="RetroArch_ra32.apk"
ARGOSY_PATTERN="arm32\.apk"
if [ "$IS64" -eq 1 ]; then
  RA_PKG="com.retroarch.aarch64"
  RA_APK="RetroArch_aarch64.apk"
  ARGOSY_PATTERN="arm64\.apk"
fi

install_url "Yarr.It TV" "https://github.com/BlizzHacker/yarr-it/releases/download/v1.0.1/Yarr.It-tv-1.0.1.apk" "yarrit-tv.apk"

TVBRO_URL="$(curl -s https://api.github.com/repos/truefedex/tv-bro/releases/latest | grep -oE 'https://[^"]+\.apk' | head -n1)"
[ -n "$TVBRO_URL" ] && install_url "TV Bro" "$TVBRO_URL" "tvbro.apk"

JF_URL="$(curl -s https://api.github.com/repos/jellyfin/jellyfin-androidtv/releases/latest | grep -oE 'https://[^"]+release\.apk' | head -n1)"
[ -n "$JF_URL" ] && install_url "Jellyfin TV" "$JF_URL" "jellyfin-tv.apk"

ARGOSY_URL="$(curl -s https://api.github.com/repos/rommapp/argosy-launcher/releases/latest | grep -oE "https://[^\"]+${ARGOSY_PATTERN}" | head -n1)"
[ -n "$ARGOSY_URL" ] && install_url "Argosy Launcher" "$ARGOSY_URL" "argosy.apk"

LEM_URL="$(curl -s https://api.github.com/repos/Swordfish90/Lemuroid/releases/latest | grep -oE 'https://[^"]+\.apk' | head -n1)"
[ -n "$LEM_URL" ] && install_url "Lemuroid (Zero-Core Emulator)" "$LEM_URL" "lemuroid.apk"

CART_URL="$(curl -s https://api.github.com/repos/BlizzHacker/Cartridge/releases/latest | grep -oE 'https://[^"]+\.apk' | head -n1)"
[ -n "$CART_URL" ] && install_url "Cartridge" "$CART_URL" "cartridge.apk"

RA_VER="$(curl -s https://buildbot.libretro.com/stable/ | grep -oE '/stable/[0-9]+\.[0-9]+\.[0-9]+/' | sed 's|/stable/||;s|/||' | sort -V | tail -n1)"
install_url "RetroArch $RA_VER" "https://buildbot.libretro.com/stable/${RA_VER}/android/${RA_APK}" "$RA_APK"

adb -s "$TARGET" shell "appops set $RA_PKG MANAGE_EXTERNAL_STORAGE allow; mkdir -p /sdcard/Android/data/$RA_PKG/files /sdcard/RetroArch/downloads /sdcard/Download/Cartridge"
[ -f "$SCRIPT_DIR/retroarch_onn4k_4p_xbox.cfg" ] && adb -s "$TARGET" push "$SCRIPT_DIR/retroarch_onn4k_4p_xbox.cfg" "/sdcard/Android/data/$RA_PKG/files/retroarch.cfg"

echo "=== Done! Pair 1-4 Xbox controllers in Settings -> Remotes & Accessories ==="
