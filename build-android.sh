#!/usr/bin/env bash
# Build the Galaxy Z Fold8 APK using the public Android debug signing key.
set -euo pipefail

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    cat <<'EOF'
Usage: ./build-android.sh [artifact-directory]

Build and verify one arm64-v8a Release APK for Galaxy Z Fold8.
Requires Node >=22.19, Yarn 4.16.0, Python 3, Android SDK 36/Build Tools
36.0.0/NDK 27.1.12297006, JAVA_HOME (prefer JDK 21), and a JDK 17 toolchain.
ANDROID_HOME defaults to ANDROID_SDK_ROOT or the standard macOS/Linux SDK
directory. On macOS, JAVA_HOME defaults to an installed JDK 21 (including
Homebrew). Explicit environment variables take priority.

Artifacts: APK, SHA-256 file, and build.*.log in the specified directory outside
the repository, or a new directory under /workspace/artifacts (when present)
or ${TMPDIR:-/tmp}. Existing APK/checksum files are never overwritten.
Uses public debug signing for personal testing. Does not install or publish.
EOF
    exit 0
fi
if (( $# > 1 )); then
    echo 'Usage: ./build-android.sh [artifact-directory]' >&2
    exit 2
fi

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
cd "$repo_dir"
if [[ -f /workspace/.setup/android-env.sh ]]; then
    source /workspace/.setup/android-env.sh
fi

if [[ -z ${ANDROID_HOME:-} ]]; then
    if [[ -n ${ANDROID_SDK_ROOT:-} ]]; then
        ANDROID_HOME=$ANDROID_SDK_ROOT
    else
        for sdk_candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
            if [[ -d "$sdk_candidate" ]]; then
                ANDROID_HOME=$sdk_candidate
                break
            fi
        done
    fi
fi
if [[ -z ${JAVA_HOME:-} && $(uname -s) == Darwin ]]; then
    JAVA_HOME=$(/usr/libexec/java_home -F -v 21 2>/dev/null || true)
    if [[ -z "$JAVA_HOME" ]]; then
        for jdk_candidate in /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
            /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; do
            if [[ -x "$jdk_candidate/bin/java" ]]; then
                JAVA_HOME=$jdk_candidate
                break
            fi
        done
    fi
fi
: "${ANDROID_HOME:?Android SDK not found; set ANDROID_HOME to its directory}"
: "${JAVA_HOME:?JDK not found; set JAVA_HOME to a JDK (JDK 21 recommended; JDK 17 toolchain also required)}"
[[ -d "$ANDROID_HOME" ]] || { echo "Android SDK directory not found: $ANDROID_HOME" >&2; exit 1; }
[[ -x "$JAVA_HOME/bin/java" ]] || { echo "Java executable not found: $JAVA_HOME/bin/java" >&2; exit 1; }
export ANDROID_HOME JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
for command_name in node yarn python3 git; do
    command -v "$command_name" >/dev/null || { echo "Missing command: $command_name" >&2; exit 1; }
done
node -e 'const [major, minor] = process.versions.node.split(".").map(Number); if (major < 22 || (major === 22 && minor < 19)) { console.error("Node >=22.19 is required"); process.exit(1); }'
[[ $(yarn --version) == 4.16.0 ]] || { echo 'Yarn 4.16.0 is required' >&2; exit 1; }
build_tools="$ANDROID_HOME/build-tools/36.0.0"
for tool_name in apksigner zipalign; do
    [[ -x "$build_tools/$tool_name" ]] || { echo "Missing tool: $build_tools/$tool_name" >&2; exit 1; }
done

if [[ $# == 1 ]]; then
    mkdir -p -- "$1"
    artifact_dir=$(cd -- "$1" && pwd -P)
else
    artifact_base=${TMPDIR:-/tmp}
    if [[ -d /workspace/artifacts ]]; then
        artifact_base=/workspace/artifacts
    fi
    artifact_dir=$(mktemp -d "${artifact_base%/}/joplin-android.XXXXXX")
    artifact_dir=$(cd -- "$artifact_dir" && pwd -P)
fi
case "$artifact_dir/" in
    "$repo_dir/"*) echo 'Artifact directory must be outside the repository' >&2; exit 1 ;;
esac
# Keep logs from separate runs without replacing an earlier build log.
log_file=$(mktemp "$artifact_dir/build.XXXXXX.log")
exec > >(tee "$log_file") 2>&1
trap 'echo "Build failed (exit $?) at line $LINENO. Log: $log_file" >&2' ERR
echo "Artifacts: $artifact_dir"
git branch --show-current
git rev-parse HEAD
git status --short

yarn install --immutable --mode=skip-build
yarn workspace @joplin/whisper-voice-typing build
yarn tsc
yarn workspace @joplin/app-mobile build
(
    cd packages/app-mobile/android
    ./gradlew :app:assembleRelease -PreactNativeArchitectures=arm64-v8a \
        -PJOPLIN_RELEASE_STORE_FILE=debug.keystore \
        -PJOPLIN_RELEASE_STORE_PASSWORD=android \
        -PJOPLIN_RELEASE_KEY_ALIAS=androiddebugkey \
        -PJOPLIN_RELEASE_KEY_PASSWORD=android \
        --no-daemon --max-workers=3 --console=plain
)

apk_path="$repo_dir/packages/app-mobile/android/app/build/outputs/apk/release/app-release.apk"
"$build_tools/apksigner" verify --verbose "$apk_path"
"$build_tools/zipalign" -c -P 16 4 "$apk_path"
python3 - "$apk_path" "$artifact_dir" <<'PY'
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    abis = {name.split('/')[1] for name in archive.namelist()
            if name.startswith('lib/') and name.endswith('.so')}
if abis != {'arm64-v8a'}:
    raise SystemExit(f'Unexpected native ABIs: {sorted(abis)}')
metadata = json.loads((apk.parent / 'output-metadata.json').read_text())
version = metadata['elements'][0]['versionName']
if not version or any(c not in '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-_' for c in version):
    raise SystemExit(f'Invalid version name: {version!r}')
destination = Path(sys.argv[2]) / f'joplin-{version}-arm64-release.apk'
checksum = destination.with_suffix('.apk.sha256')
if destination.exists() or checksum.exists():
    raise SystemExit('APK or checksum already exists; choose a new artifact directory')
with apk.open('rb') as source, destination.open('xb') as target:
    shutil.copyfileobj(source, target)
digest = hashlib.sha256(destination.read_bytes()).hexdigest()
with checksum.open('x') as target:
    target.write(f'{digest}  {destination.name}\n')
print(f'Verified arm64-v8a APK: {destination}')
print(f'SHA-256: {checksum}')
PY
echo "Build and APK verification completed. Log: $log_file"
