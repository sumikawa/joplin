"""Install an APK matching the emulator ABI and capture its startup."""

import json
from pathlib import Path
import subprocess
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
    host_abi = adb('shell', 'getprop', 'ro.product.cpu.abi').stdout.decode().strip()
    with zipfile.ZipFile(apk) as archive:
        apk_abis = {n.split('/')[1] for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')}
    if host_abi not in apk_abis:
        raise RuntimeError(f'Emulator ABI {host_abi} does not match APK ABIs {sorted(apk_abis)}. Use a native emulator test APK.')
    install = adb('install', '-r', str(apk), check=False)
    save('install.txt', install)
    install.check_returncode()
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
