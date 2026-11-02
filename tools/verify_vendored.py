"""Verify unchanged, audited upstream source/resources and official Wrapper JAR."""
from pathlib import Path
import hashlib
import json
root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / 'audit/vendored-sha256.json').read_text())
for name, expected in manifest.items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected, name
assert hashlib.sha256((root / 'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest() == '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
print(f'PASS: {len(manifest)} upstream files and Gradle Wrapper checksum')
