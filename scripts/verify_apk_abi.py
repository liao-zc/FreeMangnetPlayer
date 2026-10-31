#!/usr/bin/env python3
"""Assert an APK contains JNI libraries for only its requested device ABI."""
import sys
import zipfile

if len(sys.argv) != 3:
    raise SystemExit("usage: verify_apk_abi.py APK EXPECTED_ABI")

apk, expected = sys.argv[1:]
allowed = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
if expected not in allowed:
    raise SystemExit(f"unsupported ABI: {expected}")
with zipfile.ZipFile(apk) as archive:
    abis = {
        name.split("/")[1]
        for name in archive.namelist()
        if name.startswith("lib/") and name.endswith(".so") and len(name.split("/")) >= 3
    }
if abis != {expected}:
    raise SystemExit(f"{apk}: expected only {expected}; found {sorted(abis)}")
print(f"{apk}: JNI libraries restricted to {expected}")
