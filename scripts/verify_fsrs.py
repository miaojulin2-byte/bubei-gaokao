#!/usr/bin/env python3
"""Verify the Java FSRS math against pinned upstream vectors. No Android device or network."""
from pathlib import Path
import subprocess
from build import toolchain_root
root=Path(__file__).resolve().parents[1]
jdk=next((toolchain_root()/'jdk').glob('*/Contents/Home'))
out=root/'output/fsrs/math';out.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'bin/javac'),'-encoding','UTF-8','-d',str(out),str(root/'android/src/cn/local/gaokao/Fsrs.java'),str(root/'tests/FsrsMathCheck.java')],check=True)
result=subprocess.check_output([str(jdk/'bin/java'),'-cp',str(out),'cn.local.gaokao.FsrsMathCheck',str(root/'tests/fixtures/fsrs6-golden.tsv')],text=True)
(root/'output/fsrs/math.txt').write_text(result);print(result,end='')
