"""Validation script for Cartridge Android project structure and manifest."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
assert manifest_path.is_file(), "AndroidManifest.xml missing"

tree = ET.parse(manifest_path)
root = tree.getroot()

# Check namespace and package configuration
assert root.tag == "manifest", "Root must be manifest"
app = root.find("application")
assert app is not None, "application element missing"
assert app.attrib.get("{http://schemas.android.com/apk/res/android}hardwareAccelerated") == "true", "Hardware acceleration required"

activities = app.findall("activity")
assert len(activities) >= 1, "MainActivity missing"
main_act = activities[0]
assert main_act.attrib.get("{http://schemas.android.com/apk/res/android}name") == ".MainActivity"
assert main_act.attrib.get("{http://schemas.android.com/apk/res/android}windowSoftInputMode") == "adjustResize", (
    "MainActivity must set windowSoftInputMode=adjustResize for native Android keyboard input"
)

# Check FileProvider for Android 11-15 Scoped Storage ROM handoff to external emulators
providers = app.findall("provider")
assert any(
    p.attrib.get("{http://schemas.android.com/apk/res/android}name") == "androidx.core.content.FileProvider"
    for p in providers
), "FileProvider required for external emulator ROM handoff"

# Check kotlin code
main_kt = ROOT / "app/src/main/java/org/moveweight/cartridge/MainActivity.kt"
assert main_kt.is_file(), "MainActivity.kt missing"
content = main_kt.read_text(encoding="utf-8")
assert "CartridgeNativeBridge" in content, "Bridge missing"
assert "mapKeyCodeToJsKey" in content, "Gamepad mapper missing"
assert "launchExternalEmulator" in content, "Emulator launcher missing"
assert "CartridgeLauncherApp" in content, "Native Compose CartridgeLauncherApp missing"
assert content.index("setContentView(R.layout.activity_main)") < content.index("hideSystemUI()"), (
    "setContentView must run before hideSystemUI() to avoid NullPointerException on Android 11+ PhoneWindow.mDecor"
)

compose_ui = ROOT / "app/src/main/java/org/moveweight/cartridge/ui/CartridgeLauncherApp.kt"
assert compose_ui.is_file(), "CartridgeLauncherApp.kt missing"
ui_content = compose_ui.read_text(encoding="utf-8")
assert "OutlinedTextField" in ui_content, "Native Android OutlinedTextField required for system keyboard input"

print("Cartridge Android native Compose launcher project verified successfully.")
