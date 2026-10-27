#!/usr/bin/env python3
"""Offline parameter fitting: deterministic synthetic recall outcomes, holdout and bounds."""
from pathlib import Path
import subprocess
from build import toolchain_root
root=Path(__file__).resolve().parents[1];jdk=next((toolchain_root()/'jdk').glob('*/Contents/Home'));out=root/'output/fsrs-personal/math';out.mkdir(parents=True,exist_ok=True)
subprocess.run([str(jdk/'bin/javac'),'-encoding','UTF-8','-d',str(out),*[str(root/f) for f in ['android/src/cn/local/gaokao/Fsrs.java','android/src/cn/local/gaokao/FsrsTrainer.java','tests/FsrsTrainerCheck.java']]],check=True)
result=subprocess.check_output([str(jdk/'bin/java'),'-cp',str(out),'cn.local.gaokao.FsrsTrainerCheck'],text=True);(out/'result.txt').write_text(result);print(result,end='')
