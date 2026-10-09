#!/usr/bin/env python3
"""Sign only the independently installed SCVTC Next application."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

if len(sys.argv) != 4:
    sys.exit("Usage: sign-next.py unsigned.apk signed.apk /private/signing.json")

unsigned, output, config_path = map(Path, sys.argv[1:])
config = json.loads(config_path.read_text(encoding='utf-8-sig'))
build_tools = Path(os.environ["ANDROID_SDK_ROOT"]) / "build-tools"
tools = max(build_tools.iterdir(), key=lambda p: tuple(map(int, p.name.split("."))))
def command(name):
    if os.name == "nt":
        if name == "apksigner":
            java = Path(os.environ["JAVA_HOME"]) / "bin" / "java.exe"
            return [str(java), "-jar", str(tools / "lib" / "apksigner.jar")]
        return [str(tools / (name + ".exe"))]
    return [str(tools / name)]

staging = os.environ.get("SCVTC_TOOL_TEMP", tempfile.gettempdir())
if os.name == "nt" and not staging.isascii():
    sys.exit("Set SCVTC_TOOL_TEMP to an ASCII temporary directory.")
with tempfile.TemporaryDirectory(prefix="scvtc-manifest-", dir=staging) as temporary:
    manifest_input = Path(temporary) / "input.apk"
    shutil.copyfile(unsigned, manifest_input)
    manifest = subprocess.check_output([*command("aapt"), "dump", "badging", str(manifest_input)], text=True, encoding="utf-8", errors="replace")
if not re.search(r"^package: name='cn\.scvtc\.campus\.next'", manifest):
    sys.exit("This key signs only cn.scvtc.campus.next.")
env = os.environ.copy()
env["SCVTC_NEXT_STORE_PASSWORD"] = config["storePassword"]
env["SCVTC_NEXT_KEY_PASSWORD"] = config["keyPassword"]
output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory() as temporary:
    aligned = Path(temporary) / "aligned.apk"
    subprocess.run([*command("zipalign"), "-P", "16", "-f", "4", str(unsigned), str(aligned)], check=True)
    subprocess.run([
        *command("apksigner"), "sign", "--ks", str(config_path.parent / config["keystore"]),
        "--ks-key-alias", config["alias"], "--ks-pass", "env:SCVTC_NEXT_STORE_PASSWORD",
        "--key-pass", "env:SCVTC_NEXT_KEY_PASSWORD", "--out", str(output), str(aligned)
    ], env=env, check=True)
certificate = subprocess.check_output([
    *command("apksigner"), "verify", "--verbose", "--print-certs", str(output)
], text=True)
fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", certificate).group(1)
if fingerprint == "5cab58b4d47d14e66143be594d3a4eebb5624be7e8f6a15354126bdab47343b3":
    output.unlink()
    sys.exit("Next must use a certificate distinct from OLD.")
expected_path = Path(__file__).with_name("next-certificate.sha256")
if not expected_path.exists() or fingerprint != expected_path.read_text().strip():
    output.unlink()
    sys.exit("The Next certificate differs from the saved one; output removed.")

subprocess.run([*command("zipalign"), "-c", "-P", "16", "4", str(output)], check=True)
with zipfile.ZipFile(output) as apk:
    if apk.testzip():
        sys.exit("APK ZIP integrity check failed.")
output.with_suffix(".apk.sha256").write_text(hashlib.sha256(output.read_bytes()).hexdigest() + "  " + output.name + "\n")
print(certificate)
