"""生物首页的三维示意动画：每个知识点一幅 2 秒循环。

深色背景上的半透明、带光泽的分子和细胞结构，景深虚化，像显微动画；首页白字压在上面。
卡片 id 是 bio-01 ~ bio-20，app 里 HomeScreen.bioScenes 把每幅配一张讲同一内容的卡片。
单位随意（大致一个单位一纳米或一微米），只管画面比例。
"""
import math
import random

import bpy
from mathutils import Vector

import ink


def world(scene, top=(0.001, 0.002, 0.006, 1), bottom=(0.004, 0.012, 0.02, 1), key=(0.8, 0.9, 1.0), rim=(0.3, 0.8, 1.0)):
    """深色渐变背景，一盏主光一盏轮廓光。"""
    w = bpy.data.worlds.new("bio")
    scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    coord = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(coord.outputs["Generated"], sep.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = bottom
    ramp.color_ramp.elements[1].position = 0.6
    ramp.color_ramp.elements[1].color = top
    nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], nt.nodes["Background"].inputs["Color"])
    for loc, color, energy, size in (((6, -8, 10), key, 600, 8), ((-8, 10, 4), rim, 900, 6)):
        bpy.ops.object.light_add(type="AREA", location=loc)
        light = bpy.context.active_object
        light.data.energy = energy
        light.data.color = color
        light.data.size = size
        light.rotation_euler = (Vector((0, 0, 0)) - light.location).to_track_quat("-Z", "Y").to_euler()


def gl(name, color, emit=0.0, rough=0.3, alpha=1.0, coat=0.6):
    """带光泽的材质，可以半透明、可以自发光。"""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = color
    b.inputs["Roughness"].default_value = rough
    b.inputs["Coat Weight"].default_value = coat
    if emit:
        b.inputs["Emission Color"].default_value = color
        b.inputs["Emission Strength"].default_value = emit
    if alpha < 1:
        b.inputs["Alpha"].default_value = alpha
        m.surface_render_method = "BLENDED"
    return m


def ball(mat, loc, r, parent=None, scale=(1, 1, 1)):
    return ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, mat, parent, segments=20, ring_count=12, radius=r, location=loc, scale=scale)


def stick(mat, a, b, r, parent=None):
    a, b = Vector(a), Vector(b)
    d = b - a
    return ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, parent, vertices=12, radius=r, depth=max(d.length, 1e-3), location=(a + b) / 2, rotation=d.to_track_quat("Z", "Y").to_euler())


def tube(name, pts, radius, mat):
    """沿点列的光滑圆管（DNA 骨架、mRNA、轴突、染色质）。返回（物体，样条），改样条点即可做动画。"""
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = radius
    cu.bevel_resolution = 4
    sp = cu.splines.new("NURBS")
    sp.points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        sp.points[i].co = (p[0], p[1], p[2], 1)
    sp.use_endpoint_u = True
    sp.order_u = 4
    o = bpy.data.objects.new(name, cu)
    bpy.context.collection.objects.link(o)
    o.data.materials.append(mat)
    return o, sp


def group(loc=(0, 0, 0)):
    bpy.ops.object.empty_add(location=loc)
    return bpy.context.active_object


def drift(objs, amp=0.15, seed=0):
    """一群小东西各自轻轻漂动（布朗运动的样子），首尾无缝。"""
    rnd = random.Random(seed)
    items = [(o, o.location.copy(), [rnd.random() for _ in range(3)]) for o in objs]

    def update(t):
        for o, base, ph in items:
            o.location = base + Vector([amp * math.sin(2 * math.pi * (t + p)) for p in ph])
    ink.on_frame(update)


def flow(objs, start, end, seed=0):
    """一串东西从 start 流到 end：按相位均匀排开，每个循环走完一段，走到头从起点重来（离子、递质、水分子）。"""
    rnd = random.Random(seed)
    start, end = Vector(start), Vector(end)
    items = [(o, k / len(objs), Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1)))) for k, o in enumerate(objs)]

    def update(t):
        for o, ph, jitter in items:
            u = (t + ph) % 1
            o.location = start.lerp(end, u) + jitter * 0.4 * math.sin(math.pi * u)
            o.scale = [min(u * 8, 1, (1 - u) * 8)] * 3  # 两头缩成点，看不出跳回
    ink.on_frame(update)


def helix_points(n, rise, radius, turn, phase=0.0, z0=0.0):
    return [(radius * math.cos(phase + i * turn), radius * math.sin(phase + i * turn), z0 + i * rise) for i in range(n)]


BASES = {"A": (0.95, 0.45, 0.15, 1), "T": (0.2, 0.55, 0.95, 1), "G": (0.3, 0.85, 0.4, 1), "C": (0.9, 0.3, 0.6, 1)}


def dna(parent, n=24, rise=0.34, radius=1.0, z0=0.0, strand=(0.75, 0.82, 0.9, 1), strand2=None, seq="ATGC"):
    """一段双螺旋：两条骨架管，中间一级级碱基对（A-T、G-C 两种颜色）。"""
    turn = 2 * math.pi / 10
    o = parent.location  # 点按外层空物体的位置摆，旋转外层空物体时整段一起转
    s1 = [tuple(o + Vector(p)) for p in helix_points(n, rise, radius, turn, 0, z0)]
    s2 = [tuple(o + Vector(p)) for p in helix_points(n, rise, radius, turn, math.radians(150), z0)]  # 大沟小沟：两条链错开 150°
    m1 = gl("strand1", strand, 0.2)
    m2 = gl("strand2", strand2 or strand, 0.2)
    o1, _ = tube("backbone1", s1, 0.12, m1)
    o2, _ = tube("backbone2", s2, 0.12, m2)
    ink.keep_parent(o1, parent)
    ink.keep_parent(o2, parent)
    mats = {k: gl(f"base{k}", c, 0.5) for k, c in BASES.items()}
    pair = {"A": "T", "T": "A", "G": "C", "C": "G"}
    for i in range(n):
        b = seq[i % len(seq)]
        a, c = Vector(s1[i]), Vector(s2[i])
        mid = a.lerp(c, 0.5)
        stick(mats[b], a, mid, 0.07, parent)
        stick(mats[pair[b]], mid, c, 0.07, parent)
        ball(m1, a, 0.16, parent)
        ball(m2, c, 0.16, parent)


def screw(obj, turns=0.2, rise=0.68):
    """绕自身 z 轴拧着转：每个循环转 turns 圈、同时沿轴前进 rise，第 i 级碱基对正好走到第 i+k 级的位置；
    碱基序列以 k 级为周期，画面就首尾无缝。"""
    from mathutils import Quaternion
    q0 = obj.rotation_euler.to_quaternion()
    l0 = obj.location.copy()
    axis = q0 @ Vector((0, 0, 1))

    def update(t):
        obj.rotation_euler = (q0 @ Quaternion((0, 0, 1), 2 * math.pi * turns * t)).to_euler()
        obj.location = l0 + axis * rise * t
    ink.on_frame(update)


def bilayer(cx, cy, z, nx, ny, head, tail, spacing=0.5, parent=None, seed=0, holes=()):
    """磷脂双分子层：上下两层，每个磷脂是一个亲水头加两条疏水尾，头朝外。"""
    rnd = random.Random(seed)
    heads = []
    for layer in (1, -1):
        for i in range(nx):
            for j in range(ny):
                x = cx + (i - nx / 2) * spacing + rnd.uniform(-0.05, 0.05)
                y = cy + (j - ny / 2) * spacing + rnd.uniform(-0.05, 0.05)
                if any((x - hx) ** 2 + (y - hy) ** 2 < hr ** 2 for hx, hy, hr in holes):
                    continue
                hz = z + layer * 1.0
                g = group((x, y, hz))
                ball(head, (x, y, hz), 0.22, g)
                for dx in (-0.07, 0.07):
                    stick(tail, (x + dx, y, hz - layer * 0.15), (x + dx * 1.5, y, hz - layer * 0.95), 0.05, g)
                if parent:
                    ink.keep_parent(g, parent)
                heads.append(g)
    drift(heads, 0.05, seed)
    return heads


def protein(loc, size, mat, seed=0, parent=None):
    """一个蛋白：几个融在一起的球，表面疙疙瘩瘩。"""
    rnd = random.Random(seed)
    g = group(loc)
    for _ in range(6):
        off = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1.4, 1.4))) * size * 0.35
        ball(mat, Vector(loc) + off, size * rnd.uniform(0.4, 0.6), g)
    if parent:
        ink.keep_parent(g, parent)
    return g


def sway(obj, amp=0.08, axis="z", phase=0.0):
    ink.sway(obj, axis, amp, phase)


def pulse(obj, amp=0.06, cycles=1, phase=0.0):
    base = obj.scale.copy()

    def update(t):
        k = 1 + amp * math.sin(2 * math.pi * (t * cycles + phase))
        obj.scale = base * k
    ink.on_frame(update)


def cam(loc, target, lens=50, focus=None, fstop=2.0):
    return {"main": (loc, target, lens), "dof": (focus or (Vector(target) - Vector(loc)).length, fstop)}


# ---- 01 DNA 双螺旋 ----
def b_dna(scene):
    world(scene)
    g = group((0, 0, 0))
    dna(g, n=40, seq="AG", z0=-6.8)  # 碱基对两级一周期：拧 72° 移两级就和开头一样
    g.rotation_euler = (math.radians(25), math.radians(-30), 0)
    screw(g, turns=0.2, rise=0.68)
    dots = [ball(gl(f"mote{k}", (0.4, 0.8, 1, 1), 2.0), (random.Random(k).uniform(-6, 6), random.Random(k + 50).uniform(-3, 6), random.Random(k + 99).uniform(-6, 8)), 0.05) for k in range(40)]
    drift(dots, 0.3)
    return cam((-2.5, -17, 2), (-2.5, 0, 1.5), 50)


# ---- 02 DNA 半保留复制 ----
def b_replication(scene):
    world(scene)
    g = group((0, 0, 0))
    dna(g, n=22, seq="AG", z0=-9)  # 下面是还没解开的亲代双链
    old = gl("old", (0.75, 0.82, 0.9, 1), 0.2)
    new = gl("new", (1.0, 0.6, 0.2, 1), 0.6)
    top = -9 + 21 * 0.34
    for side in (-1, 1):  # 解开后的两条子链：一条旧链（灰蓝）+ 一条新合成的链（橙）
        pts_old, pts_new = [], []
        for i in range(16):
            u = i / 15
            x = side * (0.8 + u * 4.5)
            z = top + u * 9
            pts_old.append((x - side * 0.5, 0, z))
            pts_new.append((x + side * 0.5, 0, z))
            if i % 2 == 0:
                stick(gl(f"rung{side}{i}", BASES["AGTC"[i % 4]], 0.5), (x - side * 0.45, 0, z), (x + side * 0.45, 0, z), 0.07)
        tube(f"old{side}", pts_old, 0.12, old)
        tube(f"new{side}", pts_new, 0.12, new)
    helicase = ball(gl("helicase", (0.6, 0.4, 1, 1), 0.8, alpha=0.6), (0, 0, top + 0.3), 1.1)
    pulse(helicase, 0.06, 2)
    nts = [ball(gl(f"nt{k}", BASES["AGTC"[k % 4]], 1.2), (0, 0, 0), 0.14) for k in range(18)]
    flow(nts[:9], (-8, -2, top + 9), (-3.5, 0, top + 4))
    flow(nts[9:], (8, -2, top + 9), (3.5, 0, top + 4), seed=1)
    return cam((0, -22, 0), (0, 0, 0.5), 45)


# ---- 03 中心法则：转录 ----
def b_transcription(scene):
    world(scene, key=(1, 0.9, 0.8))
    g = group((0, 0, 0))
    dna(g, n=70, seq="AG", z0=-12)
    g.rotation_euler = (0, math.radians(90), 0)
    pol = ball(gl("polymerase", (0.35, 0.8, 0.6, 1), 0.6, alpha=0.55), (0, 0, 0), 2.0, scale=(1.3, 1.1, 1))
    pulse(pol, 0.04, 2)
    rna = gl("mrna", (1, 0.35, 0.3, 1), 1.0)
    pts = [(0.5 + i * 0.5, -0.3 * i, 1.2 + i * 0.6) for i in range(14)]
    o, sp = tube("mrna", pts, 0.12, rna)

    def wave(t):  # mRNA 链从聚合酶里伸出来，梢头在飘
        for i, p in enumerate(sp.points):
            a = 2 * math.pi * (t - i * 0.06)
            p.co = (0.5 + i * 0.5 + 0.25 * i / 13 * math.sin(a), -0.3 * i + 0.4 * i / 13 * math.cos(a), 1.2 + i * 0.6, 1)
    ink.on_frame(wave)
    return cam((2, -16, 4), (1, 0, 2.5), 45)


# ---- 04 翻译 ----
def b_translation(scene):
    world(scene, key=(1, 0.95, 0.85))
    rna = gl("mrna", (1, 0.35, 0.3, 1), 0.8)
    tube("mrna", [(x, 0, 0.3 * math.sin(x * 0.5)) for x in range(-14, 15, 2)], 0.14, rna)
    cod = [gl(f"codon{k}", c, 0.8) for k, c in enumerate(BASES.values())]
    for i, x in enumerate(range(-14, 15)):
        stick(cod[i % 4], (x, 0, 0.3 * math.sin(x * 0.5) + 0.1), (x, 0, 0.3 * math.sin(x * 0.5) + 0.55), 0.08)
    small = ball(gl("small_sub", (0.45, 0.6, 0.95, 1), 0.3, alpha=0.75), (0, 0, -0.6), 1.6, scale=(1.4, 1, 0.75))
    large = ball(gl("large_sub", (0.55, 0.7, 1, 1), 0.3, alpha=0.75), (0, 0, 1.9), 2.3, scale=(1.3, 1, 0.9))
    for o, ph in ((small, 0), (large, 0.3)):
        pulse(o, 0.03, 1, ph)
    trna = gl("trna", (0.95, 0.85, 0.3, 1), 0.6)
    for k, x in enumerate((-5, 5.5)):  # tRNA：L 形，一头带着氨基酸
        g = group((x, -1, 3))
        stick(trna, (x, -1, 1.5), (x, -1, 3.5), 0.18, g)
        stick(trna, (x, -1, 3.5), (x + 1.3, -1, 4.2), 0.18, g)
        ball(gl(f"aa{k}", (0.6, 1, 0.7, 1), 1.5), (x + 1.4, -1, 4.4), 0.32, g)
        sway(g, 0.1, "y", k * 0.4)
    beads = [ball(gl(f"aa_chain{k}", [(0.6, 1, 0.7, 1), (1, 0.6, 0.9, 1), (0.7, 0.8, 1, 1)][k % 3], 1.2), (0, 0, 0), 0.32) for k in range(11)]

    def chain(t):  # 肽链从核糖体顶上长出来，随动
        for k, b in enumerate(beads):
            a = 2 * math.pi * (t - k * 0.08)
            b.location = (0.6 * math.sin(a) * k / 10 + k * 0.25, 0.6 * math.cos(a) * k / 10, 4.2 + k * 0.62)
    ink.on_frame(chain)
    return cam((0, -26, 4), (0, 0, 2.5), 38)


def chromatid(mat, top, bottom, r, parent):
    """一条染色单体：两头圆的胶囊。"""
    stick(mat, top, bottom, r, parent)
    ball(mat, top, r, parent)
    ball(mat, bottom, r, parent)


def chromosome(cx, mat, cent, tilt, parent, seed=0):
    """一条复制后的染色体：两条姐妹染色单体在着丝粒处相连，像 X。"""
    g = group((cx, 0, 0))
    for s in (-1, 1):
        chromatid(mat, (cx + s * 0.55 + tilt, 0, 3.2), (cx + s * 0.25, 0, 0), 0.42, g)
        chromatid(mat, (cx + s * 0.25, 0, 0), (cx + s * 0.6 - tilt, 0, -2.6), 0.42, g)
    ball(cent, (cx, 0, 0), 0.38, g)
    ink.keep_parent(g, parent)
    return g


# ---- 05 减数分裂Ⅰ：同源染色体联会 ----
def b_meiosis(scene):
    world(scene, key=(1, 0.9, 0.9), rim=(1, 0.5, 0.7))
    g = group()
    cent = gl("centromere", (1, 1, 1, 1), 2.0)
    a = chromosome(-0.95, gl("homolog_a", (0.95, 0.35, 0.45, 1), 0.5), cent, 0.1, g)
    b = chromosome(0.95, gl("homolog_b", (0.35, 0.55, 1, 1), 0.5), cent, -0.1, g)
    fibers = gl("spindle", (0.6, 0.9, 1, 1), 1.5, alpha=0.5)
    for s in (-1, 1):  # 纺锤丝从两极连到着丝粒
        for k in range(8):
            a2 = k / 8 * 2 * math.pi
            stick(fibers, (s * 9, 0.8 * math.sin(a2), 6 * math.cos(a2) * 0.3), (s * 0.95, 0, 0), 0.025)
        ball(gl(f"pole{s}", (0.7, 0.95, 1, 1), 3), (s * 9, 0, 0), 0.3)
    sway(a, 0.05, "y")
    sway(b, 0.05, "y", 0.5)
    sway(g, 0.12, "z")
    return cam((0, -16, 1), (0, 0, 0.3), 45)


# ---- 06 流动镶嵌模型 ----
def b_membrane(scene):
    world(scene, key=(1, 0.95, 0.85), rim=(0.4, 0.7, 1))
    head = gl("head", (0.95, 0.75, 0.35, 1), 0.3)
    tail = gl("tail", (0.85, 0.8, 0.7, 1), 0.1, rough=0.5)
    holes = [(-2, 0.5, 1.4), (3, -1, 1.6), (0.5, 3, 1.2)]
    bilayer(0, 0, 0, 22, 14, head, tail, holes=holes)
    for k, (x, y, r) in enumerate(holes):  # 贯穿、镶在膜里的蛋白
        p = protein((x, y, 0), r * 1.8, gl(f"protein{k}", [(0.4, 0.6, 1, 1), (0.7, 0.4, 1, 1), (0.3, 0.85, 0.7, 1)][k], 0.4), seed=k)
        pulse(p, 0.03, 1, k * 0.3)
    sugar = gl("sugar", (0.55, 1, 0.6, 1), 1.0)
    for k, (x, y) in enumerate([(-2, 0.5), (3, -1)]):  # 糖蛋白上的糖链
        prev = Vector((x, y, 2.4))
        for j in range(5):
            nxt = prev + Vector((0.3 * math.sin(j + k), 0.3 * math.cos(j * 2), 0.5))
            stick(sugar, prev, nxt, 0.06)
            ball(sugar, nxt, 0.15)
            prev = nxt
    return cam((0, -13, 6.5), (0, 0, 0), 40)


# ---- 07 叶绿体 ----
def b_chloroplast(scene):
    world(scene, top=(0.001, 0.004, 0.002, 1), bottom=(0.004, 0.014, 0.006, 1), rim=(0.5, 1, 0.5))
    g = group()
    ball(gl("envelope", (0.35, 0.8, 0.3, 1), 0.15, alpha=0.25, rough=0.1), (0, 0, 0), 4, g, scale=(1.6, 1, 0.75))
    ball(gl("stroma", (0.2, 0.5, 0.15, 1), 0.1, alpha=0.2), (0, 0, 0), 3.7, g, scale=(1.6, 1, 0.72))
    disc = gl("thylakoid", (0.15, 0.85, 0.25, 1), 0.6)
    lam = gl("lamella", (0.3, 0.9, 0.35, 1), 0.4)
    rnd = random.Random(3)
    grana = []
    for k in range(7):  # 基粒：一摞摞类囊体
        x, y = rnd.uniform(-4, 4), rnd.uniform(-1.6, 1.6)
        n = rnd.randint(5, 9)
        for j in range(n):
            ink.prop(bpy.ops.mesh.primitive_cylinder_add, disc, g, vertices=24, radius=0.55, depth=0.12, location=(x, y, (j - n / 2) * 0.17))
        grana.append((x, y))
    for (x0, y0), (x1, y1) in zip(grana, grana[1:]):  # 基质片层连起相邻基粒
        stick(lam, (x0, y0, 0), (x1, y1, 0), 0.05, g)
    g.rotation_euler = (math.radians(55), 0, math.radians(15))
    sway(g, 0.12, "z")
    return cam((0, -21, 1), (0, 0, 0), 45)


# ---- 08 光反应 ----
def b_light(scene):
    world(scene, top=(0.001, 0.004, 0.002, 1), bottom=(0.004, 0.014, 0.006, 1), key=(1, 0.95, 0.6), rim=(0.5, 1, 0.5))
    head = gl("head", (0.3, 0.75, 0.3, 1), 0.3)
    tail = gl("tail", (0.6, 0.75, 0.55, 1), 0.1)
    holes = [(-2.5, 0, 1.5), (2.5, 1, 1.5)]
    bilayer(0, 0, 0, 22, 12, head, tail, holes=holes, seed=2)
    for k, (x, y, r) in enumerate(holes):
        p = protein((x, y, 0.3), r * 1.9, gl(f"ps{k}", (0.15, 0.7, 0.3, 1), 0.8), seed=k + 5)
        pulse(p, 0.05, 2, k * 0.5)
    photons = gl("photon", (1, 0.85, 0.3, 1), 8)
    import props
    props.rain((0, 0), (10, 5), 14, 60, photons, slant=0.35, radius=0.04, length=1.4, cycles=2)
    o2 = gl("o2", (0.5, 0.85, 1, 1), 2.0, alpha=0.8)
    bubbles = [ball(o2, (0, 0, 0), 0.22) for _ in range(12)]
    flow(bubbles[:6], (-2.5, -1, 2), (-4, -2, 9))
    flow(bubbles[6:], (2.5, 0, 2), (4, -1, 9), seed=2)
    return cam((0, -14, 5), (0, 0, 1.5), 40)


# ---- 09 线粒体 ----
def b_mito(scene):
    world(scene, key=(1, 0.8, 0.6), rim=(1, 0.5, 0.3))
    g = group()
    ball(gl("outer", (1, 0.55, 0.3, 1), 0.2, alpha=0.15, rough=0.15), (0, 0, 0), 3, g, scale=(1.8, 1, 0.9))
    crista = gl("cristae", (1, 0.45, 0.2, 1), 1.0)
    for k in range(9):  # 内膜折成一道道嵴：从两侧交替伸进去的圆润褶板
        x = -4 + k
        side = 1 if k % 2 else -1
        fold = ink.prop(bpy.ops.mesh.primitive_cube_add, crista, g, size=1, location=(x, 0, side * 0.6), scale=(0.28, 2.2, 1.9))
        sub = fold.modifiers.new("sub", "SUBSURF")
        sub.levels = 3
    matrix = [ball(gl(f"atp{k}", (1, 0.9, 0.4, 1), 4), (random.Random(k).uniform(-4.5, 4.5), random.Random(k + 7).uniform(-1.5, 1.5), random.Random(k + 9).uniform(-1.5, 1.5)), 0.08, g) for k in range(30)]
    drift(matrix, 0.2)
    g.rotation_euler = (math.radians(30), 0, math.radians(-10))
    sway(g, 0.1, "z")
    return cam((0, -14, 2), (0, 0, 0), 45)


# ---- 10 突触 ----
def b_synapse(scene):
    world(scene, key=(0.9, 0.85, 1), rim=(0.8, 0.4, 1))
    pre = ball(gl("presynaptic", (0.6, 0.45, 1, 1), 0.3, alpha=0.55), (0, 0, 4), 3.4, scale=(1, 1, 0.8))
    ves = gl("vesicle", (1, 0.75, 0.4, 1), 1.5, alpha=0.8)
    rnd = random.Random(5)
    vs = [ball(ves, (rnd.uniform(-2, 2), rnd.uniform(-1.5, 1.5), rnd.uniform(2.3, 4.5)), 0.4) for _ in range(14)]
    drift(vs, 0.1)
    post = gl("postsynaptic", (0.45, 0.6, 1, 1), 0.3)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=12, location=(0, 0, -12.6), segments=48, ring_count=24)
    bpy.context.active_object.data.materials.append(post)
    rec = gl("receptor", (0.3, 1, 0.7, 1), 1.2)
    for x in (-2.4, -1.2, 0, 1.2, 2.4):
        stick(rec, (x, 0, -0.7), (x, 0, -0.2), 0.18)
    nt = gl("transmitter", (1, 0.9, 0.4, 1), 6)
    dots = [ball(nt, (0, 0, 0), 0.07) for _ in range(40)]
    for k in range(4):
        flow(dots[k * 10:(k + 1) * 10], (-1.5 + k, 0, 1.3), (-2.4 + k * 1.6, 0, -0.1), seed=k)
    return cam((0, -13, 1.5), (0, 0, 1), 45)


# ---- 11 动作电位 ----
def b_action(scene):
    world(scene, key=(0.9, 0.9, 1), rim=(0.3, 0.6, 1))
    axon = gl("axon", (0.35, 0.45, 0.8, 1), 0.2, alpha=0.6)
    L = 30
    pts = [(-L / 2 + i * 2, 0.6 * math.sin(i * 0.4), 0.5 * math.cos(i * 0.3)) for i in range(16)]
    tube("axon", pts, 1.2, axon)
    myelin = gl("myelin", (0.85, 0.85, 0.95, 1), 0.1)
    for x in range(-14, 15, 4):
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, myelin, None, vertices=24, radius=1.45, depth=2.6, location=(x, 0.6 * math.sin((x + 15) / 2 * 0.4), 0.5 * math.cos((x + 15) / 2 * 0.3)), rotation=(0, math.pi / 2, 0))
    ring = gl("spike", (1, 0.8, 0.3, 1), 12)
    rings = [ink.prop(bpy.ops.mesh.primitive_torus_add, ring, None, major_radius=1.5, minor_radius=0.18, rotation=(0, math.pi / 2, 0)) for _ in range(3)]
    flow(rings, (-L / 2, 0, 0), (L / 2, 0, 0))  # 兴奋沿神经纤维往前传，三环相隔三分之一，首尾无缝
    na = gl("na", (1, 0.6, 0.2, 1), 3)
    ions = [ball(na, (0, 0, 0), 0.12) for _ in range(24)]
    for k, o in enumerate(ions):
        o.location = (random.Random(k).uniform(-12, 12), random.Random(k + 3).uniform(-2.5, 2.5), random.Random(k + 6).uniform(-2.5, 2.5))
    drift(ions, 0.4, 3)
    return cam((-6, -16, 3), (2, 0, 0), 40)


# ---- 12 红细胞吸水 ----
def b_rbc(scene):
    world(scene, top=(0.02, 0.005, 0.01, 1), bottom=(0.06, 0.015, 0.02, 1), key=(1, 0.85, 0.8), rim=(1, 0.4, 0.4))
    red = gl("rbc", (0.75, 0.08, 0.08, 1), 0.3, rough=0.25)
    rnd = random.Random(1)
    for k in range(9):
        x, y, z = rnd.uniform(-6, 6), rnd.uniform(-2, 10), rnd.uniform(-4, 4)
        g = group((x, y, z))
        ink.prop(bpy.ops.mesh.primitive_torus_add, red, g, major_radius=1.0, minor_radius=0.5, location=(x, y, z))
        ball(red, (x, y, z), 0.9, g, scale=(1, 1, 0.3))
        g.rotation_euler = (rnd.uniform(0, 3), rnd.uniform(0, 3), 0)
        pulse(g, 0.18 if k < 3 else 0.05, 1, rnd.random())  # 前面几个吸水胀大
        sway(g, 0.15, "x", rnd.random())
    water = gl("water", (0.5, 0.8, 1, 1), 2.0)
    ws = [ball(water, (0, 0, 0), 0.06) for _ in range(30)]
    for k in range(3):
        flow(ws[k * 10:(k + 1) * 10], (rnd.uniform(-8, 8), -4, rnd.uniform(-4, 4)), (rnd.uniform(-4, 4), 2, rnd.uniform(-2, 2)), seed=k)
    return cam((0, -14, 1), (0, 2, 0), 45)


# ---- 13 T2 噬菌体侵染细菌 ----
def b_phage(scene):
    world(scene, key=(0.9, 0.95, 1), rim=(0.4, 1, 0.8))
    body = gl("phage", (0.75, 0.8, 0.9, 1), 0.2, rough=0.2)
    dnam = gl("phage_dna", (1, 0.5, 0.3, 1), 3)
    cell = gl("bacterium", (0.3, 0.7, 0.5, 1), 0.2, alpha=0.8)
    ball(cell, (0, 0, -30), 30, scale=(1.4, 1, 1))
    rnd = random.Random(2)
    for k, (x, y) in enumerate([(0, 0), (-5, 3), (4.5, 4), (-2, 8)]):
        g = group((x, y, 0))
        ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, body, g, subdivisions=1, radius=1.1, location=(x, y, 5.2), scale=(1, 1, 1.25))
        stick(body, (x, y, 1.1), (x, y, 3.8), 0.22, g)
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, body, g, vertices=6, radius=0.6, depth=0.25, location=(x, y, 1.0))
        for j in range(6):
            a = j / 6 * 2 * math.pi
            knee = Vector((x + math.cos(a) * 1.4, y + math.sin(a) * 1.4, 1.4))
            stick(body, (x, y, 1.0), knee, 0.06, g)
            stick(body, knee, (x + math.cos(a) * 2.0, y + math.sin(a) * 2.0, 0.05), 0.06, g)
        if k == 0:  # 注入：DNA 顺尾管进到细菌里
            inj = [ball(dnam, (0, 0, 0), 0.1) for _ in range(10)]
            flow(inj, (x, y, 4.5), (x, y, -1.5))
        ink.bob(g, 0.08, rnd.random())
    return cam((0, -18, 7), (0, 2, 2.5), 40)


def antibody(loc, mat, tip, rot, parent=None):
    """抗体：Y 形，两条臂端是结合抗原的部位。"""
    g = group(loc)
    x, y, z = loc
    stick(mat, (x, y, z - 1.2), (x, y, z), 0.25, g)
    for s in (-1, 1):
        end = (x + s * 0.9, y, z + 1.0)
        stick(mat, (x, y, z), end, 0.22, g)
        ball(tip, end, 0.28, g)
    g.rotation_euler = rot
    if parent:
        ink.keep_parent(g, parent)
    return g


# ---- 14 抗体与抗原 ----
def b_antibody(scene):
    world(scene, key=(1, 0.95, 0.9), rim=(1, 0.6, 0.3))
    virus = group()
    ball(gl("virus", (0.6, 0.3, 0.8, 1), 0.3), (0, 0, 0), 2.4, virus)
    spike = gl("antigen", (1, 0.35, 0.35, 1), 1.0)
    ab = gl("antibody", (0.85, 0.9, 1, 1), 0.3, rough=0.25)
    tip = gl("binding", (0.4, 1, 0.8, 1), 3)
    rnd = random.Random(4)
    for k in range(26):
        d = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized()
        stick(spike, d * 2.3, d * 3.1, 0.1, virus)
        ball(spike, d * 3.15, 0.18, virus)
        if k % 3 == 0:
            antibody(tuple(d * 4.7), ab, tip, d.to_track_quat("-Z", "Y").to_euler(), virus)
    sway(virus, 0.25, "z")
    free = [antibody((rnd.uniform(-7, 7), rnd.uniform(0, 8), rnd.uniform(-5, 5)), ab, tip, (rnd.uniform(0, 3), rnd.uniform(0, 3), 0)) for _ in range(6)]
    drift(free, 0.4)
    return cam((0, -15, 1), (0, 0, 0), 45)


# ---- 15 质壁分离 ----
def b_plasmolysis(scene):
    world(scene, top=(0.01, 0.01, 0.02, 1), bottom=(0.04, 0.03, 0.06, 1), key=(1, 0.95, 0.9), rim=(0.7, 0.5, 1))
    wall = gl("wall", (0.7, 0.85, 0.6, 1), 0.2, alpha=0.65)
    vac = gl("vacuole", (0.55, 0.15, 0.6, 1), 0.6, alpha=0.9)
    for i in range(3):
        for j in range(2):
            x, y = (i - 1) * 4.2, j * 3.2
            for (sx, sy, dx, dy) in ((4, 0.25, 0, -1.5), (4, 0.25, 0, 1.5), (0.25, 3.2, -2, 0), (0.25, 3.2, 2, 0)):
                ink.prop(bpy.ops.mesh.primitive_cube_add, wall, None, size=1, location=(x + dx, y + dy, 0), scale=(sx, sy, 2.4))
            p = ball(vac, (x, y, 0), 1, scale=(1.75, 1.3, 1.05))
            base = p.scale.copy()

            def breathe(t, p=p, base=base, ph=(i + j) * 0.1):  # 失水收缩离开细胞壁，再吸水复原
                k = 0.72 + 0.26 * (0.5 + 0.5 * math.cos(2 * math.pi * (t + ph)))
                p.scale = base * k
            ink.on_frame(breathe)
    return cam((0, -9, 11), (0, 1.6, 0), 40)


# ---- 16 溶酶体 ----
def b_lysosome(scene):
    world(scene, key=(1, 0.9, 0.8), rim=(1, 0.8, 0.3))
    ball(gl("lyso_membrane", (1, 0.75, 0.3, 1), 0.3, alpha=0.3, rough=0.1), (0, 0, 0), 3.2)
    enz = gl("enzyme", (1, 0.7, 0.25, 1), 1.0)
    rnd = random.Random(7)
    es = [protein((rnd.uniform(-1.8, 1.8), rnd.uniform(-1.8, 1.8), rnd.uniform(-1.8, 1.8)), 0.4, enz, seed=k) for k in range(14)]
    drift(es, 0.25)
    old = group((0.6, 0, 0.3))  # 被吞进来的衰老细胞器，正被分解
    ball(gl("debris", (0.5, 0.35, 0.3, 1), 0.2), (0.6, 0, 0.3), 1.0, old, scale=(1.5, 0.9, 0.8))
    pulse(old, 0.1, 1)
    bits = [ball(gl("fragment", (0.7, 0.5, 0.4, 1), 0.4), (0, 0, 0), 0.12) for _ in range(10)]
    flow(bits, (0.6, 0, 0.3), (-2, 1.5, 1.5))
    return cam((0, -12, 1), (0, 0, 0), 45)


# ---- 17 ATP ----
def b_atp(scene):
    world(scene, key=(1, 0.95, 0.85), rim=(1, 0.7, 0.2))
    g = group()
    C = gl("carbon", (0.4, 0.42, 0.45, 1), 0.1, rough=0.25)
    N = gl("nitrogen", (0.3, 0.45, 1, 1), 0.3, rough=0.25)
    O = gl("oxygen", (1, 0.25, 0.2, 1), 0.3, rough=0.25)
    P = gl("phosphorus", (1, 0.6, 0.15, 1), 0.6, rough=0.25)
    bond = gl("bond", (0.8, 0.8, 0.85, 1), 0.1)

    def ring(center, n, r, mats, start=0.0):
        pts = []
        for k in range(n):
            a = start + k / n * 2 * math.pi
            p = Vector(center) + Vector((r * math.cos(a), 0, r * math.sin(a)))
            ball(mats[k % len(mats)], p, 0.32, g)
            pts.append(p)
        for a, b in zip(pts, pts[1:] + pts[:1]):
            stick(bond, a, b, 0.08, g)
        return pts
    six = ring((-6, 0, 1.2), 6, 1.2, [C, N])
    five = ring((-4.1, 0, 1.2), 5, 1.0, [C, N, C, N, C], math.pi)
    sugar = ring((-1.2, 0, -0.4), 5, 1.0, [C, C, C, O, C])
    stick(bond, five[2], sugar[2], 0.08, g)
    prev = sugar[0]
    groups = []
    for k in range(3):  # 三个磷酸基团
        c = Vector((1.4 + k * 2.2, 0, 0.3))
        pg = group(c)
        ball(P, c, 0.45, pg)
        for d in ((0, 0, 1), (0, 0, -1), (0, -1, 0)):
            o = c + Vector(d) * 0.9
            ball(O, o, 0.32, pg)
            stick(bond, c, o, 0.08, pg)
        stick(bond, prev, c, 0.08, g)
        ink.keep_parent(pg, g)
        groups.append(pg)
        prev = c
    glow = gl("energy", (1, 0.8, 0.3, 1), 10, alpha=0.6)
    spark = ball(glow, (6.6, 0, 0.3), 0.6, g)
    end, base = groups[-1], groups[-1].location.copy()

    def release(t):  # 末端磷酸基团离开、放能，再接回去
        u = 0.5 - 0.5 * math.cos(2 * math.pi * t)
        end.location = base + Vector((1.6 * u, 0, 0.5 * u))
        spark.scale = [0.2 + 1.6 * u] * 3
    ink.on_frame(release)
    g.rotation_euler = (math.radians(10), math.radians(-75), math.radians(-10))  # 竖屏：分子竖着放，磷酸基团在上
    sway(g, 0.08, "z")
    return cam((0, -24, 0.5), (0, 0, 0.5), 45)


# ---- 18 细胞核 ----
def b_nucleus(scene):
    world(scene, key=(0.9, 0.9, 1), rim=(0.5, 0.6, 1))
    env = gl("envelope", (0.4, 0.5, 1, 1), 0.2, alpha=0.35, rough=0.15)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=4, segments=64, ring_count=32)
    shell = bpy.context.active_object
    shell.data.materials.append(env)
    bisect = shell.modifiers.new("cut", "BOOLEAN")  # 切掉朝向镜头的一角，露出里面
    bpy.ops.mesh.primitive_cube_add(size=5, location=(2.5, -2.5, 2.5))
    cutter = bpy.context.active_object
    cutter.hide_render = True
    bisect.object = cutter
    bisect.operation = "DIFFERENCE"
    pore = gl("pore", (0.7, 0.85, 1, 1), 2)
    rnd = random.Random(3)
    pores = []
    for _ in range(40):
        d = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized()
        if d.x > 0.3 and d.y < -0.3 and d.z > 0.3:
            continue
        ink.prop(bpy.ops.mesh.primitive_torus_add, pore, None, major_radius=0.3, minor_radius=0.08, location=d * 4.02, rotation=d.to_track_quat("Z", "Y").to_euler())
        pores.append(d)
    chrom = gl("chromatin", (1, 0.5, 0.7, 1), 1.0)
    for k in range(7):
        pts = [(rnd.uniform(-3, 3), rnd.uniform(-3, 3), rnd.uniform(-3, 3)) for _ in range(8)]
        pts = [tuple(Vector(p).normalized() * min(Vector(p).length, 3.2)) for p in pts]
        tube(f"chromatin{k}", pts, 0.08, chrom)
    ball(gl("nucleolus", (1, 0.7, 0.4, 1), 1.5), (-0.6, 0.5, -0.4), 1.1)
    rna = gl("mrna_out", (0.5, 1, 0.7, 1), 3)
    for k, d in enumerate(pores[:4]):  # mRNA 从核孔出去
        flow([ball(rna, (0, 0, 0), 0.09) for _ in range(5)], d * 3.6, d * 7, seed=k)
    return cam((5, -10, 6), (0, 0, 0), 40)


# ---- 19 渗透作用 ----
def b_osmosis(scene):
    world(scene, key=(0.85, 0.95, 1), rim=(0.3, 0.7, 1))
    mem = gl("membrane", (0.75, 0.8, 0.9, 1), 0.2, alpha=0.6)
    ink.prop(bpy.ops.mesh.primitive_cube_add, mem, None, size=1, location=(0, 0, 0), scale=(0.5, 8, 8))
    chan = gl("aquaporin", (0.3, 0.8, 1, 1), 1.5)
    for z in (-2.5, 0, 2.5):
        ink.prop(bpy.ops.mesh.primitive_torus_add, chan, None, major_radius=0.45, minor_radius=0.12, location=(0, -1, z), rotation=(0, math.pi / 2, 0))
    h2o = gl("water", (0.5, 0.85, 1, 1), 2.5)
    for k, z in enumerate((-2.5, 0, 2.5)):  # 水分子穿过半透膜，从低浓度一侧到高浓度一侧
        flow([ball(h2o, (0, 0, 0), 0.13) for _ in range(6)], (-5, -1, z), (5, -1, z), seed=k)
    solute = gl("sucrose", (1, 0.75, 0.3, 1), 1.0)
    rnd = random.Random(2)
    big = [protein((rnd.uniform(1.5, 5.5), rnd.uniform(-3, 3), rnd.uniform(-3.5, 3.5)), 0.7, solute, seed=k) for k in range(9)]
    drift(big, 0.35)  # 蔗糖分子太大，过不了膜，只在一侧晃
    return cam((0, -14, 1.5), (0, 0, 0), 45)


# ---- 20 自由扩散 ----
def b_diffusion(scene):
    world(scene, key=(1, 0.95, 0.9), rim=(0.4, 0.8, 1))
    head = gl("head", (0.95, 0.75, 0.35, 1), 0.3)
    tail = gl("tail", (0.85, 0.8, 0.7, 1), 0.1, rough=0.5)
    bilayer(0, 0, 0, 22, 12, head, tail, seed=4)
    o = gl("o2", (1, 0.3, 0.25, 1), 1.5)
    c = gl("co2", (0.4, 0.4, 0.45, 1), 0.6)
    mols = []
    for k in range(10):  # O₂ 和 CO₂ 小分子直接穿过磷脂双分子层
        g = group()
        ball(o, (-0.18, 0, 0), 0.2, g)
        ball(o, (0.18, 0, 0), 0.2, g)
        if k % 2:
            ball(c, (0, 0, 0), 0.22, g)
            ball(o, (0.4, 0, 0), 0.2, g)
        mols.append(g)
    flow(mols[:5], (-2, -1, 6), (-1, -1, -6))
    flow(mols[5:], (2, -1, -6), (1.5, -1, 6), seed=1)
    return cam((0, -14, 4), (0, 0, 0), 40)


SCENES = {
    "bio-01": b_dna,
    "bio-02": b_replication,
    "bio-03": b_transcription,
    "bio-04": b_translation,
    "bio-05": b_meiosis,
    "bio-06": b_membrane,
    "bio-07": b_chloroplast,
    "bio-08": b_light,
    "bio-09": b_mito,
    "bio-10": b_synapse,
    "bio-11": b_action,
    "bio-12": b_rbc,
    "bio-13": b_phage,
    "bio-14": b_antibody,
    "bio-15": b_plasmolysis,
    "bio-16": b_lysosome,
    "bio-17": b_atp,
    "bio-18": b_nucleus,
    "bio-19": b_osmosis,
    "bio-20": b_diffusion,
}
