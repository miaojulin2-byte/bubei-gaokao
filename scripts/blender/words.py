"""每个实词一幅词义画：画的是卡片里某一个例句的意思（与 assets/illustrations 的 data-example 对应）。

每个函数搭好场景，返回机位：{"main": (位置, 看向, 焦距), 其他检查用特写...}。
竖屏 1080x2340：上方约四成留给纸色天空写字，主体放在画面 55%~70% 高度，底部会被 Learn/Review 卡片挡住。
"""
import math

import bmesh
import bpy
from mathutils import Vector

import ink


def landscape(far=(800, 140, 320, 150, 130, 3), mid=(460, 90, 165, 85, 70, 7), near=(180, 50, 66, 34, 30, 1), mist=True):
    """三重山、山谷地面、低雾、纸色天空，大多数场景的底子。参数是 mountains() 的尺寸，None 表示不要这一重。"""
    if far:
        ink.mountains("far", *far, ink.rock("rock_far", (0.3, 0.3, 0.3, 1), (0.7, 0.69, 0.66, 1), 3))
    if mid:
        ink.mountains("mid", *mid, ink.rock("rock_mid", (0.1, 0.1, 0.095, 1), (0.5, 0.49, 0.46, 1), 5))
    if near:
        ink.mountains("near", *near, ink.rock("rock_near", (0.07, 0.065, 0.06, 1), (0.38, 0.37, 0.35, 1), 8), res=(160, 60))
    if mist:
        ink.valley_mist()
    ink.paper_world(bpy.context.scene)


# ---- 率：率领。蹑足行伍之间……率疲弊之卒，将数百之众，转而攻秦（《过秦论》） ----
def lv(scene):
    """山谷里两路纵队持矛步卒行进，领头的将军骑马举旗，几面朱砂旗随风飘。"""
    landscape()
    ink.ground()
    black = ink.flat("ink", ink.INK)
    red = ink.flat("red", ink.RED, emission=0.15)
    heading = Vector((30, -12, 0)).normalized()
    side = Vector((heading.y, -heading.x, 0))
    yaw = ink.yaw_toward(heading)

    def dress(root):
        """头盔、短袍下摆、扛在肩上的长矛。"""
        loc = root.location
        ink.prop(bpy.ops.mesh.primitive_cone_add, black, root, radius1=0.17, depth=0.22, location=loc + Vector((0, 0, 1.8)), vertices=14)
        ink.prop(bpy.ops.mesh.primitive_cone_add, black, root, radius1=0.36, radius2=0.2, depth=0.5, location=loc + Vector((0, 0, 0.72)), vertices=16)
        base = loc + side * 0.22 - heading * 0.1 + Vector((0, 0, 0.3))
        d = (heading * 0.45 + Vector((0, 0, 1))).normalized()
        rot = d.to_track_quat("Z", "Y").to_euler()
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, black, root, radius=0.04, depth=3.4, location=base + d * 1.7, rotation=rot, vertices=6)
        ink.prop(bpy.ops.mesh.primitive_cone_add, black, root, radius1=0.06, depth=0.32, location=base + d * 3.54, rotation=rot, vertices=8)

    template, template_arm, strip = ink.character("man.glb", 1.75, "Man_Walk", black)
    strip.repeat = 20
    soldiers = []
    for i in range(26):
        for col in (-0.9, 0.9):
            t = i / 25
            root, arm = (template, template_arm) if not soldiers else ink.duplicate(template)
            root.location = Vector((-26 + t * 30, 30 - t * 12 + col, 0.5)) + Vector((math.sin(i * 1.7) * 0.25, math.cos(i * 2.3) * 0.2, 0))
            root.rotation_euler = (0, 0, yaw)
            # 步伐错开整数帧，看起来不齐步，2 秒循环仍然无缝。
            arm.animation_data.nla_tracks[-1].strips[0].frame_start = -((i * 7 + (col > 0) * 11) % 25)
            soldiers.append(root)
    for root in soldiers:
        dress(root)

    flags = [ink.flag(x, y + 1.6, 0.5, 4.6, 1.0, black, red, f"flag{i}")[1] for i, (x, y) in enumerate([(-20, 27.6), (-11, 24), (-2, 20.4)])]

    horse, horse_arm, horse_strip = ink.character("horse.glb", 2.5, "Walk", black)
    horse.location = (7.5, 16.5, 0.5)
    horse.rotation_euler = (0, 0, yaw)
    horse_strip.scale = 25 / 28  # 马的走路动作 28 帧，压成 25 帧和步卒同周期
    horse_strip.repeat = 20
    rider, rider_arm, _ = ink.character("man.glb", 1.75, "Man_Sitting", black, frame=100)
    rider.rotation_euler = (0, 0, yaw)
    ink.mount(horse, horse_arm, rider, rider_arm)
    head = ink.bone_point(rider_arm, "Head", tail=True)
    ink.prop(bpy.ops.mesh.primitive_cone_add, black, rider, radius1=0.19, depth=0.3, location=head + Vector((0, 0, 0.05)), vertices=14)
    hand = ink.bone_point(rider_arm, "Hips") + heading * 0.35 - side * 0.3
    pole, banner = ink.flag(hand.x, hand.y, hand.z - 0.6, 5.2, 1.4, black, red, "flag_general")
    ink.keep_parent(pole, rider)
    ink.keep_parent(banner, rider)
    ink.wave_flags(flags + [banner])
    return {
        "main": ((-4, -24, 14), (2, 30, 12), 22),
        "closeup": ((-2, 4, 3.5), (4, 20, 1.6), 50),
        "horse": (Vector((7.5, 16.5, 2.4)) - side * 9, (7.5, 16.5, 2.2), 50),
    }


def city(cx, cy, w, d, mat):
    """远处一座城：四面城墙带垛口、四角角楼、城门楼、城里一片屋顶。"""
    h = 2.6
    for (x, y, sx, sy) in [(cx, cy - d / 2, w, 0.9), (cx, cy + d / 2, w, 0.9), (cx - w / 2, cy, 0.9, d), (cx + w / 2, cy, 0.9, d)]:
        ink.prop(bpy.ops.mesh.primitive_cube_add, mat, size=1, location=(x, y, h / 2), scale=(sx, sy, h))
        n = int(max(sx, sy) / 1.2)
        for k in range(n):
            u = (k + 0.5) / n - 0.5
            ink.prop(bpy.ops.mesh.primitive_cube_add, mat, size=0.55, location=(x + u * sx * (sx > 1), y + u * sy * (sy > 1), h + 0.25))
    for (x, y) in [(cx - w / 2, cy - d / 2), (cx + w / 2, cy - d / 2), (cx - w / 2, cy + d / 2), (cx + w / 2, cy + d / 2), (cx, cy - d / 2)]:
        big = x == cx
        ink.prop(bpy.ops.mesh.primitive_cube_add, mat, size=1, location=(x, y, h + 1.2), scale=(3.2 if big else 2.2, 2.2, 2.4))
        ink.prop(bpy.ops.mesh.primitive_cone_add, mat, vertices=4, radius1=2.9 if big else 2.1, depth=1.6, location=(x, y, h + 3.2), rotation=(0, 0, math.pi / 4))
    rnd = __import__("random").Random(7)
    for _ in range(40):
        x = cx + rnd.uniform(-0.42, 0.42) * w
        y = cy + rnd.uniform(-0.4, 0.4) * d
        s = rnd.uniform(1.2, 2.2)
        ink.prop(bpy.ops.mesh.primitive_cube_add, mat, size=1, location=(x, y, 0.5 + s * 0.3), scale=(s * 1.3, s, s * 0.6))
        ink.prop(bpy.ops.mesh.primitive_cone_add, mat, vertices=4, radius1=s * 1.05, depth=s * 0.6, location=(x, y, 0.5 + s * 0.9), rotation=(0, 0, math.pi / 4), scale=(1.25, 1, 1))


# ---- 望：远望。望晚日照城郭，汶水、徂徕如画，而半山居雾若带然（《登泰山记》） ----
def wang(scene):
    """山顶崖边一人远望：脚下平原上一座城，一条河，对面山腰横一条雾带，晚日落在远山上。"""
    landscape(mid=(460, 90, 210, 50, 70, 7), near=None, mist=False)
    ink.valley_mist(y=200, depth=300, height=14, density=0.012)
    ink.ground()
    black = ink.flat("ink", ink.INK)
    wall = ink.flat("wall", (0.12, 0.115, 0.11, 1))
    water = ink.flat("water", (0.42, 0.42, 0.41, 1))
    ink.ribbon("river", [(-90, 260), (-55, 200), (-35, 150), (-8, 100), (30, 80), (45, 55), (35, 35)], lambda u: 4 + u * 8, 1.3, water)
    city(14, 112, 40, 26, wall)
    ink.mist_band((0, 200), (600, 70), 16, 30, density=0.09)
    ink.sun((-30, 800, 95), 20)
    peak = ink.cliff("cliff", (-9, -5), 10, 13, 3, ink.rock("cliff", (0.05, 0.05, 0.045, 1), (0.42, 0.41, 0.38, 1), 6))
    x, y = -9, -3
    man, arm, strip = ink.character("man.glb", 1.75, "Man_Idle", black)
    strip.scale = 0.5  # 100 帧的站立呼吸压成 50 帧一圈
    strip.repeat = 20
    man.location = (x, y, ink.surface_z(peak, x, y))
    man.rotation_euler = (0, 0, ink.yaw_toward(Vector((0.3, 1, 0))))
    loc = man.location.copy()
    ink.prop(bpy.ops.mesh.primitive_cone_add, black, man, radius1=0.42, radius2=0.2, depth=1.0, location=loc + Vector((0, 0, 0.55)), vertices=16)
    head = ink.bone_point(arm, "Head", tail=True)
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, black, man, radius=0.1, depth=0.28, location=head + Vector((0, 0, 0.02)), vertices=10)
    ink.birds((0, 160, 0), 16, 42, 3, 0.9, black)
    return {
        "main": (loc + Vector((1, -10, 4)), (x + 6, 150, 0), 32),
        "man": (loc + Vector((6, -6, 3)), loc + Vector((0, 0, 1)), 50),
    }

def gazer(peak, x, y, toward, ink_mat, height=1.75, phase=0):
    """站在崖上的人：长袍、方巾，微微呼吸。返回（外层空物体，骨架）。"""
    man, arm, strip = ink.character("man.glb", height, "Man_Idle", ink_mat)
    strip.scale = 0.5  # 100 帧的站立呼吸压成 50 帧一圈
    strip.repeat = 20
    strip.frame_start = -phase
    man.location = (x, y, ink.surface_z(peak, x, y))
    man.rotation_euler = (0, 0, ink.yaw_toward(toward))
    loc = man.location.copy()
    k = height / 1.75
    ink.prop(bpy.ops.mesh.primitive_cone_add, ink_mat, man, radius1=0.42 * k, radius2=0.2 * k, depth=1.0 * k, location=loc + Vector((0, 0, 0.55 * k)), vertices=16)
    head = ink.bone_point(arm, "Head", tail=True)
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, ink_mat, man, radius=0.1 * k, depth=0.28 * k, location=head + Vector((0, 0, 0.02)), vertices=10)
    return man, arm


# ---- 负：背着。及既上，苍山负雪，明烛天南（《登泰山记》） ----
def fu(scene):
    """登上山顶，眼前一列青黑的群山背着白雪，雪光照亮南边的天；两人立在崖头看，云在山腰流。"""
    ink.mountains("far", 900, 140, 380, 120, 140, 4, ink.snowy(ink.rock("rock_far", (0.22, 0.25, 0.26, 1), (0.6, 0.62, 0.62, 1), 3), 30, 10, 0.35))
    ink.mountains("mid", 520, 100, 210, 85, 75, 9, ink.snowy(ink.rock("rock_mid", (0.05, 0.07, 0.07, 1), (0.36, 0.4, 0.4, 1), 5), 18, 8, 0.42))
    ink.valley_mist(y=200, depth=300, height=18, density=0.02)
    ink.mist_band((0, 150), (700, 60), 14, 26, density=0.07, drift=10)
    ink.paper_world(scene)
    ink.ground()
    black = ink.flat("ink", ink.INK)
    peak = ink.cliff("cliff", (-6, -5), 10, 13, 5, ink.snowy(ink.rock("cliff", (0.04, 0.05, 0.05, 1), (0.36, 0.39, 0.38, 1), 6), 11, 2, 0.75))
    a, _ = gazer(peak, -6.5, -2.5, Vector((0.25, 1, 0)), black)
    gazer(peak, -5.2, -2.9, Vector((0.1, 1, 0)), black, 1.68, 17)
    ink.birds((10, 120, 0), 14, 40, 3, 0.8, black)
    loc = a.location
    return {
        "main": (loc + Vector((1.5, -10, 4)), (loc.x + 6, 150, 14), 32),
    }

def scrolling_ground(period, mat, seed=3, span=4, rows=((-6, 40),)):
    """跑动场景的近地：草丛和石子按 period 米周期排布，每个循环整体后移一个周期，首尾无缝。"""
    rnd = __import__("random").Random(seed)
    scroll = bpy.data.objects.new("scroll", None)
    bpy.context.collection.objects.link(scroll)
    pattern = []
    for y0, y1 in rows:
        for _ in range(int(period * (y1 - y0) * 0.12)):
            pattern.append((rnd.uniform(0, period), rnd.uniform(y0, y1), rnd.random(), rnd.random()))
    for k in range(-span, span + 1):
        for (x, y, a, b) in pattern:
            loc = (x + k * period, y, 0)
            if a < 0.75:  # 一丛草：几根细锥
                for j in range(3):
                    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, scroll, vertices=4, radius1=0.05, depth=0.35 + 0.3 * b, location=(loc[0] + j * 0.08 - 0.08, y, 0.15), rotation=(0, (j - 1) * 0.35, 0))
            else:
                ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, mat, scroll, subdivisions=1, radius=0.12 + 0.25 * b, location=loc, scale=(1.4, 1, 0.6))

    def update(t):
        scroll.location.x = -period * t
    ink.on_frame(update)
    return scroll


def dust(points, mat_color, count=10):
    """马蹄扬起的尘：小团从蹄下往后飘、变大、变淡，按相位错开循环。"""
    puffs = []
    for k in range(count):
        m = bpy.data.materials.new(f"dust{k}")
        m.use_nodes = True
        m.surface_render_method = "BLENDED"
        bsdf = m.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Base Color"].default_value = mat_color
        bsdf.inputs["Roughness"].default_value = 1.0
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=0.3)
        o = bpy.context.active_object
        o.data.materials.append(m)
        o.visible_shadow = False
        puffs.append((o, bsdf, points[k % len(points)], k / count))

    def update(t):
        for o, bsdf, (x, y), ph in puffs:
            u = (t * 4 + ph) % 1  # 每步一团
            o.location = (x - 3.5 * u, y + 0.3 * math.sin(ph * 9), 0.2 + 0.6 * u)
            o.scale = (0.5 + 2.2 * u, 0.5 + 1.2 * u, 0.3 + 0.9 * u)
            bsdf.inputs["Alpha"].default_value = 0.22 * (1 - u) * min(u * 6, 1)
    ink.on_frame(update)


# ---- 乘：骑。乘纤离之马，建翠凤之旗（《谏逐客书》） ----
def cheng(scene):
    """一人骑骏马在原野上奔驰，手里一面翠绿的旗向后猎猎飘，蹄下扬尘。"""
    landscape(near=None)
    paper_ground = ink.flat("plain", (0.6, 0.58, 0.54, 1))
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 60, 0))
    bpy.context.active_object.scale = (400, 160, 1)
    bpy.context.active_object.data.materials.append(paper_ground)
    black = ink.flat("ink", ink.INK)
    green = ink.flat("green", (0.025, 0.15, 0.11, 1))
    scrolling_ground(16, black)
    heading = Vector((1, 0, 0))
    yaw = ink.yaw_toward(heading)
    horse, horse_arm, strip = ink.character("horse.glb", 2.5, "Gallop", black)
    horse.rotation_euler = (0, 0, yaw)
    strip.scale = 12.5 / 14  # 一步 12.5 帧，一个循环四步
    strip.repeat = 40
    rider, rider_arm, _ = ink.character("man.glb", 1.75, "Man_Sitting", black, frame=100)
    rider.rotation_euler = (0, 0, yaw)
    ink.mount(horse, horse_arm, rider, rider_arm)
    head = ink.bone_point(rider_arm, "Head", tail=True)
    ink.prop(bpy.ops.mesh.primitive_cone_add, black, rider, radius1=0.19, depth=0.3, location=head + Vector((0, 0, 0.05)), vertices=14)
    hand = ink.bone_point(rider_arm, "Hips") + heading * 0.3 + Vector((0, 0.35, 0))
    pole, banner = ink.flag(hand.x, hand.y, hand.z - 0.5, 4.2, 1.3, black, green, "flag_green")
    banner.location.x = 2 * hand.x - banner.location.x  # 旗面翻到旗杆后面，迎风向后飘
    banner.rotation_euler = (math.pi / 2, 0, math.pi)
    ink.keep_parent(pole, rider)
    ink.keep_parent(banner, rider)
    ink.wave_flags([banner])
    # 蹄下的尘（后蹄附近）。
    hips = horse.location
    dust([(hips.x - 1.0, -0.3), (hips.x - 0.8, 0.3), (hips.x + 0.6, 0.1)], (0.3, 0.29, 0.27, 1))
    return {
        "main": ((0.3, -15, 1.8), (0.3, 0, 3.4), 35),
    }

def boat(length, width, mat):
    """一叶小篷船：船身按截面放样，两头翘起，中间一个半圆篷。返回船（篷挂在船上）。"""
    bm = bmesh.new()
    n, m = 24, 8
    rings = []
    for i in range(n + 1):
        u = i / n
        w = width / 2 * math.sin(math.pi * u) ** 0.6 + 0.02
        sheer = 0.25 + 0.5 * (2 * u - 1) ** 4  # 船头船尾翘
        depth = 0.45 * math.sin(math.pi * u) ** 0.4 + 0.02
        ring = []
        for j in range(m + 1):
            a = math.pi * j / m
            ring.append(bm.verts.new(((u - 0.5) * length, -w * math.cos(a), sheer - depth * math.sin(a))))
        rings.append(ring)
    for i in range(n):
        for j in range(m):
            bm.faces.new((rings[i][j], rings[i + 1][j], rings[i + 1][j + 1], rings[i][j + 1]))
    mesh = bpy.data.meshes.new("boat")
    bm.to_mesh(mesh)
    hull = bpy.data.objects.new("boat", mesh)
    bpy.context.collection.objects.link(hull)
    mesh.materials.append(mat)
    hull.modifiers.new("solid", "SOLIDIFY").thickness = 0.04
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=width * 0.42, depth=length * 0.28, location=(-length * 0.08, 0, 0.3), rotation=(0, math.pi / 2, 0))
    canopy = bpy.context.active_object
    bm = bmesh.from_edit_mesh(canopy.data) if canopy.mode == "EDIT" else None
    canopy.data.materials.append(mat)
    canopy.scale = (1, 1, 1)
    # 只留上半个圆柱，作篷。
    me = canopy.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.x > 0.01 * width], context="VERTS")  # 圆柱绕 y 转了 90°，局部 x 是竖直方向
    bm.to_mesh(me)
    canopy.modifiers.new("solid", "SOLIDIFY").thickness = 0.03
    ink.keep_parent(canopy, hull)
    return hull


def reeds(cx, cy, count, mat, seed=5, spread=3.0, height=2.2):
    """一丛芦苇：秆、斜出的叶、下垂的淡色穗，各自慢慢摆（清风徐来）。"""
    rnd = __import__("random").Random(seed)
    plume = ink.flat("plume", (0.07, 0.065, 0.06, 1))
    for k in range(count):
        x, y = cx + rnd.gauss(0, spread), cy + rnd.gauss(0, spread * 0.4)
        h = height * rnd.uniform(0.5, 1.2)
        bpy.ops.object.empty_add(location=(x, y, 0))
        pivot = bpy.context.active_object
        pivot.rotation_euler = (rnd.uniform(-0.12, 0.12), rnd.uniform(-0.12, 0.12), 0)
        ink.prop(bpy.ops.mesh.primitive_cone_add, mat, pivot, vertices=4, radius1=0.018, depth=h, location=(x, y, h / 2))
        for j in range(rnd.randint(1, 3)):  # 叶：细长三角，斜着伸出去
            z = h * rnd.uniform(0.15, 0.6)
            a = rnd.uniform(0, 2 * math.pi)
            tilt = rnd.uniform(0.5, 1.0)
            L = h * rnd.uniform(0.35, 0.6)
            d = Vector((math.cos(a) * math.sin(tilt), math.sin(a) * math.sin(tilt), math.cos(tilt)))
            ink.prop(bpy.ops.mesh.primitive_cone_add, mat, pivot, vertices=3, radius1=0.035, depth=L, location=Vector((x, y, z)) + d * L / 2, rotation=d.to_track_quat("Z", "Y").to_euler(), scale=(1, 0.25, 1))
        if rnd.random() < 0.7:  # 穗：一束向一侧垂下
            ink.prop(bpy.ops.mesh.primitive_cone_add, plume, pivot, vertices=6, radius1=0.06, depth=0.45, location=(x + 0.08, y, h + 0.05), rotation=(0, 2.7, 0))
        ink.sway(pivot, "x", 0.06, rnd.random())


# ---- 徐：慢慢地。清风徐来，水波不兴（《赤壁赋》） ----
def xu(scene):
    """赤壁下一江静水，一叶篷船上三人坐着，微风让芦苇轻摆、船身轻轻起伏，水面几乎不起波纹。"""
    landscape(mid=(500, 90, 220, 50, 70, 11), near=None)
    scene.eevee.use_raytracing = True
    water = bpy.data.materials.new("water")
    water.use_nodes = True
    w = water.node_tree.nodes["Principled BSDF"]
    w.inputs["Base Color"].default_value = (0.35, 0.35, 0.34, 1)
    w.inputs["Roughness"].default_value = 0.08
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 150, 0.2))
    bpy.context.active_object.scale = (800, 400, 1)
    bpy.context.active_object.data.materials.append(water)
    black = ink.flat("ink", ink.INK)
    red_cliff = ink.rock("chibi", (0.07, 0.03, 0.02, 1), (0.4, 0.27, 0.21, 1), 9)
    ink.cliff("chibi", (-40, 70), 22, 46, 7, red_cliff, rough=0.5)
    ink.cliff("chibi2", (-52, 120), 26, 34, 2, red_cliff, rough=0.5)
    hull = boat(5.2, 1.5, black)
    hull.location = (2, 30, 0.2)
    hull.rotation_euler = (0, 0, math.radians(-18))
    for k, (dx, turn, ph) in enumerate([(-1.5, 0.4, 0), (1.0, -2.6, 23), (1.8, 1.4, 41)]):
        man, arm, _ = ink.character("man.glb", 1.75, "Man_Sitting", black, frame=100)
        man.rotation_euler = (0, 0, turn)
        bpy.context.view_layer.update()
        hips = arm.matrix_world @ arm.pose.bones["Hips"].head
        seat = hull.matrix_world @ Vector((dx, 0, 0.42))
        man.location += seat - hips
        ink.keep_parent(man, hull)
    ink.bob(hull, 0.05)
    ink.sway(hull, "y", 0.015, 0.25)
    reeds(6.2, 8, 40, black, spread=0.7, height=1.5)
    reeds(-1.2, 9, 12, black, seed=8, spread=0.4, height=1.1)
    return {
        "main": ((4, 2, 2.6), (1.5, 60, 6.5), 40),
    }

def pavilion(x, y, z, size, mat, base=None):
    """山顶一座小亭：台基、四根柱、栏杆、四角攒尖顶带翘檐、宝顶。"""
    h = size * 0.75
    ink.prop(bpy.ops.mesh.primitive_cube_add, base or mat, size=1, location=(x, y, z - 0.6), scale=(size * 1.15, size * 1.15, 1.8))  # 台基往下埋进山石，坡上不悬空
    for sx in (-1, 1):
        for sy in (-1, 1):
            ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, vertices=10, radius=0.09, depth=h, location=(x + sx * size / 2, y + sy * size / 2, z + 0.3 + h / 2))
    for (dx, dy, sx, sy) in [(0, -1, 1, 0), (0, 1, 1, 0), (-1, 0, 0, 1), (1, 0, 0, 1)]:  # 栏杆（前面留出口）
        if dy == -1:
            continue
        ink.prop(bpy.ops.mesh.primitive_cube_add, mat, size=1, location=(x + dx * size / 2, y + dy * size / 2, z + 0.85), scale=(size * sx + 0.06, size * sy + 0.06, 0.06))
    top = z + 0.3 + h
    ink.prop(bpy.ops.mesh.primitive_cube_add, mat, size=1, location=(x, y, top + 0.08), scale=(size * 1.05, size * 1.05, 0.16))
    # 屋顶：一个压扁的四棱锥做屋面，下面一个更宽更薄的做出檐，四角挑起。
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, vertices=4, radius1=size * 0.95, radius2=0.05, depth=size * 0.55, location=(x, y, top + 0.16 + size * 0.275), rotation=(0, 0, math.pi / 4))
    eave = ink.prop(bpy.ops.mesh.primitive_cone_add, mat, vertices=4, radius1=size * 1.2, radius2=size * 0.8, depth=0.18, location=(x, y, top + 0.2), rotation=(0, 0, math.pi / 4))
    for v in eave.data.vertices:  # 底圈四个角往上翘
        if v.co.z < 0:
            v.co.z += 0.25
    ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, mat, segments=10, ring_count=8, radius=0.14, location=(x, y, top + 0.16 + size * 0.56))
    return top


def cloud_sea(z, y0, y1, width, count, seed=11):
    """云海：一大片压扁的云团铺在山腰以下，向光的一面亮、背光的一面带灰，各自缓缓漂移起伏。"""
    rnd = __import__("random").Random(seed)
    mat = ink.flat("cloud", (0.93, 0.915, 0.88, 1))
    for k in range(count):
        y = rnd.uniform(y0, y1)
        r = rnd.uniform(6, 14) * (1 + (y - y0) / (y1 - y0))  # 远处的团大一些，近大远小不至于太悬殊
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=3, radius=r, location=(rnd.uniform(-width / 2, width / 2), y, z + rnd.uniform(-2, 2)))
        o = bpy.context.active_object
        o.scale = (rnd.uniform(1.2, 2.0), rnd.uniform(0.8, 1.2), rnd.uniform(0.25, 0.4))
        o.data.materials.append(mat)
        bpy.ops.object.shade_smooth()
        base = o.location.copy()
        ph = rnd.random()

        def update(t, o=o, base=base, ph=ph, r=r):
            a = 2 * math.pi * (t + ph)
            o.location = base + Vector((0.12 * r * math.sin(a), 0, 0.04 * r * math.sin(a + 1.3)))
        ink.on_frame(update)


# ---- 坐：坐着。戊申晦，五鼓，与子颍坐日观亭，待日出（《登泰山记》） ----
def zuo(scene):
    """天将亮，泰山顶的日观亭里两人并肩坐着，脚下云海流动，天边一线红，太阳还没出来。"""
    landscape(far=(900, 140, 380, 70, 130, 3), mid=None, near=None, mist=False)
    ink.valley_mist(y=250, depth=500, height=34, density=0.035)
    cloud_sea(28, 25, 420, 420, 220)
    black = ink.flat("ink", ink.INK)
    summit = ink.rock("summit", (0.05, 0.05, 0.045, 1), (0.42, 0.41, 0.38, 1), 6)
    peak = ink.cliff("taishan", (0, 0), 14, 44, 4, summit, rough=0.08)
    ground_z = ink.surface_z(peak, 0, 2)
    pavilion(0, 2, ground_z, 3.2, black, summit)
    sun = ink.sun((120, 700, 52), 22)
    glow = sun.data.materials[0].node_tree.nodes["Emission"].inputs["Strength"]
    # 天边一线红：很远处一条扁的发光带，随循环微微呼吸。
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 760, 34), rotation=(math.pi / 2, 0, 0))
    band = bpy.context.active_object
    band.scale = (2400, 10, 1)
    band.data.materials.append(ink.emissive("dawn", (0.85, 0.42, 0.3, 1), 0.5))
    band.visible_shadow = False
    dawn = band.data.materials[0].node_tree.nodes["Emission"].inputs["Strength"]

    def breathe(t):
        dawn.default_value = 0.45 + 0.1 * math.sin(2 * math.pi * t)
        glow.default_value = 0.9 + 0.1 * math.sin(2 * math.pi * t)
    ink.on_frame(breathe)
    bench = ink.prop(bpy.ops.mesh.primitive_cube_add, black, size=1, location=(0, 2.6, ground_z + 0.3 + 0.2), scale=(2.0, 0.35, 0.4))
    seat_top = ground_z + 0.3 + 0.4
    for dx, ph in ((-0.45, 0), (0.45, 37)):
        man, arm, _ = ink.character("man.glb", 1.75, "Man_Sitting", black, frame=100)
        man.rotation_euler = (0, 0, ink.yaw_toward(Vector((0.45, 1, 0))))  # 侧过一点，露出坐姿
        bpy.context.view_layer.update()
        hips = arm.matrix_world @ arm.pose.bones["Hips"].head
        man.location += Vector((dx, 2.55, seat_top + 0.08)) - hips
        head = ink.bone_point(arm, "Head", tail=True)
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, black, man, radius=0.1, depth=0.28, location=head + Vector((0, 0, 0.02)), vertices=10)
    ink.birds((6, 45, 0), 9, ground_z + 5, 3, 0.45, black)
    return {
        "main": ((0.6, -14, ground_z + 3), (2, 200, ground_z - 3), 30),
    }

SCENES = {
    "gk-shi-078": lv,     # 率
    "gk-shi-087": wang,   # 望
    "gk-shi-024": fu,     # 负
    "gk-shi-012": cheng,  # 乘
    "gk-shi-098": xu,     # 徐
    "gk-shi-120": zuo,    # 坐
}
