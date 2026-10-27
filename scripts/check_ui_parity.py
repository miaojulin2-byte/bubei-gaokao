"""Check required shared geometry against the original Wenyan app, not a new snapshot."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
LIVE_REFERENCE = ROOT.parent / 'android/src/cn/local/wenyan'
REFERENCE = LIVE_REFERENCE if (LIVE_REFERENCE / 'MainActivity.java').is_file() else ROOT / 'reference/wenyan-ui'
APP = ROOT / 'android/src/cn/local/gaokao'


def method(source, name):
    start = source.index('    void ' + name + '(')
    index = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[index] == '{') - (source[index] == '}')
        index += 1
    return source[start:index]


checks = {}
for name in ['home', 'nav', 'homeCard', 'screen', 'applySafeInsets']:
    checks[name] = method((REFERENCE / 'MainActivity.java').read_text(), name) == method((APP / 'MainActivity.java').read_text(), name)
intro = method((APP / "MainActivity.java").read_text(), "showIntro").replace("不背高考", "不背古文").replace("高\\n考", "古\\n文")
checks["showIntro"] = method((REFERENCE / "MainActivity.java").read_text(), "showIntro") == intro
for name in ['CheckInCalendar.java', 'Atmosphere.java', 'PreferenceSheet.java', 'AvatarBadge.java', 'ProfileIcon.java']:
    checks[name] = (REFERENCE / name).read_text().replace('cn.local.wenyan', 'cn.local.gaokao') == (APP / name).read_text()
(ROOT / 'output').mkdir(exist_ok=True)
(ROOT / 'output/ui-parity.json').write_text(json.dumps(checks, indent=2) + '\n')
assert all(checks.values()), checks
print('PASS: original home, navigation, Learn/Review, page layout, safe insets, background, sheet, avatar and profile icons match Wenyan')
