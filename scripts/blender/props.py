"""词义画里反复用到的人物装束和景物：长袍、兵器、松、柳、雨、麻雀、静水、石路。

ink.py 管画风（材质、山、雾、人物导入），这里管“画什么”；场景在 words.py。
"""
import math
import random

import bpy
from mathutils import Vector

import ink

STRIDE = 3.35  # 1.75 米的人走一个循环（两步周期）前进的距离，地面卷动的周期要和它一致，脚才不打滑


def robe(man, arm, mat, k=1.0, length=1.0, hat=True):
    """长袍下摆和方巾。length 是袍子从腰到脚的比例，走路的人用短一点、窄一点，露出迈开的腿。"""
    loc = man.location.copy()
    depth = 1.0 * k * length
    flare = 0.42 if length >= 1 else 0.3
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, man, radius1=flare * k, radius2=0.2 * k, depth=depth, location=loc + Vector((0, 0, 1.05 * k - depth / 2)), vertices=16)
    if hat:
        head = ink.bone_point(arm, "Head", tail=True)
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, man, radius=0.1 * k, depth=0.28 * k, location=head + Vector((0, 0, 0.02)), vertices=10)


def armed(root, heading, side, mat):
    """兵卒：头盔、短袍下摆、扛在肩上的长矛。"""
    loc = root.location
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, root, radius1=0.17, depth=0.22, location=loc + Vector((0, 0, 1.8)), vertices=14)
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, root, radius1=0.36, radius2=0.2, depth=0.5, location=loc + Vector((0, 0, 0.72)), vertices=16)
    base = loc + side * 0.22 - heading * 0.1 + Vector((0, 0, 0.3))
    d = (heading * 0.45 + Vector((0, 0, 1))).normalized()
    rot = d.to_track_quat("Z", "Y").to_euler()
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, root, radius=0.04, depth=3.4, location=base + d * 1.7, rotation=rot, vertices=6)
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, root, radius1=0.06, depth=0.32, location=base + d * 3.54, rotation=rot, vertices=8)


def person(x, y, z, toward, mat, action="Man_Idle", height=1.75, phase=0, frame=None, speed=None):
    """一个人：导入、定位、朝向，动作按 LOOP 对齐。返回（外层空物体，骨架，片段）。"""
    man, arm, strip = ink.character("man.glb", height, action, mat, frame=frame)
    if frame is None:
        length = strip.action.frame_range[1] - strip.action.frame_range[0]
        strip.scale = speed or (ink.LOOP / length if length > ink.LOOP else ink.LOOP / 2 / length)
        strip.repeat = 40
        strip.frame_start = -phase
    man.location = (x, y, z)
    man.rotation_euler = (0, 0, ink.yaw_toward(toward))
    bpy.context.view_layer.update()
    return man, arm, strip


def seat(man, arm, point):
    """让坐姿的人臀部落在 point 上。"""
    bpy.context.view_layer.update()
    hips = arm.matrix_world @ arm.pose.bones["Hips"].head
    man.location += Vector(point) - hips
    bpy.context.view_layer.update()


def pose_part(arm, action, frame, bones):
    """把另一个动作里某一帧的几根骨骼叠在当前动作上（坐着举杯：坐姿 + 出拳那一帧的右臂）。"""
    src = next(a for a in bpy.data.actions if a.name.endswith(action) and "|" in a.name)
    act = src.copy()
    act.name = src.name + "_part"
    for layer in act.layers:
        for st in layer.strips:
            for bag in st.channelbags:
                for fc in list(bag.fcurves):
                    if not any(f'"{b}"' in fc.data_path for b in bones):
                        bag.fcurves.remove(fc)
    track = arm.animation_data.nla_tracks.new()
    strip = track.strips.new(action + "_part", 0, act)
    strip.action_frame_start = frame
    strip.action_frame_end = frame + 1
    strip.repeat = 500
    return strip


def water(y=150, size=(800, 400), color=(0.35, 0.35, 0.34, 1), z=0.2):
    """静水面：低粗糙度，打开光线追踪后能倒映岸和船。"""
    bpy.context.scene.eevee.use_raytracing = True
    m = bpy.data.materials.new("water")
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = color
    b.inputs["Roughness"].default_value = 0.08
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, y, z))
    w = bpy.context.active_object
    w.scale = (size[0], size[1], 1)
    w.data.materials.append(m)
    return w


def pine(x, y, z, h, mat, seed=0):
    """松：微弯的干，几层压扁的云片状树冠，冠子随风轻摆。"""
    rnd = random.Random(seed)
    bpy.ops.object.empty_add(location=(x, y, z))
    pivot = bpy.context.active_object
    lean = rnd.uniform(-0.25, 0.25)
    for i in range(3):
        u0, u1 = i / 3, (i + 1) / 3
        p0 = Vector((x + lean * h * u0 ** 2, y, z + h * u0))
        p1 = Vector((x + lean * h * u1 ** 2, y, z + h * u1))
        d = p1 - p0
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, pivot, vertices=8, radius=h * 0.035 * (1 - u0 * 0.6), depth=d.length, location=(p0 + p1) / 2, rotation=d.to_track_quat("Z", "Y").to_euler())
    top = Vector((x + lean * h, y, z + h))
    for i in range(rnd.randint(4, 6)):
        side = rnd.uniform(-1, 1)
        c = top + Vector((side * h * 0.35, rnd.uniform(-0.15, 0.15) * h, -i * h * 0.13))
        r = h * rnd.uniform(0.07, 0.11)
        for j in range(4):  # 一片针叶：几小团挤在一起，边缘参差
            off = Vector((rnd.uniform(-1.4, 1.4) * r, rnd.uniform(-0.8, 0.8) * r, rnd.uniform(-0.25, 0.25) * r))
            ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, mat, pivot, subdivisions=1, radius=r * rnd.uniform(0.8, 1.2), location=c + off, scale=(1.3, 1.0, 0.55))
        stem = Vector((x + lean * h, y, c.z))  # 从干上横伸出去托住这一片
        d = c - stem
        if d.length > 0.05:
            ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, pivot, vertices=6, radius=h * 0.012, depth=d.length, location=(c + stem) / 2, rotation=d.to_track_quat("Z", "Y").to_euler())
    ink.sway(pivot, "x", 0.012, rnd.random())
    return pivot


def willow(x, y, z, h, mat, seed=0, strands=60, wind=0.06):
    """柳：短粗的干，几根斜伸的枝，冠上一团叶；细长的柳条从冠下垂，上粗下尖，各自随风摆。"""
    rnd = random.Random(seed)
    leaf = ink.flat("willow_leaf", (0.22, 0.22, 0.2, 1))
    ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, None, vertices=10, radius=h * 0.05, depth=h * 0.6, location=(x, y, z + h * 0.3))
    crown = Vector((x, y, z + h * 0.62))
    for i in range(4):
        a = i * math.pi / 2 + rnd.uniform(-0.4, 0.4)
        d = Vector((math.cos(a) * 0.6, math.sin(a) * 0.6, 0.8)).normalized()
        L = h * rnd.uniform(0.3, 0.45)
        ink.prop(bpy.ops.mesh.primitive_cylinder_add, mat, None, vertices=6, radius=h * 0.018, depth=L, location=crown + d * L / 2, rotation=d.to_track_quat("Z", "Y").to_euler())
    for i in range(7):  # 冠顶的叶团
        a = rnd.uniform(0, 2 * math.pi)
        r = h * rnd.uniform(0.05, 0.3)
        ink.prop(bpy.ops.mesh.primitive_ico_sphere_add, leaf, None, subdivisions=1, radius=h * rnd.uniform(0.09, 0.14), location=crown + Vector((math.cos(a) * r, math.sin(a) * r * 0.7, h * rnd.uniform(0.25, 0.4))), scale=(1.4, 1, 0.6))
    for k in range(strands):
        a = rnd.uniform(0, 2 * math.pi)
        r = h * rnd.uniform(0.1, 0.45)
        top = crown + Vector((math.cos(a) * r, math.sin(a) * r * 0.7, h * rnd.uniform(0.2, 0.35)))
        L = h * rnd.uniform(0.3, 0.75)
        bpy.ops.object.empty_add(location=top)
        pivot = bpy.context.active_object
        ink.prop(bpy.ops.mesh.primitive_cone_add, leaf, pivot, vertices=4, radius1=0.014 * h, radius2=0.0, depth=L, location=top - Vector((0, 0, L / 2)), rotation=(math.pi, 0, 0))
        ink.sway(pivot, "y", wind * rnd.uniform(0.6, 1.2), rnd.random() * 0.3)


def rain(center, size, top, count, mat, slant=0.25, seed=2, cycles=3, radius=0.02, length=1.6):
    """斜雨：细线从 top 落到 0，落到底从顶上重来；每个循环落 cycles 次，首尾无缝。"""
    rnd = random.Random(seed)
    drops = []
    for _ in range(count):
        bpy.ops.mesh.primitive_cylinder_add(vertices=4, radius=radius, depth=length)
        o = bpy.context.active_object
        o.data.materials.append(mat)
        o.visible_shadow = False
        o.rotation_euler = (0, slant, 0)
        drops.append((o, center[0] + rnd.uniform(-0.5, 0.5) * size[0], center[1] + rnd.uniform(-0.5, 0.5) * size[1], rnd.random()))

    def update(t):
        for o, x, y, ph in drops:
            u = (t * cycles + ph) % 1
            o.location = (x - slant * top * u, y, top * (1 - u))
    ink.on_frame(update)


def sparrow(x, y, z, heading, mat, phase=0.0):
    """一只啄食的麻雀：身子、头、喙、尾，整只低头啄地、抬头，循环里啄几下。"""
    bpy.ops.object.empty_add(location=(x, y, z))
    pivot = bpy.context.active_object
    pivot.rotation_euler = (0, 0, ink.yaw_toward(heading))
    s = 0.09
    brown = ink.flat("sparrow", (0.3, 0.24, 0.18, 1))
    ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, brown, pivot, segments=12, ring_count=8, radius=s, location=(x, y, z + s), scale=(0.75, 1.3, 0.75))
    ink.prop(bpy.ops.mesh.primitive_uv_sphere_add, mat, pivot, segments=10, ring_count=6, radius=s * 0.55, location=(x, y - s * 1.1, z + s * 1.6))
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, pivot, vertices=6, radius1=s * 0.18, depth=s * 0.5, location=(x, y - s * 1.75, z + s * 1.55), rotation=(math.pi / 2, 0, 0))
    ink.prop(bpy.ops.mesh.primitive_cone_add, mat, pivot, vertices=4, radius1=s * 0.4, depth=s * 1.4, location=(x, y + s * 1.5, z + s * 1.2), rotation=(-math.pi / 2 - 0.4, 0, 0), scale=(1, 0.3, 1))
    base = pivot.rotation_euler.copy()

    def update(t):
        u = (t * 4 + phase) % 1  # 每个循环啄四下：前 30% 低头，其余抬头张望
        peck = math.sin(math.pi * u / 0.3) if u < 0.3 else 0.0
        r = base.copy()
        r.x = -0.7 * peck
        r.z = base.z + 0.25 * math.sin(2 * math.pi * (t + phase))
        pivot.rotation_euler = r
    ink.on_frame(update)
    return pivot


def road(y, width, mat_road, mat_line, period=STRIDE, span=12):
    """一条沿 x 方向的石板路：路面不动，横向的石缝每 period/5 一道，随人走动往后卷，首尾无缝。"""
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, y, 0.03))
    r = bpy.context.active_object
    r.scale = (400, width, 1)
    r.data.materials.append(mat_road)
    scroll = bpy.data.objects.new("road_scroll", None)
    bpy.context.collection.objects.link(scroll)
    step = period / 5
    for k in range(int(-span * 5), int(span * 5) + 1):
        ink.prop(bpy.ops.mesh.primitive_cube_add, mat_line, scroll, size=1, location=(k * step, y, 0.04), scale=(0.03, width, 0.01))

    def update(t):
        scroll.location.x = -period * t
    ink.on_frame(update)
    return scroll
