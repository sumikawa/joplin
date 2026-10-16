"""Install the distributed APK and capture a fresh emulator launch."""

import json
from pathlib import Path
import subprocess
import tempfile
import time
import zipfile


evidence = Path('launch-evidence')
evidence.mkdir(exist_ok=True)
package = 'net.cozic.joplin'
apk, = Path('launch-apk').glob('*.apk')


def adb(*args, check=True):
    return subprocess.run(['adb', *args], capture_output=True, check=check)


def save(name, result):
    (evidence / name).write_bytes(result.stdout + result.stderr)
    print((result.stdout + result.stderr).decode(errors='replace'))


try:
    save('device-properties.txt', adb('shell', 'getprop'))
    install = adb('install', '-r', str(apk), check=False)
    save('install.txt', install)
    install.check_returncode()
    # SoLoader's direct-APK source uses the host ABI on native-bridge emulators.
    # Extract the unchanged ARM64 libraries into the installed application's
    # nativeLibraryDir, as Android does for legacy native-library packaging.
    # This is an emulator-only installation adjustment; the APK is unchanged.
    host_abi = adb('shell', 'getprop', 'ro.product.cpu.abi').stdout.decode().strip()
    if host_abi != 'arm64-v8a':
        save('native-bridge-setup.txt', adb('root'))
        adb('wait-for-device')
        base_apk = adb('shell', 'pm', 'path', package).stdout.decode().strip().splitlines()[0]
        app_directory = base_apk.removeprefix('package:').rsplit('/', 1)[0]
        library_directory = f'{app_directory}/lib/arm64'
        adb('shell', 'mkdir', '-p', library_directory)
        with tempfile.TemporaryDirectory() as directory, zipfile.ZipFile(apk) as archive:
            for name in archive.namelist():
                if name.startswith('lib/arm64-v8a/') and name.endswith('.so'):
                    local = Path(directory) / Path(name).name
                    local.write_bytes(archive.read(name))
                    adb('push', str(local), f'{library_directory}/{local.name}')
        adb('shell', 'chmod', '-R', '755', f'{app_directory}/lib')
        adb('shell', 'restorecon', '-RF', f'{app_directory}/lib')
    adb('logcat', '-c')
    launch = adb('shell', 'am', 'start', '-W', '-n', f'{package}/.MainActivity', check=False)
    save('launch.txt', launch)
    launch.check_returncode()
    observations = []
    for second in range(0, 61, 5):
        if second:
            time.sleep(5)
        pid = adb('shell', 'pidof', package, check=False).stdout.decode().strip()
        observations.append({'seconds': second, 'pid': pid})
        (evidence / 'process-observations.json').write_text(json.dumps(observations, indent=2))
        if not pid:
            raise RuntimeError(f'Application exited at {second} seconds after launch')
    adb('shell', 'uiautomator', 'dump', '/sdcard/launch-window.xml', check=False)
    adb('pull', '/sdcard/launch-window.xml', str(evidence / 'window.xml'), check=False)
finally:
    save('logcat.txt', adb('logcat', '-d', '-v', 'threadtime', check=False))
    save('crash-buffer.txt', adb('logcat', '-b', 'crash', '-d', check=False))
    (evidence / 'screen.png').write_bytes(adb('exec-out', 'screencap', '-p', check=False).stdout)
    save('activities.txt', adb('shell', 'dumpsys', 'activity', 'activities', check=False))
