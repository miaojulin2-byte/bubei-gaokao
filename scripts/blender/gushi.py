"""古诗文首页的名句画：每句一幅 2 秒循环的设色三维画。

和文言的宣纸水墨不同，这里是有天色的设色画：黄昏、月夜、春雨、雪夜、清晨，天空偏深，
首页的白字压在上面看得清。景物沿用 ink / props / words 里的部件，只换材质和天色。
卡片 id 是 gushi-01 ~ gushi-20，对应 app 的 assets/scenes.json。
"""
import math
import random

import bpy
from mathutils import Vector

import ink
import props
import words


def sky(scene, top, horizon, strength=1.0, sun_color=(1, 0.95, 0.9, 1), sun_energy=2.0, sun_rot=(55, 0, -40)):
    """天色：地平线 horizon 到天顶 top 渐变，再加一盏平行光。"""
    world = bpy.data.worlds.new("sky")
    scene.world = world
    world.use_nodes = True
    nt = world.node_tree
    coord = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(coord.outputs["Generated"], sep.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = horizon
    ramp.color_ramp.elements[1].position = 0.45
    ramp.color_ramp.elements[1].color = top
    nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    bg = nt.nodes["Background"]
    bg.inputs["Strength"].default_value = strength
    nt.links.new(ramp.outputs["Color"], bg.inputs["Color"])
    bpy.ops.object.light_add(type="SUN", location=(0, 0, 50))
    sun = bpy.context.active_object
    sun.data.energy = sun_energy
    sun.data.color = sun_color[:3]
    sun.data.angle = math.radians(10)
    sun.rotation_euler = tuple(math.radians(a) for a in sun_rot)
    return sun


def mat(name, color, rough=0.9, emission=0.0):
    m = ink.flat(name, color, emission)
    m.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = rough
    return m


def moon(location, radius, color=(1, 0.95, 0.82, 1), strength=3.0):
    """月亮：发光的圆盘。"""
    m = ink.sun(location, radius, color)
    m.data.materials[0].node_tree.nodes["Emission"].inputs["Strength"].default_value = strength
    return m


def fall(center, size, top, count, make, cycles=1, spin=2.0, sway=1.5, seed=3):
    """落叶、雪花：make() 造一片，从 top 飘落到地面，边落边转、左右摆；落到底从顶上重来，首尾无缝。"""
    rnd = random.Random(seed)
    flakes = []
    for _ in range(count):
        o = make(rnd)
        o.visible_shadow = False
        flakes.append((o, center[0] + rnd.uniform(-0.5, 0.5) * size[0], center[1] + rnd.uniform(-0.5, 0.5) * size[1], rnd.random(), rnd.uniform(0.6, 1.4)))

    def update(t):
        for o, x, y, ph, k in flakes:
            u = (t * cycles + ph) % 1
            a = 2 * math.pi * (t * cycles + ph)
            o.location = (x + sway * math.sin(a * 2) * k, y, top * (1 - u))
            o.rotation_euler = (a * spin, a * spin * 0.7 * k, a)
    ink.on_frame(update)


def smoke(x, y, z, height, count=8, color=(0.5, 0.48, 0.52, 1), seed=1):
    """炊烟：一团团从烟囱口升起、变大、变淡，按相位错开循环。"""
    rnd = random.Random(seed)
    puffs = []
    for k in range(count):
        m = bpy.data.materials.new(f"smoke{seed}_{k}")
        m.use_nodes = True
        m.surface_render_method = "BLENDED"
        b = m.node_tree.nodes["Principled BSDF"]
        b.inputs["Base Color"].default_value = color
        b.inputs["Roughness"].default_value = 1.0
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=0.5)
        o = bpy.context.active_object
        o.data.materials.append(m)
        o.visible_shadow = False
        puffs.append((o, b, k / count, rnd.uniform(-1, 1)))

    def update(t):
        for o, b, ph, side in puffs:
            u = (t + ph) % 1
            o.location = (x + u * height * 0.35 + side * 0.3 * u * height * 0.1, y, z + u * height)  # 依依：微微被风带向一边
            o.scale = [0.6 + u * height * 0.16] * 3
            b.inputs["Alpha"].default_value = 0.6 * (1 - u) * min(u * 8, 1)
    ink.on_frame(update)


def house(x, y, z, w, d, h, wall, roof, yaw=0.0, window=None):
    """一间房：墙、两坡屋顶，可选一扇亮着的窗。"""
    bpy.ops.object.empty_add(location=(x, y, z))
    root = bpy.context.active_object
    ink.prop(bpy.ops.mesh.primitive_cube_add, wall, root, size=1, location=(x, y, z + h / 2), scale=(w, d, h))
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, roof, root, vertices=3, radius=d * 0.62, depth=w * 1.15, location=(x, y, z + h + d * 0.3), rotation=(math.pi / 2, 0, math.pi / 2))  # 三棱柱的第一个顶点在 +Y：绕 X 转 90° 让尖朝上，再绕 Z 转到沿房子宽度
    if window is not None:
        ink.prop(bpy.ops.mesh.primitive_cube_add, window, root, size=1, location=(x, y - d / 2 - 0.02, z + h * 0.55), scale=(w * 0.25, 0.04, h * 0.3))
    root.rotation_euler = (0, 0, yaw)
    return root


def tree(x, y, z, h, trunk, leaves, seed=0, clumps=9, spread=0.35, wind=0.02, size=1.0):
    """一棵阔叶树：干加一团团叶冠（枫、桑、杏花都用它，换叶色）。"""
    rnd = random.Random(seed)
    bpy.ops.object.empty_add(location=(x, y, z))
    pivot = bpy.context.active_object
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, trunk, pivot, vertices=8, radius=h * 0.04, depth=h * 0.6, location=(x, y, z + h * 0.3))
    for k in range(clumps):
        a = rnd.uniform(0, 2 * math.pi)
        r = h * spread * rnd.random() ** 0.5
        c = Vector((x + math.cos(a) * r, y + math.sin(a) * r * 0.8, z + h * rnd.uniform(0.55, 0.95)))
        ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, leaves, pivot, subdivisions=2, radius=h * rnd.uniform(0.14, 0.22) * size, location=c, scale=(1.2, 1.1, 0.85))
    ink.sway(pivot, "x", wind, rnd.random())
    return pivot


def water(color, y=150, size=(1200, 600), rough=0.06, z=0.2):
    w = props.water(y=y, size=size, color=color, z=z)
    w.data.materials[0].node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = rough
    return w


def warship(x, y, length, hull_mat, deck_mat, flag_mat, yaw=0.0, lanterns=None, flags=2, name="ship"):
    """楼船：船身、两三层船楼、桅杆和旗，可选挂灯笼。返回（船，旗面列表）。"""
    hull = words.boat(length, length * 0.28, hull_mat)
    hull.location = (x, y, 0.2)
    bpy.context.view_layer.update()
    m = hull.matrix_world
    for k, (w, h) in enumerate([(0.5, 0.9), (0.36, 0.8), (0.22, 0.7)]):
        z = 0.45 + sum(hh for _, hh in [(0.5, 0.9), (0.36, 0.8), (0.22, 0.7)][:k]) * length * 0.12
        ink.prop(bpy.ops.mesh.primitive_cube_add, deck_mat, hull, size=1, location=m @ Vector((-length * 0.05, 0, z + h * length * 0.06)), scale=(length * w, length * 0.2 * (1 - k * 0.15), h * length * 0.12))
        if lanterns is not None:
            for s in (-1, 1):
                ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, lanterns, hull, segments=10, ring_count=6, radius=length * 0.018, location=m @ Vector((s * length * w * 0.45, -length * 0.11, z + h * length * 0.03)))
    banners = []
    for k in range(flags):
        fx = (k - (flags - 1) / 2) * length * 0.3
        p = m @ Vector((fx, 0, 0.5))
        pole, f = ink.flag(p.x, p.y, p.z, length * 0.55, length * 0.12, deck_mat, flag_mat, f"{name}_flag{k}")
        ink.keep_parent(pole, hull)
        ink.keep_parent(f, hull)
        banners.append(f)
    hull.rotation_euler = (0, 0, yaw)
    return hull, banners


# ---- 01 登高：无边落木萧萧下，不尽长江滚滚来 ----
def denggao(scene):
    """秋天傍晚，高崖上一人独立，崖边树叶纷纷飘落，脚下长江在群山间滚滚东去。"""
    sky(scene, (0.16, 0.13, 0.2, 1), (0.85, 0.52, 0.3, 1), 1.0, (1, 0.7, 0.45, 1), 2.5, (75, 0, 40))
    ink.mountains("far", 900, 140, 420, 120, 140, 2, ink.rock("far", (0.18, 0.13, 0.16, 1), (0.42, 0.3, 0.3, 1), 3))
    ink.mountains("mid", 520, 100, 200, 80, 75, 6, ink.rock("mid", (0.12, 0.08, 0.06, 1), (0.42, 0.26, 0.15, 1), 5))
    ink.valley_mist(y=250, depth=350, height=30, density=0.015, color=(0.9, 0.6, 0.4, 1))
    water((0.25, 0.2, 0.22, 1), y=150, z=-0.5)
    rock = ink.rock("cliff", (0.08, 0.06, 0.05, 1), (0.35, 0.25, 0.18, 1), 6)
    peak = ink.cliff("cliff", (-6, -4), 11, 40, 4, rock, rough=0.06)
    dark = mat("figure", (0.03, 0.025, 0.03, 1))
    x, y = -6, -3  # 站在崖顶，镜头在崖外身后
    man, arm, _ = props.person(x, y, ink.surface_z(peak, x, y), Vector((0.5, 1, 0)), dark)
    props.robe(man, arm, dark)
    gz = man.location.z
    autumn = mat("autumn", (0.62, 0.22, 0.05, 1))
    for k, (tx, ty) in enumerate([(-10, 4), (-13, 7), (3, 6)]):
        tree(tx, ty, ink.surface_z(peak, tx, ty) - 0.3, 4 + k, mat("bark", (0.08, 0.05, 0.04, 1)), autumn, seed=k, clumps=7)

    def leaf(rnd):
        c = [(0.7, 0.3, 0.05, 1), (0.55, 0.15, 0.04, 1), (0.8, 0.55, 0.1, 1)][rnd.randrange(3)]
        bpy.ops.mesh.primitive_plane_add(size=0.3)
        o = bpy.context.active_object
        o.data.materials.append(mat(f"leaf{rnd.random()}", c))
        return o
    fall((x + 3, y + 6, 0), (16, 10), gz + 9, 140, leaf, cycles=1, seed=5)
    ink.birds((20, 120, 0), 14, 45, 3, 0.8, dark)
    return {"main": (man.location + Vector((-1.5, -14, 3.5)), (x + 10, 160, gz - 12), 30)}


# ---- 02 书愤：楼船夜雪瓜洲渡，铁马秋风大散关 ----
def shufen(scene):
    """雪夜江上的渡口，几艘高大的楼船挂着灯，大雪纷纷落在江面和船楼上。"""
    sky(scene, (0.03, 0.04, 0.08, 1), (0.16, 0.18, 0.26, 1), 1.0, (0.6, 0.7, 1, 1), 0.8, (60, 0, 30))
    ink.mountains("far", 900, 140, 380, 70, 140, 3, ink.snowy(ink.rock("far", (0.08, 0.09, 0.12, 1), (0.25, 0.27, 0.32, 1), 3), 25, 6, 0.3))
    water((0.05, 0.06, 0.09, 1))
    ink.valley_mist(y=200, depth=300, height=18, density=0.02, color=(0.4, 0.45, 0.6, 1))
    hull_m = mat("hull", (0.06, 0.05, 0.05, 1))
    deck_m = mat("deck", (0.14, 0.11, 0.1, 1))
    lamp = mat("lamp", (1, 0.55, 0.2, 1), emission=6)
    red = mat("flag", (0.5, 0.06, 0.04, 1))
    banners = []
    for k, (x, y, L, yaw) in enumerate([(-6, 34, 16, 0.2), (14, 60, 18, -0.15), (-22, 80, 14, 0.1)]):
        _, b = warship(x, y, L, hull_m, deck_m, red, yaw, lanterns=lamp, name=f"ship{k}")
        banners += b
    ink.wave_flags(banners)
    snow_m = mat("snow", (0.9, 0.92, 1, 1), emission=0.4)

    def flake(rnd):
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=0.07)
        o = bpy.context.active_object
        o.data.materials.append(snow_m)
        return o
    fall((0, 30), (40, 50), 22, 700, flake, cycles=1, spin=0, sway=0.6)
    return {"main": ((2, 0, 4), (-2, 80, 9), 32)}


# ---- 03 苏幕遮：叶上初阳干宿雨、水面清圆，一一风荷举 ----
def suzhemu(scene):
    """清晨的荷塘，阳光照在圆圆的荷叶上，一枝枝荷叶挺出水面在风里轻摇，几朵荷花。"""
    sky(scene, (0.25, 0.35, 0.45, 1), (0.95, 0.8, 0.55, 1), 1.0, (1, 0.85, 0.6, 1), 3.0, (78, 0, 160))
    ink.mountains("far", 900, 140, 380, 50, 140, 3, ink.rock("far", (0.3, 0.38, 0.4, 1), (0.55, 0.62, 0.62, 1), 3))
    ink.valley_mist(y=200, depth=300, height=12, density=0.03, color=(1, 0.9, 0.75, 1))
    water((0.12, 0.18, 0.16, 1))
    leaf_m = mat("lotus_leaf", (0.12, 0.38, 0.12, 1), rough=0.5)
    stem_m = mat("stem", (0.15, 0.3, 0.1, 1))
    petal = mat("petal", (0.95, 0.55, 0.65, 1), rough=0.6)
    rnd = random.Random(2)
    for k in range(170):
        y = 3 + 40 * rnd.random() ** 1.6  # 近处密
        x = rnd.uniform(-0.5, 0.5) * (4 + y * 0.9)
        h = rnd.uniform(0.3, 1.6)
        bpy.ops.object.empty_add(location=(x, y, 0.2))
        pivot = bpy.context.active_object
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, stem_m, pivot, vertices=6, radius=0.02, depth=h, location=(x, y, 0.2 + h / 2))
        r = rnd.uniform(0.45, 0.8)
        leaf = ink.prop(bpy.ops.mesh.primitive_cone_add, leaf_m, pivot, vertices=28, radius1=r, radius2=0.03, depth=0.12, location=(x, y, 0.2 + h), rotation=(math.pi + rnd.uniform(-0.3, 0.3), rnd.uniform(-0.3, 0.3), 0))
        if rnd.random() < 0.12:
            for j in range(6):
                a = j / 6 * 2 * math.pi
                ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, petal, pivot, segments=10, ring_count=6, radius=0.12, location=(x + math.cos(a) * 0.08, y + math.sin(a) * 0.08, 0.35 + h), scale=(0.6, 0.6, 1.4), rotation=(math.sin(a) * 0.5, -math.cos(a) * 0.5, 0))
        ink.sway(pivot, "x", 0.05 * h, rnd.random())
    return {"main": ((0, -2, 2.4), (0, 60, 1.5), 32)}


# ---- 04 归园田居：暧暧远人村，依依墟里烟 ----
def yuantian(scene):
    """黄昏，田野那头一个小村子模模糊糊，几缕炊烟袅袅升起；近处是田埂和柳。"""
    sky(scene, (0.2, 0.22, 0.32, 1), (0.95, 0.68, 0.45, 1), 1.0, (1, 0.75, 0.5, 1), 2.0, (80, 0, 20))
    ink.mountains("far", 900, 140, 380, 60, 140, 3, ink.rock("far", (0.25, 0.25, 0.32, 1), (0.5, 0.48, 0.55, 1), 3))
    ink.valley_mist(y=150, depth=250, height=10, density=0.03, color=(0.95, 0.75, 0.6, 1))
    field = ink.rock("field", (0.2, 0.25, 0.12, 1), (0.42, 0.45, 0.25, 1), 0, per_meter=0.08)
    ink.ground(mat=field)
    wall = mat("wall", (0.55, 0.48, 0.4, 1))
    roof = mat("thatch", (0.25, 0.2, 0.15, 1))
    lamp = mat("window", (1, 0.6, 0.25, 1), emission=4)
    rnd = random.Random(4)
    chimneys = []
    for k in range(9):
        x, y = rnd.uniform(-16, 16), rnd.uniform(70, 90)
        house(x, y, 0.8, rnd.uniform(3, 5), rnd.uniform(2.5, 3.5), rnd.uniform(2, 2.6), wall, roof, rnd.uniform(-0.3, 0.3), lamp if k % 3 == 0 else None)
        if k % 3 == 1:
            chimneys.append((x, y))
    for k, (x, y) in enumerate(chimneys):
        smoke(x, y, 4.5, 14, seed=k)
    dark = mat("dark", (0.05, 0.05, 0.05, 1))
    for k, (x, y) in enumerate([(-14, 64), (-9, 70), (12, 66), (18, 74)]):
        props.willow(x, y, 0.8, 7, dark, seed=k, strands=40, wind=0.05)
    for k in range(6):  # 田埂
        ink.prop(bpy.ops.mesh.primitive_cube_add, mat("ridge", (0.3, 0.32, 0.18, 1)), None, size=1, location=(0, 12 + k * 9, 0.9), scale=(60, 0.5, 0.4))
    return {"main": ((0, -6, 4), (0, 80, 6), 35)}


# ---- 05 蜀道难：连峰去天不盈尺，枯松倒挂倚绝壁 ----
def shudao(scene):
    """仰望连绵的绝壁直插云天，崖上一株枯松倒挂，云从峰间流过。"""
    sky(scene, (0.12, 0.16, 0.24, 1), (0.6, 0.66, 0.7, 1), 1.0, (0.9, 0.9, 1, 1), 2.0, (40, 0, -60))
    rock = ink.rock("cliffs", (0.06, 0.07, 0.08, 1), (0.38, 0.4, 0.4, 1), 4)
    for k, (x, y, r, h) in enumerate([(-22, 50, 14, 120), (18, 70, 16, 140), (-4, 110, 18, 160), (30, 130, 14, 110), (-36, 120, 16, 130)]):
        c = ink.cliff(f"spire{k}", (x, y), r, h, k + 3, rock, res=90, rough=0.35, peaked=True)
        c.scale = (0.6, 0.6, 1)
    ink.mist_band((0, 90), (300, 120), 40, 70, density=0.05, drift=20, color=(0.8, 0.82, 0.86, 1))
    ink.mist_band((0, 110), (300, 120), 90, 115, density=0.04, drift=-20, color=(0.8, 0.82, 0.86, 1))
    dead = mat("deadpine", (0.06, 0.05, 0.05, 1))
    # 倒挂的枯松：从左边绝壁上斜伸出来，冠朝下。
    p = props.pine(-12, 46, 48, 9, dead, seed=4)
    p.rotation_euler = (0, math.radians(130), 0)
    ink.sway(p, "y", 0.015)
    ink.birds((0, 80, 0), 10, 70, 2, 1.0, dead)
    return {"main": ((0, -10, 4), (-2, 80, 75), 26)}


# ---- 06 蜀道难：飞湍瀑流争喧豗，砯崖转石万壑雷 ----
def feituan(scene):
    """峡谷两壁之间几道瀑布飞泻而下，撞在乱石上溅起水雾。"""
    sky(scene, (0.1, 0.14, 0.18, 1), (0.5, 0.58, 0.6, 1), 1.0, (0.9, 0.95, 1, 1), 2.0, (50, 0, -30))
    rock = ink.rock("gorge", (0.05, 0.06, 0.06, 1), (0.35, 0.37, 0.36, 1), 5)
    ink.cliff("back", (0, 105), 30, 95, 9, rock, res=120, rough=0.3)  # 正面一堵大崖，瀑布从崖头泻下
    for k, (x, y, r, h) in enumerate([(-30, 50, 14, 80), (30, 56, 14, 90)]):  # 两侧夹峙
        ink.cliff(f"side{k}", (x, y), r, h, k + 3, rock, res=90, rough=0.35, peaked=True)
    white = mat("fall", (0.85, 0.9, 0.92, 1), emission=0.3)
    for k, (x, top, w) in enumerate([(-9, 60, 3.5), (3, 75, 5), (12, 55, 3)]):
        props.rain((x, 76), (w, 1.5), top, 260, white, slant=0.0, radius=0.14, length=8, cycles=2, seed=k)
    water((0.12, 0.16, 0.16, 1), y=60, size=(200, 100), z=0.1)
    ink.valley_mist(y=70, depth=30, height=12, density=0.06, color=(0.85, 0.9, 0.92, 1))
    for k in range(25):
        rnd = random.Random(k)
        ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, rock, None, subdivisions=2, radius=rnd.uniform(0.8, 2.5), location=(rnd.uniform(-16, 16), rnd.uniform(40, 70), 0), scale=(1.3, 1, 0.7))
    return {"main": ((0, 8, 8), (0, 80, 38), 28)}


# ---- 07 春江花月夜：江天一色无纤尘，皎皎空中孤月轮 ----
def chunjiang(scene):
    """春夜，江水和天连成一片没有一点尘埃，一轮明月高悬，月光铺在江面上；岸边开着花的树。"""
    sky(scene, (0.02, 0.04, 0.1, 1), (0.12, 0.18, 0.3, 1), 1.0, (0.7, 0.8, 1, 1), 0.6, (35, 0, 180))
    moon((0, 600, 160), 18)
    water((0.04, 0.06, 0.1, 1), rough=0.03)
    ink.mountains("far", 900, 140, 420, 25, 140, 3, ink.rock("far", (0.05, 0.06, 0.1, 1), (0.12, 0.14, 0.2, 1), 3))
    ink.mist_band((0, 300), (900, 300), 0, 12, density=0.03, drift=30, color=(0.5, 0.6, 0.8, 1))
    blossom = mat("blossom", (0.85, 0.75, 0.85, 1), emission=0.15)
    trunk = mat("trunk", (0.04, 0.04, 0.05, 1))
    bank = mat("bank", (0.04, 0.05, 0.06, 1))
    bpy.ops.mesh.primitive_plane_add(size=1, location=(-14, 8, 0.4))
    bpy.context.active_object.scale = (16, 16, 1)
    bpy.context.active_object.data.materials.append(bank)
    for k, (x, y) in enumerate([(-9, 10), (-12, 14)]):
        tree(x, y, 0.4, 6 - k, trunk, blossom, seed=k + 3, clumps=40, wind=0.015, size=0.4)

    def petal(rnd):  # 镜头前飘过几片落花
        bpy.ops.mesh.primitive_plane_add(size=0.07)
        o = bpy.context.active_object
        o.data.materials.append(blossom)
        return o
    fall((0, 2), (5, 3), 5, 40, petal, cycles=1, spin=1.5, sway=0.8, seed=7)
    dark = mat("boat", (0.02, 0.025, 0.04, 1))
    hull = words.boat(3.5, 1.0, dark)  # 谁家今夜扁舟子
    hull.location = (6, 90, 0.2)
    hull.rotation_euler = (0, 0, 0.3)
    man, arm, _ = props.person(0, 0, 0, Vector((1, 1, 0)), dark, action="Man_Sitting", frame=100)
    props.seat(man, arm, hull.matrix_world @ Vector((0.4, 0, 0.42)))
    ink.keep_parent(man, hull)
    ink.bob(hull, 0.08)
    ink.sway(hull, "x", 0.04, 0.3)
    return {"main": ((-1, -6, 3), (0, 400, 40), 30)}


# ---- 08 临安春雨初霁：小楼一夜听春雨，深巷明朝卖杏花 ----
def xiaolou(scene):
    """春夜细雨，深巷里一座小楼亮着灯，雨丝斜斜落下，墙角一树杏花。"""
    sky(scene, (0.04, 0.05, 0.08, 1), (0.16, 0.18, 0.22, 1), 1.0, (0.7, 0.8, 1, 1), 0.5)
    wall = mat("wall", (0.42, 0.42, 0.44, 1))
    roof = mat("tile", (0.06, 0.06, 0.07, 1))
    lamp = mat("window", (1, 0.65, 0.3, 1), emission=6)
    paving = mat("paving", (0.12, 0.12, 0.13, 1), rough=0.2)
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 30, 0))
    bpy.context.active_object.scale = (60, 80, 1)
    bpy.context.active_object.data.materials.append(paving)
    for k, y in enumerate([8, 16, 24, 32, 40]):  # 巷子两边的房子
        for side in (-1, 1):
            house(side * 4.2, y, 0, 3.4, 7, 3.2 + (k % 2) * 0.8, wall, roof, math.pi / 2 * side, None)
    # 小楼：两层，二楼窗亮着。
    ink.prop(bpy.ops.mesh.primitive_cube_add, wall, None, size=1, location=(2.8, 6, 3), scale=(3, 4, 6))
    ink.prop(bpy.ops.mesh.primitive_cube_add, lamp, None, size=1, location=(1.28, 6, 4.4), scale=(0.04, 1.6, 1.1))
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, roof, None, vertices=3, radius=2.6, depth=4.6, location=(2.8, 6, 6.9), rotation=(math.pi / 2, 0, 0))
    bpy.ops.object.light_add(type="POINT", location=(1.0, 6, 4.4))
    bpy.context.active_object.data.energy = 300
    bpy.context.active_object.data.color = (1, 0.6, 0.3)
    tree(-1.4, 12, 0, 5, mat("trunk", (0.05, 0.04, 0.04, 1)), mat("apricot", (0.95, 0.75, 0.75, 1), emission=0.1), seed=2, clumps=40, size=0.4)
    props.rain((0, 14), (14, 26), 14, 700, mat("rain", (0.6, 0.65, 0.75, 1), emission=0.2), slant=0.15, radius=0.012, length=1.0)
    return {"main": ((-0.6, -4, 1.7), (0.3, 30, 3.5), 30)}


# ---- 09 赤壁赋：纵一苇之所如，凌万顷之茫然 ----
def yiwei(scene):
    """月下茫茫一片江水，一叶小舟随意漂去，四周万顷烟波。"""
    sky(scene, (0.03, 0.05, 0.1, 1), (0.25, 0.3, 0.38, 1), 1.0, (0.7, 0.8, 1, 1), 0.6, (30, 0, 160))
    moon((-60, 600, 90), 12)
    water((0.05, 0.07, 0.1, 1), rough=0.04)
    ink.mist_band((0, 200), (900, 400), 0, 10, density=0.06, drift=30, color=(0.55, 0.62, 0.75, 1))
    dark = mat("dark", (0.02, 0.02, 0.03, 1))
    hull = words.boat(4, 1.2, dark)
    hull.location = (2, 40, 0.2)
    hull.rotation_euler = (0, 0, 0.5)
    for dx in (-0.6, 0.8):
        man, arm, _ = props.person(0, 0, 0, Vector((1, 1, 0)), dark, action="Man_Sitting", frame=100)
        props.seat(man, arm, hull.matrix_world @ Vector((dx, 0, 0.42)))
        ink.keep_parent(man, hull)
    ink.bob(hull, 0.06)
    ink.sway(hull, "x", 0.03, 0.3)
    return {"main": ((0, 0, 5), (0, 200, 10), 30)}


# ---- 10 永遇乐：想当年，金戈铁马，气吞万里如虎 ----
def tiema(scene):
    """夕阳下一队铁骑持戈纵马奔驰，旌旗猎猎，尘土飞扬。"""
    sky(scene, (0.18, 0.1, 0.1, 1), (0.95, 0.45, 0.2, 1), 1.0, (1, 0.6, 0.35, 1), 3.0, (82, 0, -90))
    ink.mountains("far", 900, 140, 380, 60, 140, 3, ink.rock("far", (0.18, 0.1, 0.1, 1), (0.4, 0.22, 0.18, 1), 3))
    ink.valley_mist(y=200, depth=300, height=14, density=0.02, color=(0.95, 0.55, 0.35, 1))
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 60, 0))
    bpy.context.active_object.scale = (400, 160, 1)
    bpy.context.active_object.data.materials.append(mat("plain", (0.35, 0.22, 0.14, 1)))
    dark = mat("rider", (0.04, 0.03, 0.03, 1))
    gold = mat("gold", (0.8, 0.55, 0.2, 1), rough=0.3)
    red = mat("banner", (0.55, 0.07, 0.04, 1))
    words.scrolling_ground(16, dark, rows=((-6, 30),), span=6)
    banners = []
    rnd = random.Random(3)
    heading = Vector((1, 0, 0))
    for k, (x, y) in enumerate([(0, 0), (-4, 2.5), (-7.5, -0.5), (-3, 5.5), (-10, 4), (3.5, 4)]):
        horse, harm, strip = ink.character("horse.glb", 2.5, "Gallop", dark)
        horse.location = (x, y, 0)
        horse.rotation_euler = (0, 0, ink.yaw_toward(heading))
        strip.scale = 12.5 / 14
        strip.repeat = 40
        strip.frame_start = -rnd.randrange(12)
        rider, rarm, _ = ink.character("man.glb", 1.75, "Man_Sitting", dark, frame=100)
        rider.rotation_euler = (0, 0, ink.yaw_toward(heading))
        ink.mount(horse, harm, rider, rarm)
        head = ink.bone_point(rarm, "Head", tail=True)
        ink.prop(bpy.ops.mesh.primitive_cone_add, gold, rider, radius1=0.2, depth=0.35, location=head + Vector((0, 0, 0.08)), vertices=12)
        hand = ink.bone_point(rarm, "Hips") + Vector((0.3, 0.35, 0))
        d = Vector((0.5, 0, 1)).normalized()
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, dark, rider, radius=0.035, depth=3.2, location=hand + d * 1.2, rotation=d.to_track_quat("Z", "Y").to_euler(), vertices=6)
        ink.prop(bpy.ops.mesh.primitive_cone_add, gold, rider, radius1=0.08, depth=0.4, location=hand + d * 2.9, rotation=d.to_track_quat("Z", "Y").to_euler(), vertices=6)
        if k % 2 == 0:
            pole, f = ink.flag(hand.x - 0.4, hand.y, hand.z - 0.4, 4.0, 1.1, dark, red, f"cav_flag{k}")
            f.location.x = 2 * (hand.x - 0.4) - f.location.x
            f.rotation_euler = (math.pi / 2, 0, math.pi)
            ink.keep_parent(pole, rider)
            ink.keep_parent(f, rider)
            banners.append(f)
    ink.wave_flags(banners)
    words.dust([(x - 1, y) for x, y in [(0, 0), (-4, 2.5), (-7.5, -0.5), (-10, 4)]], (0.55, 0.35, 0.22, 1), count=16)
    return {"main": ((-2, -16, 2.2), (-2.5, 0, 3.6), 32)}


# ---- 11 客至：舍南舍北皆春水，但见群鸥日日来 ----
def kezhi(scene):
    """春水漫到草堂前后，一群白鸥在水面上空盘旋。"""
    sky(scene, (0.2, 0.32, 0.45, 1), (0.85, 0.85, 0.78, 1), 1.0, (1, 0.95, 0.85, 1), 2.5, (60, 0, -40))
    ink.mountains("far", 900, 140, 380, 50, 140, 3, ink.rock("far", (0.2, 0.3, 0.3, 1), (0.45, 0.55, 0.5, 1), 3))
    water((0.25, 0.35, 0.38, 1), rough=0.08)
    ink.valley_mist(y=200, depth=300, height=10, density=0.02, color=(0.9, 0.92, 0.88, 1))
    isle = mat("isle", (0.2, 0.3, 0.12, 1))
    ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, isle, None, radius=8, location=(2, 30, -2.5), scale=(1.4, 1, 0.4))
    house(1, 30, 0.6, 4, 3, 2.0, mat("wall", (0.6, 0.55, 0.45, 1)), mat("thatch", (0.3, 0.25, 0.15, 1)), yaw=0.6)
    trunk = mat("trunk", (0.08, 0.07, 0.05, 1))
    for k, (x, y) in enumerate([(-5, 32), (7, 33)]):
        props.willow(x, y, 0.6, 6, trunk, seed=k + 2, strands=40, wind=0.05)
    gull = mat("gull", (0.95, 0.95, 0.93, 1), emission=0.15)
    ink.birds((0, 20, 0), 5, 5, 4, 0.8, gull)
    ink.birds((4, 26, 0), 7, 7, 3, 0.8, gull)
    return {"main": ((-4, 6, 6), (1.5, 40, 3), 32)}


# ---- 12 梦游天姥吟留别：我欲因之梦吴越，一夜飞度镜湖月 ----
def jinghu(scene):
    """月夜，镜湖平得像镜子，倒映着月亮；梦里的人凌空飞过湖面。"""
    sky(scene, (0.03, 0.03, 0.1, 1), (0.18, 0.2, 0.35, 1), 1.0, (0.7, 0.75, 1, 1), 0.6, (30, 0, 180))
    moon((20, 700, 130), 20)
    water((0.03, 0.04, 0.08, 1), rough=0.01)
    ink.mountains("far", 900, 140, 420, 60, 140, 5, ink.rock("far", (0.04, 0.05, 0.1, 1), (0.12, 0.13, 0.22, 1), 3))
    robe_m = mat("robe", (0.85, 0.88, 1, 1), emission=1.0)
    man, arm, _ = props.person(-1.5, 24, 7, Vector((1, 0.3, 0)), robe_m)
    man.rotation_euler = (0, math.radians(-25), ink.yaw_toward(Vector((1, 0.3, 0))))
    props.robe(man, arm, robe_m, length=1.15)
    ink.bob(man, 0.5)
    ink.mist_band((0, 200), (900, 300), 0, 8, density=0.03, drift=30, color=(0.4, 0.45, 0.7, 1))
    return {"main": ((0, 10, 6), (0, 300, 30), 30)}


# ---- 13 菩萨蛮·书江西造口壁：青山遮不住，毕竟东流去 ----
def qingshan(scene):
    """青山层层叠叠想挡住江水，江水还是绕过山脚向东流去，水面的波光一道道流走。"""
    sky(scene, (0.2, 0.3, 0.42, 1), (0.85, 0.82, 0.7, 1), 1.0, (1, 0.95, 0.85, 1), 2.5, (50, 0, -50))
    pts = [(math.sin(k * 0.45) * 50 + k * 4, k * 25 - 20) for k in range(34)]
    width = lambda u: 34 - u * 22
    river = mat("river", (0.55, 0.62, 0.62, 1), rough=0.2, emission=0.05)
    ink.ribbon("river", pts, width, 0.4, river)
    green = ink.rock("green", (0.04, 0.12, 0.08, 1), (0.25, 0.42, 0.3, 1), 0, per_meter=0.05)
    words.valley(pts, width, green)
    ink.mountains("far", 1400, 200, 1000, 110, 160, 3, ink.rock("far", (0.2, 0.3, 0.35, 1), (0.45, 0.55, 0.58, 1), 3))
    ink.valley_mist(y=600, depth=900, height=40, density=0.008, color=(0.85, 0.85, 0.8, 1))
    glint = mat("glint", (1, 1, 0.95, 1), emission=1.2)
    flow = bpy.data.objects.new("flow", None)
    bpy.context.collection.objects.link(flow)
    rnd = random.Random(1)
    marks = []
    for k in range(260):
        i = rnd.uniform(0, len(pts) - 1.01)
        marks.append((i, rnd.uniform(-0.35, 0.35)))
        o = ink.prop(bpy.ops.mesh.primitive_cube_add, glint, None, size=1, scale=(2.5, 0.25, 0.05))
        marks[-1] = (i, marks[-1][1], o)

    def along(i):
        j = int(i)
        a, b = Vector(pts[j] + (0,)), Vector(pts[j + 1] + (0,))
        return a.lerp(b, i - j), (b - a).normalized()

    def update(t):
        for i0, side, o in marks:
            i = (i0 + t) % (len(pts) - 1.01)  # 每个循环流过一段折线：近处快远处慢，像透视
            p, d = along(i)
            n = Vector((-d.y, d.x, 0))
            p = p + n * side * width(i / len(pts))
            o.location = (p.x, p.y, 0.5)
            o.rotation_euler = (0, 0, math.atan2(d.y, d.x))
    ink.on_frame(update)
    return {"main": ((10, -120, 190), (-20, 600, -40), 30)}


# ---- 14 长亭送别：晓来谁染霜林醉？总是离人泪 ----
def shuanglin(scene):
    """清晨，一片经霜的枫林红得像醉了，长亭边两人依依作别，一匹马等在路旁。"""
    sky(scene, (0.25, 0.28, 0.38, 1), (0.9, 0.75, 0.62, 1), 1.0, (1, 0.85, 0.7, 1), 2.2, (78, 0, 140))
    ink.mountains("far", 900, 140, 380, 60, 140, 3, ink.rock("far", (0.3, 0.3, 0.38, 1), (0.55, 0.52, 0.58, 1), 3))
    ink.valley_mist(y=120, depth=200, height=10, density=0.04, color=(0.95, 0.85, 0.82, 1))
    frost = ink.rock("frost", (0.5, 0.5, 0.5, 1), (0.85, 0.85, 0.86, 1), 0, per_meter=0.1)
    ink.ground(mat=frost)
    trunk = mat("trunk", (0.08, 0.05, 0.05, 1))
    reds = [mat(f"maple{k}", c) for k, c in enumerate([(0.6, 0.05, 0.03, 1), (0.75, 0.15, 0.03, 1), (0.85, 0.35, 0.05, 1)])]
    rnd = random.Random(8)
    for k in range(45):
        x, y = rnd.uniform(-30, 30), rnd.uniform(18, 60)
        if abs(x) < 4 and y < 30:
            continue
        tree(x, y, 0.8, rnd.uniform(5, 9), trunk, reds[k % 3], seed=k, clumps=8)
    dark = mat("dark", (0.04, 0.035, 0.04, 1))
    words.pavilion(-4, 10, 0.8, 3, dark)
    for k, (x, toward) in enumerate([(-0.6, Vector((1, 0.2, 0))), (0.6, Vector((-1, 0.2, 0)))]):
        man, arm, _ = props.person(x, 9, 0.8, toward, dark, phase=k * 21)
        props.robe(man, arm, dark)
    horse, _, strip = ink.character("horse.glb", 2.3, "Idle_Headlow", dark)
    strip.scale = ink.LOOP / 80
    strip.repeat = 20
    horse.location = (3.5, 11, 0.8)
    horse.rotation_euler = (0, 0, math.radians(-80))

    def leaf(rnd):
        bpy.ops.mesh.primitive_plane_add(size=0.16)
        o = bpy.context.active_object
        o.data.materials.append(reds[rnd.randrange(3)])
        return o
    fall((0, 14), (16, 10), 8, 60, leaf, seed=4)
    return {"main": ((0.5, -5, 2.4), (0.5, 40, 4.2), 32)}


# ---- 15 赤壁赋：舳舻千里，旌旗蔽空 ----
def zhulu(scene):
    """江上战船首尾相接一眼望不到头，船上旌旗遮天蔽日。"""
    sky(scene, (0.12, 0.12, 0.16, 1), (0.75, 0.55, 0.38, 1), 1.0, (1, 0.75, 0.5, 1), 2.5, (80, 0, 120))
    ink.mountains("far", 900, 140, 420, 80, 140, 3, ink.rock("far", (0.2, 0.15, 0.15, 1), (0.42, 0.32, 0.28, 1), 3))
    water((0.1, 0.09, 0.09, 1))
    ink.valley_mist(y=250, depth=400, height=14, density=0.015, color=(0.85, 0.65, 0.5, 1))
    hull_m = mat("hull", (0.05, 0.04, 0.04, 1))
    deck_m = mat("deck", (0.12, 0.09, 0.07, 1))
    flag_ms = [mat(f"flag{k}", c) for k, c in enumerate([(0.6, 0.08, 0.05, 1), (0.75, 0.55, 0.1, 1), (0.1, 0.1, 0.12, 1)])]
    banners = []
    rnd = random.Random(6)
    for k in range(26):
        row = k % 3
        x = -60 + (k // 3) * 14 + row * 5
        y = 26 + row * 14 + (k // 3) * 9
        _, b = warship(x, y, rnd.uniform(10, 14), hull_m, deck_m, flag_ms[k % 3], rnd.uniform(-0.1, 0.1), flags=3, name=f"fleet{k}")
        banners += b
    ink.wave_flags(banners)
    return {"main": ((-16, 4, 6), (4, 90, 7), 30)}


# ---- 16 桂枝香·金陵怀古：千里澄江似练，翠峰如簇 ----
def chengjiang(scene):
    """清澈的长江像一匹白绢铺向远方，两岸青翠的山峰一簇簇攒聚；江上几片帆。"""
    sky(scene, (0.2, 0.34, 0.5, 1), (0.88, 0.86, 0.78, 1), 1.0, (1, 0.95, 0.85, 1), 2.5, (60, 0, -30))
    pts = [(math.sin(k * 0.35) * 30 - k * 2, k * 30 - 30) for k in range(36)]
    width = lambda u: 46 - u * 30
    silk = mat("silk", (0.92, 0.93, 0.9, 1), rough=0.3, emission=0.15)
    ink.ribbon("river", pts, width, 0.4, silk)
    green = ink.rock("jade", (0.02, 0.18, 0.12, 1), (0.25, 0.55, 0.38, 1), 0, per_meter=0.06)
    rnd = random.Random(3)
    for i in range(0, len(pts) - 1, 2):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        d = Vector((x1 - x0, y1 - y0, 0)).normalized()
        n = Vector((-d.y, d.x, 0))
        w = width(i / len(pts)) / 2
        for side in (-1, 1):
            for j in range(2):  # 一簇：几座峰挤在一起
                r = rnd.uniform(18, 30) * (1 + i / 50)
                c = Vector((x0, y0, 0)) + n * side * (w + r * (1.0 + j * 0.8)) + d * rnd.uniform(-10, 10)
                ink.cliff(f"cu{i}{side}{j}", (c.x, c.y), r, r * rnd.uniform(1.0, 1.6), i * 7 + j + side, green, res=48, rough=0.6, peaked=True)
    ink.ground(size=(1400, 1200), y=500, mat=mat("land", (0.15, 0.28, 0.18, 1)))
    ink.valley_mist(y=600, depth=1000, height=40, density=0.008, color=(0.85, 0.88, 0.85, 1))
    sail = mat("sail", (0.95, 0.92, 0.85, 1))
    dark = mat("dark", (0.05, 0.05, 0.05, 1))
    for k, i in enumerate((4, 9, 15)):
        x, y = pts[i]
        hull = words.boat(5, 1.4, dark)
        hull.location = (x + (k - 1) * 6, y, 0.45)
        hull.rotation_euler = (0, 0, math.radians(80))
        ink.prop(bpy.ops.mesh.primitive_cube_add, sail, hull, size=1, location=(x + (k - 1) * 6, y, 3.2), scale=(0.06, 2.4, 4.6))
        ink.bob(hull, 0.06, k * 0.3)
    return {"main": ((5, -60, 45), (-15, 500, 0), 30)}


# ---- 17 氓：桑之未落，其叶沃若 ----
def sangye(scene):
    """暖阳下一片桑林，叶子还没落，青绿润泽，在风里轻轻翻动。"""
    sky(scene, (0.25, 0.4, 0.55, 1), (0.95, 0.88, 0.7, 1), 1.0, (1, 0.9, 0.7, 1), 3.5, (60, 0, 150))
    ink.mountains("far", 900, 140, 380, 50, 140, 3, ink.rock("far", (0.25, 0.35, 0.35, 1), (0.5, 0.6, 0.55, 1), 3))
    ink.valley_mist(y=150, depth=250, height=10, density=0.02, color=(1, 0.95, 0.85, 1))
    ink.ground(mat=ink.rock("grass", (0.15, 0.28, 0.08, 1), (0.4, 0.55, 0.2, 1), 0, per_meter=0.1))
    trunk = mat("trunk", (0.18, 0.12, 0.08, 1))
    leaves = [mat(f"mulberry{k}", c, rough=0.35) for k, c in enumerate([(0.12, 0.42, 0.08, 1), (0.2, 0.5, 0.1, 1), (0.08, 0.32, 0.06, 1)])]
    rnd = random.Random(2)
    for k in range(30):
        x, y = rnd.uniform(-14, 14), rnd.uniform(6, 50)
        tree(x, y, 0.8, rnd.uniform(4, 6.5), trunk, leaves[k % 3], seed=k, clumps=12, spread=0.4, wind=0.03)
    return {"main": ((0, -3, 2.0), (0, 40, 4.2), 30)}


# ---- 18 念奴娇·过洞庭：尽挹西江，细斟北斗，万象为宾客 ----
def beidou(scene):
    """洞庭湖上的秋夜，满天星斗，北斗七星格外亮；船头一人举杯，像要舀尽西江、用北斗来斟酒。"""
    sky(scene, (0.01, 0.015, 0.05, 1), (0.08, 0.1, 0.2, 1), 1.0, (0.6, 0.7, 1, 1), 0.4, (30, 0, 180))
    water((0.02, 0.03, 0.06, 1), rough=0.02)
    ink.mountains("far", 900, 140, 420, 30, 140, 3, ink.rock("far", (0.02, 0.03, 0.06, 1), (0.06, 0.07, 0.12, 1), 3))
    rnd = random.Random(9)
    star = mat("star", (0.9, 0.92, 1, 1), emission=8)
    bright = mat("dipper", (1, 0.95, 0.8, 1), emission=20)
    stars = []
    for _ in range(260):
        az, el = rnd.uniform(-0.6, 0.6), rnd.uniform(0.04, 0.75)
        d = Vector((math.sin(az) * math.cos(el), math.cos(az) * math.cos(el), math.sin(el)))
        stars.append(ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, star, None, subdivisions=1, radius=rnd.uniform(0.6, 1.6), location=d * 800))
    dipper = [(-0.28, 0.42), (-0.2, 0.44), (-0.13, 0.42), (-0.06, 0.4), (0.0, 0.35), (0.12, 0.36), (0.1, 0.45)]  # 北斗：斗柄在左，斗口在右
    for az, el in dipper:
        d = Vector((math.sin(az) * math.cos(el), math.cos(az) * math.cos(el), math.sin(el)))
        stars.append(ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, bright, None, subdivisions=2, radius=4, location=d * 800))
    phases = [rnd.random() for _ in stars]

    def twinkle(t):
        for o, ph in zip(stars, phases):
            o.scale = [0.75 + 0.25 * math.sin(2 * math.pi * (t * 2 + ph))] * 3
    ink.on_frame(twinkle)
    dark = mat("dark", (0.02, 0.02, 0.03, 1))
    hull = words.boat(4.5, 1.3, dark)
    hull.location = (1.5, 16, 0.2)
    hull.rotation_euler = (0, 0, math.radians(-10))
    man, arm, _ = props.person(0, 0, 0, Vector((0.2, 1, 0)), dark, action="Man_Sitting", frame=100)
    props.pose_part(arm, "Man_Punch", 7, ["Shoulder.R", "UpperArm.R", "LowerArm.R", "Palm.R"])
    props.seat(man, arm, hull.matrix_world @ Vector((1.2, 0, 0.42)))
    ink.keep_parent(man, hull)
    ink.bob(hull, 0.05)
    return {"main": ((0, 4, 1.6), (0, 300, 120), 26)}


# ---- 19 阿房宫赋：廊腰缦回，檐牙高啄 ----
def efang(scene):
    """暮色里宫殿连绵，长廊像腰带一样曲折回环，殿檐四角高高翘起像鸟嘴在啄。"""
    sky(scene, (0.14, 0.1, 0.18, 1), (0.9, 0.55, 0.35, 1), 1.0, (1, 0.7, 0.45, 1), 2.5, (78, 0, -60))
    ink.mountains("far", 900, 140, 420, 70, 140, 3, ink.rock("far", (0.16, 0.12, 0.16, 1), (0.38, 0.28, 0.3, 1), 3))
    ink.valley_mist(y=200, depth=300, height=12, density=0.02, color=(0.9, 0.6, 0.45, 1))
    ink.ground(mat=mat("court", (0.28, 0.22, 0.2, 1)))
    roof = mat("roof", (0.08, 0.07, 0.08, 1))
    wall = mat("redwall", (0.5, 0.12, 0.08, 1))
    lamp = mat("lamp", (1, 0.6, 0.25, 1), emission=5)
    halls = [(-10, 40, 6), (8, 52, 7), (-4, 70, 8), (16, 84, 6), (-18, 88, 7)]
    for x, y, s in halls:
        ink.prop(bpy.ops.mesh.primitive_cube_add, wall, None, size=1, location=(x, y, 2), scale=(s * 1.6, s, 4))
        ink.prop(bpy.ops.mesh.primitive_cube_add, lamp, None, size=1, location=(x, y - s / 2 - 0.03, 1.8), scale=(s * 0.4, 0.05, 1.4))
        words.pavilion(x, y, 4, s * 1.2, roof)
    # 长廊：在殿与殿之间折来折去，架在柱上，上面一道廊顶。
    for (x0, y0, _), (x1, y1, _) in zip(halls, halls[1:]):
        mid = Vector(((x0 + x1) / 2 + 6, (y0 + y1) / 2, 0))
        for a, b in ((Vector((x0, y0, 0)), mid), (mid, Vector((x1, y1, 0)))):
            d = b - a
            n = int(d.length / 2)
            for i in range(n + 1):
                p = a.lerp(b, i / max(n, 1))
                ink.prop(bpy.ops.mesh.primitive_cylinder_add, wall, None, vertices=6, radius=0.12, depth=2.6, location=(p.x, p.y, 1.3))
            yaw = math.atan2(d.y, d.x)
            c = (a + b) / 2
            ink.prop(bpy.ops.mesh.primitive_cube_add, roof, None, size=1, location=(c.x, c.y, 2.8), scale=(d.length, 2.2, 0.3), rotation=(0, 0, yaw))
    ink.birds((0, 60, 0), 18, 20, 4, 0.6, roof)
    return {"main": ((-8, -6, 12), (2, 70, 4), 30)}


# ---- 20 锦瑟：沧海月明珠有泪，蓝田日暖玉生烟 ----
def canghai(scene):
    """月照沧海，海边礁石上一只蚌张着壳，里面的珍珠闪着泪光；海浪一起一伏。"""
    sky(scene, (0.02, 0.03, 0.08, 1), (0.15, 0.2, 0.32, 1), 1.0, (0.7, 0.8, 1, 1), 0.7, (30, 0, 180))
    moon((-30, 700, 110), 18)
    sea = water((0.03, 0.05, 0.09, 1), rough=0.05)
    d = sea.modifiers.new("waves", "DISPLACE")
    tex = bpy.data.textures.new("waves", type="CLOUDS")
    tex.noise_scale = 3
    d.texture = tex
    d.strength = 0.6
    sea.data = sea.data.copy()
    bpy.context.view_layer.objects.active = sea
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.subdivide(number_cuts=80)
    bpy.ops.object.mode_set(mode="OBJECT")
    ink.bob(sea, 0.15)
    rock = ink.rock("reef", (0.03, 0.04, 0.05, 1), (0.15, 0.17, 0.2, 1), 6)
    reef = ink.cliff("reef", (0, 8), 4, 1.6, 2, rock, res=60, rough=0.4)
    shell = mat("shell", (0.5, 0.5, 0.55, 1), rough=0.3)
    top = ink.surface_z(reef, 0, 7)
    for k, tilt in ((0, 0), (1, -1.0)):
        s = ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, shell, None, segments=24, ring_count=12, radius=0.6, location=(0, 7, top + 0.1), scale=(1, 0.8, 0.25))
        s.rotation_euler = (tilt, 0, 0)
        if k:
            s.location = (0, 7.45, top + 0.45)
    pearl = mat("pearl", (0.95, 0.93, 1, 1), emission=3)
    p = ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, pearl, None, segments=24, ring_count=12, radius=0.14, location=(0, 6.9, top + 0.25))
    glow = p.data.materials[0].node_tree.nodes["Principled BSDF"].inputs["Emission Strength"]
    ink.on_frame(lambda t: setattr(glow, "default_value", 2.5 + 1.5 * math.sin(2 * math.pi * t)))
    return {"main": ((0.6, 2.5, top + 1.4), (0, 300, 40), 32)}


SCENES = {
    "gushi-01": denggao,
    "gushi-02": shufen,
    "gushi-03": suzhemu,
    "gushi-04": yuantian,
    "gushi-05": shudao,
    "gushi-06": feituan,
    "gushi-07": chunjiang,
    "gushi-08": xiaolou,
    "gushi-09": yiwei,
    "gushi-10": tiema,
    "gushi-11": kezhi,
    "gushi-12": jinghu,
    "gushi-13": qingshan,
    "gushi-14": shuanglin,
    "gushi-15": zhulu,
    "gushi-16": chengjiang,
    "gushi-17": sangye,
    "gushi-18": beidou,
    "gushi-19": efang,
    "gushi-20": canghai,
}
