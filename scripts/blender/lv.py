"""「率」（率领）：蹑足行伍之间……率疲弊之卒，将数百之众，转而攻秦（《过秦论》）。

三重山加山谷低雾；山谷里两路纵队持矛步卒原地踏步，领头的将军骑马，几面朱砂旗随风飘。
人和马是 Quaternius 的 CC0 模型，换成浓墨；士兵加头盔、短袍下摆和长矛。

用法：
  Blender -b -P scripts/blender/lv.py -- still out.png [宽 高]
  Blender -b -P scripts/blender/lv.py -- anim  帧目录/ [宽 高]   # 渲染 LOOP 帧的循环动画
"""
import math
import pathlib
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import ink  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else ["still", "/tmp/lv.png"]
MODE, OUT = args[0], args[1]
W, H = (int(args[2]), int(args[3])) if len(args) >= 4 else (1080, 2340)
LOOP = 50  # 2 秒，25 帧/秒；走路动作 25 帧一个周期，正好两步

scene = ink.reset()
ink_mat = ink.flat("ink", ink.INK)
red = ink.flat("red", ink.RED, emission=0.15)

ink.mountains("far", 800, 140, 320, 150, 130, 3, ink.rock("rock_far", (0.3, 0.3, 0.3, 1), (0.7, 0.69, 0.66, 1), 3))
ink.mountains("mid", 460, 90, 165, 85, 70, 7, ink.rock("rock_mid", (0.1, 0.1, 0.095, 1), (0.5, 0.49, 0.46, 1), 5))
ink.mountains("near_left", 180, 50, 66, 34, 30, 1, ink.rock("rock_near", (0.07, 0.065, 0.06, 1), (0.38, 0.37, 0.35, 1), 8), res=(160, 60))
ink.ground()
ink.valley_mist()
ink.paper_world(scene)

# 行进方向：从画面左后方走向右前方。
heading = Vector((30, -12, 0)).normalized()
yaw = math.atan2(heading.y, heading.x) + math.pi / 2  # 模型默认面朝 -Y


def dress(root, height):
    """给步卒加头盔、短袍下摆、扛在肩上的长矛。挂在人物根节点上，保持世界坐标。"""
    def add(obj):
        obj.data.materials.append(ink_mat)
        bpy.context.view_layer.update()  # 先刷新，矩阵里才有刚设的倾斜
        mw = obj.matrix_world.copy()
        obj.parent = root
        obj.matrix_world = mw
    loc = root.location
    k = height / 1.75
    bpy.ops.mesh.primitive_cone_add(radius1=0.17 * k, depth=0.22, location=loc + Vector((0, 0, height + 0.05)), vertices=14)
    add(bpy.context.active_object)
    bpy.ops.mesh.primitive_cone_add(radius1=0.36 * k, radius2=0.2, depth=0.5, location=loc + Vector((0, 0, 0.72 * k)), vertices=16)
    add(bpy.context.active_object)
    side = Vector((heading.y, -heading.x, 0)) * 0.22
    spear_base = loc + side + heading * -0.1 + Vector((0, 0, 0.3))
    spear_dir = (heading * 0.45 + Vector((0, 0, 1))).normalized()
    length = 3.4
    bpy.ops.mesh.primitive_cylinder_add(radius=0.04, depth=length, location=spear_base + spear_dir * length / 2, vertices=6)
    s = bpy.context.active_object
    s.rotation_euler = spear_dir.to_track_quat("Z", "Y").to_euler()
    add(s)
    bpy.ops.mesh.primitive_cone_add(radius1=0.06, depth=0.32, location=spear_base + spear_dir * (length + 0.14), vertices=8)
    tip = bpy.context.active_object
    tip.rotation_euler = spear_dir.to_track_quat("Z", "Y").to_euler()
    add(tip)


template, template_arm, template_strip = ink.character("man.glb", 1.75, "Man_Walk", ink_mat)
template_strip.repeat = 20
soldiers = []
for i in range(26):
    for col in (-0.9, 0.9):
        t = i / 25
        root, arm = (template, template_arm) if not soldiers else ink.duplicate(template)
        root.location = Vector((-26 + t * 30, 30 - t * 12 + col, 0.5)) + Vector((math.sin(i * 1.7) * 0.25, math.cos(i * 2.3) * 0.2, 0))
        root.rotation_euler = (0, 0, yaw)
        # 每人的步伐错开几帧，看起来不齐步；错开整数帧，2 秒循环仍然无缝。
        strip = arm.animation_data.nla_tracks[0].strips[0]
        strip.frame_start = -((i * 7 + (col > 0) * 11) % 25)
        soldiers.append(root)
bpy.context.view_layer.update()
for root in soldiers:
    dress(root, 1.75)

flags = []
for i, (x, y) in enumerate([(-20, 27.6), (-11, 24), (-2, 20.4)]):
    flags.append(ink.flag(x, y + 1.6, 0.5, 4.6, 1.0, ink_mat, red, f"flag{i}")[1])

# 将军：马走路，人用“坐”的姿势挂在马鞍处的脊骨上，随马起伏。
horse, horse_arm, horse_strip = ink.character("horse.glb", 2.5, "Walk", ink_mat)
horse.location = (7.5, 16.5, 0.5)
horse.rotation_euler = (0, 0, yaw)
horse_strip.scale = 25 / 28  # 马的走路动作 28 帧，压成 25 帧和步卒同周期
horse_strip.repeat = 20
rider, rider_arm, _ = ink.character("man.glb", 1.75, "Man_Sitting", ink_mat, frame=100)
bpy.context.view_layer.update()
# 让将军的臀部骨骼正好落在马背最凹处（马鞍）。坐姿的臀部在脚底后上方，不能拿原点去对。
seat = ink.saddle(horse)
rider.rotation_euler = (0, 0, yaw)
bpy.context.view_layer.update()
hips = rider_arm.matrix_world @ rider_arm.pose.bones["Hips"].head
rider.location += seat + Vector((0, 0, 0.08)) - hips
bpy.context.view_layer.update()
bpy.ops.object.select_all(action="DESELECT")
rider.select_set(True)
horse_arm.select_set(True)
bpy.context.view_layer.objects.active = horse_arm
horse_arm.data.bones.active = horse_arm.data.bones["Torso"]
bpy.ops.object.parent_set(type="BONE", keep_transform=True)
# 将军戴盔、举大旗，都挂在将军身上，随马起伏。
bpy.context.view_layer.update()
head = rider_arm.matrix_world @ rider_arm.pose.bones["Head"].tail
bpy.ops.mesh.primitive_cone_add(radius1=0.19, depth=0.3, location=head + Vector((0, 0, 0.05)), vertices=14)
helm = bpy.context.active_object
helm.data.materials.append(ink_mat)
hand = rider_arm.matrix_world @ rider_arm.pose.bones["Hips"].head + heading * 0.35 + Vector((heading.y, -heading.x, 0)) * -0.3
pole, general_flag = ink.flag(hand.x, hand.y, hand.z - 0.6, 5.2, 1.4, ink_mat, red, "flag_general")
bpy.context.view_layer.update()
for o in (helm, pole, general_flag):
    mw = o.matrix_world.copy()
    o.parent = rider
    o.matrix_world = mw
flags.append(general_flag)

ink.wave_flags(flags, LOOP)
if MODE == "horse":
    side = Vector((heading.y, -heading.x, 0))
    ink.camera(Vector((7.5, 16.5, 1.8)) - side * 8, (7.5, 16.5, 1.6), 50)
elif MODE == "closeup":
    ink.camera((-2, 4, 3.5), (4, 20, 1.6), 50)
else:
    ink.camera((-4, -24, 14), (2, 30, 12), 22)
ink.render_settings(scene, W, H)

if MODE in ("still", "closeup", "horse"):
    scene.frame_set(0)
    scene.render.filepath = OUT
    bpy.ops.render.render(write_still=True)
else:
    scene.frame_start = 0
    scene.frame_end = LOOP - 1
    scene.render.filepath = str(pathlib.Path(OUT) / "f_")
    bpy.ops.render.render(animation=True)
print("rendered", OUT)
