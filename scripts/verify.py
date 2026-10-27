"""Isolate and restore only cn.local.gaokao data on the dedicated Wenyan emulator."""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess
from build import toolchain_root

R = Path(__file__).resolve().parents[1]
ADB = toolchain_root() / 'platform-tools/adb'
SERIAL = 'emulator-5556'
PKG = 'cn.local.gaokao'
OUT = R / 'output/verification'
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output([str(ADB), '-s', SERIAL, *args], stderr=subprocess.STDOUT)


def app(command):
    return adb('shell', 'run-as', PKG, 'sh', '-c', shlex.quote(command))


def fingerprints():
    return sorted(app('for folder in databases shared_prefs; do if [ -d "$folder" ]; then find "$folder" -type f -exec sha256sum {} \\; ; fi; done').decode().splitlines())


def main():
    if adb('emu', 'avd', 'name').decode().splitlines()[0] != 'Wenyan':
        raise SystemExit('Refusing any emulator other than Wenyan/5556')
    adb('install', '-r', str(R / '安装包/不背高考-最新版.apk'))
    adb('install', '-r', str(R / 'output/android-tests/tests.apk'))
    adb('shell', 'am', 'force-stop', PKG)
    before = fingerprints()
    moved = []
    for folder in ('databases', 'shared_prefs'):
        if app(f'if [ -e {folder}.gaokao-verify ]; then echo exists; fi').strip():
            raise SystemExit('Previous recovery data exists; restore before rerunning')
    results = []
    try:
        for folder in ('databases', 'shared_prefs'):
            if app(f'if [ -d {folder} ]; then echo exists; fi').strip():
                app(f'mv {folder} {folder}.gaokao-verify')
                moved.append(folder)
        for suite in ('FourPointInstrumentation', 'UnifiedInstrumentation'):
            adb('shell', 'am', 'force-stop', PKG)
            app('rm -rf databases shared_prefs')
            result = adb('shell', 'am', 'instrument', '-w', PKG + '.tests/' + PKG + '.' + suite).decode()
            (OUT / (suite + '.txt')).write_text(result)
            print(result, flush=True)
            results.append({'suite': suite, 'passed': 'PASS ' in result and 'FAIL ' not in result})
            if not results[-1]['passed']:
                raise RuntimeError(suite + ' failed')
        subprocess.run([str(ADB), '-s', SERIAL, 'pull', f'/sdcard/Android/data/{PKG}/files/unified', str(OUT)], check=True)
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        for folder in ('databases', 'shared_prefs'):
            app(f'rm -rf {folder}')
            if folder in moved:
                app(f'mv {folder}.gaokao-verify {folder}')
        after = fingerprints()
        report = {'suites': results, 'emulator': 'Wenyan/5556', 'original_data_restored': before == after,
                  'apk_sha256': hashlib.sha256((R / '安装包/不背高考-最新版.apk').read_bytes()).hexdigest()}
        (OUT / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        if before != after:
            raise RuntimeError('Original data fingerprints changed')


if __name__ == '__main__':
    main()
