"""Validate the device or emulator APK before uploading it as an artifact."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


root = Path(__file__).resolve().parents[2]
source = root / 'packages/app-mobile/android/app/build/outputs/apk/release/app-release.apk'
output = Path(os.environ['ARTIFACT_DIR'])
output.mkdir(parents=True, exist_ok=True)
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
metadata = json.loads((source.parent / 'output-metadata.json').read_text())
version = metadata['elements'][0]['versionName']
expected_abi = os.environ.get('APK_ABI', 'arm64-v8a')
if expected_abi not in ('arm64-v8a', 'x86_64'):
    raise ValueError(f'Unsupported build ABI: {expected_abi}')
label = 'arm64' if expected_abi == 'arm64-v8a' else 'x86_64'
device = 'Galaxy ZFold 8 / Samsung DeX' if expected_abi == 'arm64-v8a' else 'Android x86_64 emulator (testing only)'
apk = output / f'joplin-{version}-{label}-release.apk'
shutil.copy2(source, apk)

for name, args in [
    ('signature', [str(tools / 'apksigner'), 'verify', '--verbose', str(apk)]),
    ('alignment', [str(tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)]),
]:
    result = subprocess.run(args, capture_output=True, text=True)
    (output / 'logs').mkdir(exist_ok=True)
    (output / 'logs' / f'{name}.log').write_text(result.stdout + result.stderr)
    result.check_returncode()

with zipfile.ZipFile(apk) as archive:
    libraries = [n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
    abis = sorted({n.split('/')[1] for n in libraries})
    if abis != [expected_abi]:
        raise RuntimeError(f'Unexpected APK architectures: {abis}')
    if 'assets/index.android.bundle' not in archive.namelist():
        raise RuntimeError('APK is missing its JavaScript bundle')

checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
(output / f'{apk.name}.sha256').write_text(f'{checksum}  {apk.name}\n')
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
report = {
    'version': version,
    'commit': commit,
    'abi': abis,
    'native_libraries': len(libraries),
    'sha256': checksum,
    'bytes': apk.stat().st_size,
    'signature_verified': True,
    'alignment_16kb_verified': True,
    'signature': 'Public Android Debug key; personal testing only',
    'device_tested': False,
}
(output / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
description = f'''# Joplin {version} personal Android build

- Device: {device}
- ABI: {expected_abi} only
- Source commit: {commit}
- Signature: public Android Debug key (personal testing)
- Verified: signature, 16KB ZIP alignment, ABI, JavaScript bundle
- Physical device / DeX testing: not performed
- SHA-256: {checksum}

Updating an existing installation requires the same signing key.
'''
(output / 'README.md').write_text(description)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
        summary.write(description + '\nDownload the APK from this run\'s Artifacts section.\n')
print(json.dumps(report, indent=2))
