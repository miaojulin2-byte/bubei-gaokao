"""渲染一个实词的词义画。

  Blender -b -P scripts/blender/render.py -- <卡片 id> still <out.png> [宽 高]
  Blender -b -P scripts/blender/render.py -- <卡片 id> anim  <帧目录>  [宽 高]
  Blender -b -P scripts/blender/render.py -- <卡片 id> <特写名> <out.png> [宽 高]   # 场景里定义的检查用特写

场景写在 words.py，每个词一个函数；成片用 scripts/blender/build.sh 渲染并压成 WebP 放进 assets。
"""
import pathlib
import sys

import bpy

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import ink  # noqa: E402
import words  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
card, mode, out = args[0], args[1], args[2]
width, height = (int(args[3]), int(args[4])) if len(args) >= 5 else (1080, 2340)

scene = ink.reset()
shot = words.SCENES[card](scene)
location, target, lens = shot.get(mode, shot["main"])
ink.camera(location, target, lens)
ink.render_settings(scene, width, height)

if mode == "anim":
    scene.frame_start = 0
    scene.frame_end = ink.LOOP - 1
    scene.render.filepath = str(pathlib.Path(out) / "f_")
    bpy.ops.render.render(animation=True)
else:
    scene.frame_set(0)
    scene.render.filepath = out
    bpy.ops.render.render(write_still=True)
print("rendered", out)
