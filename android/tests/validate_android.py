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

# Check kotlin code
main_kt = ROOT / "app/src/main/java/org/moveweight/cartridge/MainActivity.kt"
assert main_kt.is_file(), "MainActivity.kt missing"
content = main_kt.read_text()
assert "CartridgeNativeBridge" in content, "Bridge missing"
assert "mapKeyCodeToJsKey" in content, "Gamepad mapper missing"
assert "launchExternalEmulator" in content, "Emulator launcher missing"

print("Cartridge Android project verified successfully.")
