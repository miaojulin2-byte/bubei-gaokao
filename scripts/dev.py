from pathlib import Path
import argparse,subprocess,sys
R=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('task',choices=['prepare','check','build','build-tests','verify']);a=p.parse_args()
tasks={'prepare':['prepare_content.py'],'check':['check_content.py','check_ui_parity.py','verify_fsrs.py','verify_fsrs_trainer.py'],'build':['check_content.py','check_ui_parity.py','build.py'],'build-tests':['build_tests.py'],'verify':['verify.py']}
for script in tasks[a.task]:subprocess.run([sys.executable,str(R/'scripts'/script)],cwd=R,check=True)
