#!/usr/bin/env python3
"""Build a local debug APK with the pinned Android 35 toolchain, without Gradle."""
import os, shutil, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def toolchain_root():
    configured=os.environ.get("WENYAN_TOOLCHAIN_ROOT")
    if configured: return Path(configured)
    for candidate in (ROOT/"tools", ROOT.parent/"tools", ROOT.parent/"逃耻"/"tools", ROOT.parent.parent/"tools", ROOT.parent.parent/"逃耻"/"tools"):
        if (candidate/"jdk").is_dir(): return candidate
    return ROOT.parent/"tools"

def main():
    toolchain=toolchain_root()
    (ROOT/"tools").mkdir(exist_ok=True)
    jdk=next((toolchain/'jdk').glob('*/Contents/Home'))
    build=toolchain/'android-sdk/android-15'
    platform=toolchain/'android-sdk/android-35/android.jar'
    out=ROOT/'output/android';out.mkdir(parents=True,exist_ok=True)
    classes=out/'classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir()
    env=os.environ.copy();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+':'+env['PATH']
    def run(*args):subprocess.run([str(a) for a in args],env=env,check=True)
    run(jdk/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',platform,'-d',classes,*ROOT.glob('android/src/**/*.java'))
    run(build/'d8','--lib',platform,'--min-api','26','--output',out,*classes.rglob('*.class'))
    unsigned=out/'unsigned.apk';aligned=out/'aligned.apk';final=out/'gaokao-0.1.1.apk'
    run(build/'aapt2','compile','--dir',ROOT/'android/res','-o',out/'resources.zip')
    run(build/'aapt2','link','--manifest',ROOT/'android/AndroidManifest.xml','-I',platform,'-o',unsigned,'-A',ROOT/'android/assets',out/'resources.zip')
    with zipfile.ZipFile(unsigned,'a') as z:z.write(out/'classes.dex','classes.dex')
    run(build/'zipalign','-f','4',unsigned,aligned)
    key=ROOT/'tools/debug.keystore'
    if not key.exists():
        run(jdk/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Local Development,C=CN','-keyalg','RSA','-keysize','2048','-validity','10000')
    run(build/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',final,aligned)
    run(build/'apksigner','verify','--verbose',final)
    run(build/'aapt','dump','badging',final)
    releases=ROOT/'安装包';releases.mkdir(exist_ok=True)
    latest=releases/'不背高考-最新版.apk'
    temporary=releases/'不背高考-最新版.apk.tmp'
    shutil.copyfile(final,temporary);temporary.replace(latest)
    print(latest)

if __name__=='__main__':main()
