#!/usr/bin/env python3
import os,shutil,subprocess,zipfile
from pathlib import Path
from build import toolchain_root
R=Path(__file__).resolve().parents[1];T=toolchain_root();jdk=next((T/'jdk').glob('*/Contents/Home'));bt=T/'android-sdk/android-15';platform=T/'android-sdk/android-35/android.jar';out=R/'output/android-tests';out.mkdir(parents=True,exist_ok=True);classes=out/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
env=dict(os.environ,JAVA_HOME=str(jdk));env['PATH']=str(jdk/'bin')+':'+env['PATH']
def run(*a):subprocess.run(list(map(str,a)),env=env,check=True)
run(jdk/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',str(platform)+os.pathsep+str(R/'output/android/classes'),'-d',classes,*R.glob('tests/*Instrumentation.java'))
run(bt/'d8','--lib',platform,'--classpath',R/'output/android/classes','--min-api','26','--output',out,*classes.rglob('*.class'))
run(bt/'aapt2','link','--manifest',R/'tests/AndroidManifest.xml','-I',platform,'-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(out/'classes.dex','classes.dex')
run(bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(bt/'apksigner','sign','--ks',R/'tools/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'tests.apk',out/'aligned.apk')
