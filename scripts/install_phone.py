"""Install the verified independent app and verify existing app data fingerprints."""
from pathlib import Path
import argparse
import hashlib
import json
import shlex
import subprocess
from build import toolchain_root

R = Path(__file__).resolve().parents[1]
ADB = toolchain_root() / 'platform-tools/adb'
PKG = 'cn.local.gaokao'
OLD = ['cn.local.wenyan', 'cn.local.gushi', 'cn.bubei.biology']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    if args.serial.startswith('emulator-'):
        raise SystemExit('Use verify.py for emulator checks')

    def adb(*parts):
        return subprocess.check_output([str(ADB), '-s', args.serial, *parts], stderr=subprocess.STDOUT)

    apk = R / '安装包/不背高考-最新版.apk'
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    result = json.loads((R / 'output/verification/result.json').read_text())
    if result['apk_sha256'] != sha or not result['original_data_restored'] or not all(x['passed'] for x in result['suites']):
        raise SystemExit('Current APK has not passed verification')
    if adb('get-state').strip() != b'device':
        raise SystemExit('Phone is unavailable')
    installed = adb('shell', 'pm', 'list', 'packages').decode().splitlines()
    packages = [p for p in OLD + [PKG] if 'package:' + p in installed]
    for package in packages:
        adb('shell', 'am', 'force-stop', package)

    unavailable = {}

    def fingerprints(package):
        code = 'for folder in databases shared_prefs app_webview; do if [ -d "$folder" ]; then find "$folder" -type f -exec sha256sum {} \\; ; fi; done'
        try:
            return sorted(adb('shell', 'run-as', package, 'sh', '-c', shlex.quote(code)).decode().splitlines())
        except subprocess.CalledProcessError as failure:
            reason = failure.output.decode().strip()
            if 'package not debuggable' not in reason:
                raise
            unavailable[package] = reason
            return None

    before = {p: fingerprints(p) for p in packages}
    install = adb('install', '--no-incremental', '-r', str(apk)).decode()
    if 'Success' not in install:
        raise SystemExit(install)
    after = {p: fingerprints(p) for p in packages}
    report = {'package': PKG, 'version': '0.1.1', 'apk_sha256': sha, 'before': before, 'after': after,
              'readable_existing_data_unchanged_before_launch': before == after,
              'unavailable_data_fingerprints': unavailable,
              'old_records_imported': False,
              'device_model': adb('shell', 'getprop', 'ro.product.model').decode().strip()}
    output = R / 'output/phone-install-verification.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    if before != after:
        raise SystemExit('Data fingerprints changed; new app not launched')
    print('PASS: verified independent APK installed; readable existing app files unchanged before launch')
    if unavailable:
        print('Unavailable private data fingerprints: ' + ', '.join(unavailable))
    print(adb('shell', 'am', 'start', '-n', PKG + '/.MainActivity').decode())


if __name__ == '__main__':
    main()
