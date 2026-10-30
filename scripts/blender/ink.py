"""水墨场景的公共部分：纸色天空、皴法山石、山谷低雾、墨色人物、旗、渲染设置。

各个词的场景脚本 import 这个模块，只写构图。人物和马用 Quaternius 的 CC0 模型（scripts/blender/models/）。
"""
import math
import pathlib

import bmesh
import bpy
from mathutils import Vector, noise

MODELS = pathlib.Path(__file__).resolve().parent / "models"
PAPER = (0.95, 0.925, 0.875, 1)
INK = (0.008, 0.007, 0.006, 1)
RED = (0.42, 0.07, 0.04, 1)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    return bpy.context.scene


def flat(name, color, emission=0.0):
    """不反光的平涂材质：人马用浓墨，旗用朱砂。"""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = color
    bsdf.inputs["Roughness"].default_value = 1.0
    bsdf.inputs["Specular IOR Level"].default_value = 0.0
    if emission:
        bsdf.inputs["Emission Color"].default_value = color
        bsdf.inputs["Emission Strength"].default_value = emission
    return m


def rock(name, dark, light, scale, per_meter=None):
    """山石：噪声在浓墨和淡墨之间过渡，像皴法。

    默认按物体自身的包围盒取纹理坐标；很扁很大的地形会被拉成横条，这时给 per_meter（每米几个噪声周期）按实际尺寸取。
    """
    m = flat(name, light)
    nt = m.node_tree
    tex = nt.nodes.new("ShaderNodeTexNoise")
    tex.inputs["Scale"].default_value = scale if per_meter is None else per_meter
    if per_meter is not None:
        coord = nt.nodes.new("ShaderNodeTexCoord")
        nt.links.new(coord.outputs["Object"], tex.inputs["Vector"])
    tex.inputs["Detail"].default_value = 12
    tex.inputs["Roughness"].default_value = 0.65
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.35
    ramp.color_ramp.elements[0].color = dark
    ramp.color_ramp.elements[1].position = 0.7
    ramp.color_ramp.elements[1].color = light
    nt.links.new(tex.outputs["Fac"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], nt.nodes["Principled BSDF"].inputs["Base Color"])
    return m


def mountains(name, width, depth, y, peak, scale, seed, mat, res=(260, 90)):
    """一重山：脊状分形噪声高度场，中间高、前后落下，山头尖、山腰陡。"""
    nx, ny = res
    bm = bmesh.new()
    rows = []
    for j in range(ny + 1):
        row = []
        for i in range(nx + 1):
            x = (i / nx - 0.5) * width
            v = j / ny
            yy = y + (v - 0.5) * depth
            envelope = math.sin(math.pi * v) ** 1.5
            n = noise.ridged_multi_fractal(Vector((x / scale + seed * 13.1, yy / scale, seed)), 0.9, 2.1, 6, 0.9, 2.2, noise_basis="PERLIN_ORIGINAL")
            row.append(bm.verts.new((x, yy, peak * envelope * max(n - 0.45, 0) ** 1.4)))
        rows.append(row)
    for j in range(ny):
        for i in range(nx):
            bm.faces.new((rows[j][i], rows[j][i + 1], rows[j + 1][i + 1], rows[j + 1][i]))
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    mesh.materials.append(mat)
    for f in mesh.polygons:
        f.use_smooth = True
    return obj


def ground(size=(200, 160), y=40, mat=None):
    bpy.ops.mesh.primitive_grid_add(x_subdivisions=120, y_subdivisions=120, size=1)
    g = bpy.context.active_object
    g.name = "ground"
    g.scale = (size[0], size[1], 1)
    g.location = (0, y, 0)
    tex = bpy.data.textures.new("ground_tex", type="CLOUDS")
    tex.noise_scale = 0.6
    d = g.modifiers.new("disp", "DISPLACE")
    d.texture = tex
    d.strength = 1.2
    d.texture_coords = "GLOBAL"
    g.data.materials.append(mat or rock("ground", (0.35, 0.34, 0.32, 1), (0.78, 0.76, 0.71, 1), 14))
    bpy.ops.object.shade_smooth()
    return g


def valley_mist(y=260, depth=400, height=32, density=0.022, color=PAPER):
    """山谷低雾：一个体积块，密度随高度减小，山脚埋在雾里、山头露出来。"""
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, y, height / 2))
    mist = bpy.context.active_object
    mist.name = "mist"
    mist.scale = (1000, depth, height)
    m = bpy.data.materials.new("mist")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.remove(nt.nodes["Principled BSDF"])
    vol = nt.nodes.new("ShaderNodeVolumePrincipled")
    vol.inputs["Color"].default_value = color
    vol.inputs["Anisotropy"].default_value = 0.1
    coord = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(coord.outputs["Generated"], sep.inputs["Vector"])
    rng = nt.nodes.new("ShaderNodeMapRange")
    rng.inputs["To Min"].default_value = density
    rng.inputs["To Max"].default_value = 0.0
    nt.links.new(sep.outputs["Z"], rng.inputs["Value"])
    nt.links.new(rng.outputs["Result"], vol.inputs["Density"])
    nt.links.new(vol.outputs["Volume"], nt.nodes["Material Output"].inputs["Volume"])
    mist.data.materials.append(m)
    return mist


def paper_world(scene):
    world = bpy.data.worlds.new("paper")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = PAPER
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.0
    bpy.ops.object.light_add(type="SUN", location=(0, 0, 50))
    sun = bpy.context.active_object
    sun.data.energy = 2.2
    sun.data.angle = math.radians(20)
    sun.rotation_euler = (math.radians(55), 0, math.radians(-40))


def character(file, height, action, ink, frame=None):
    """导入一个带骨骼的 CC0 模型，统一换成墨色，缩放到 height 米，播放指定动作。

    frame 不为空时定格在动作的这一帧（比如“坐”的姿势）。
    返回（外层空物体，骨架，动作片段）；位置和朝向设在外层空物体上，模型面朝 -Y。
    """
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(MODELS / file))
    new = [o for o in bpy.data.objects if o not in before]
    for o in new:
        if o.type == "MESH" and o.parent is None:
            bpy.data.objects.remove(o)
    new = [o for o in bpy.data.objects if o not in before]
    arm = next(o for o in new if o.type == "ARMATURE")
    for o in new:
        if o.type == "MESH":
            o.data.materials.clear()
            o.data.materials.append(ink)
        if o.type == "EMPTY" and o.parent is None:
            arm.parent = None
    act = next(a for a in bpy.data.actions if a.name.endswith(action) and "|" in a.name)
    arm.animation_data_create()
    arm.animation_data.action = None
    track = arm.animation_data.nla_tracks.new()
    start, end = act.frame_range
    strip = track.strips.new(action, int(start), act)
    if frame is not None:
        strip.action_frame_start = frame
        strip.action_frame_end = frame + 1
        strip.repeat = 500
    # 按身高缩放：以骨架的世界包围盒为准。
    bpy.context.view_layer.update()
    meshes = [o for o in new if o.type == "MESH"]
    zs = [(o.matrix_world @ Vector(c)).z for o in meshes for c in o.bound_box]
    k = height / max(max(zs) - min(zs), 1e-6)
    root = arm
    while root.parent is not None:
        root = root.parent
    root.scale = tuple(s * k for s in root.scale)
    # 外面套一个空物体管位置和朝向，不动模型自带的坐标轴转换。
    holder = bpy.data.objects.new(file + "_holder", None)
    bpy.context.collection.objects.link(holder)
    root.parent = holder
    bpy.context.view_layer.update()
    return holder, arm, strip


def saddle(holder):
    """马背最凹处（马鞍）的世界坐标：沿马身从前肩到后胯切片，取背部最低的一片。

    马的模型面朝 -Y；在外层空物体的局部坐标里量，和马的朝向无关。
    """
    bpy.context.view_layer.update()
    deps = bpy.context.evaluated_depsgraph_get()
    to_local = holder.matrix_world.inverted()
    pts = []
    for o in holder.children_recursive:
        if o.type != "MESH":
            continue
        ev = o.evaluated_get(deps)
        for v in ev.to_mesh().vertices:
            pts.append(to_local @ (o.matrix_world @ v.co))
        ev.to_mesh_clear()
    ys = [p.y for p in pts]
    y0, y1 = min(ys), max(ys)
    # 只看身体中段（去掉头颈和臀尾）。低多边形模型顶点稀，有的切片只取到身侧偏下的点，
    # 所以先取上包络（每片和左右相邻两片里的最高点），再在包络上找最低处。
    lo, hi = y0 + (y1 - y0) * 0.3, y0 + (y1 - y0) * 0.8
    n = 18  # 每片约 8 厘米，再细会有太多空片
    step = (hi - lo) / n
    tops = []
    for k in range(n):
        z = [p.z for p in pts if lo + k * step <= p.y < lo + (k + 1) * step]
        tops.append(max(z) if z else None)
    envelope = []
    for k in range(n):
        near = [t for t in tops[max(k - 1, 0):k + 2] if t is not None]
        envelope.append(max(near) if near else None)
    k = min((k for k in range(n) if envelope[k] is not None), key=lambda k: envelope[k])
    best = Vector((0, lo + (k + 0.5) * step, envelope[k]))
    return holder.matrix_world @ Vector((0, best.y, best.z))


def duplicate(root):
    """复制整个人物（骨架和网格），网格数据共享，动作各自独立。"""
    bpy.ops.object.select_all(action="DESELECT")
    stack = [root]
    while stack:
        o = stack.pop()
        o.select_set(True)
        stack.extend(o.children)
    bpy.context.view_layer.objects.active = root
    bpy.ops.object.duplicate(linked=True)
    dup = next(o for o in bpy.context.selected_objects if o.parent is None)
    arm = next(o for o in dup.children_recursive if o.type == "ARMATURE")
    return dup, arm


def attach(obj, arm, bone, offset=(0, 0, 0), rotation=(0, 0, 0)):
    """把道具挂到骨骼上，随动作一起动。"""
    obj.parent = arm
    obj.parent_type = "BONE"
    obj.parent_bone = bone
    obj.location = offset
    obj.rotation_euler = rotation


def flag(x, y, z, pole_height, size, mat_pole, mat_flag, name):
    """旗杆加一面网格旗，飘动交给 wave_flags。返回（旗杆，旗面）。"""
    bpy.ops.mesh.primitive_cylinder_add(radius=0.04, depth=pole_height, location=(x, y, z + pole_height / 2), vertices=8)
    pole = bpy.context.active_object
    pole.data.materials.append(mat_pole)
    bpy.ops.mesh.primitive_grid_add(x_subdivisions=20, y_subdivisions=8, size=1, location=(x + size * 0.75, y, z + pole_height - size * 0.35))
    f = bpy.context.active_object
    f.name = name
    f.scale = (size * 1.5, size * 0.7, 1)
    f.rotation_euler = (math.pi / 2, 0, 0)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)  # 只烘焙缩放，位置和朝向留在物体上
    f.data.materials.append(mat_flag)
    f["base"] = [c for v in f.data.vertices for c in v.co]
    return pole, f


def wave_flags(flags):
    """每帧按 sin 重算旗面，一个循环正好两个波，首尾无缝。"""
    def update(t):
        for k, f in enumerate(flags):
            base = list(f["base"])
            xs = base[0::3]
            x0, x1 = min(xs), max(xs)
            for i, v in enumerate(f.data.vertices):
                bx, by, bz = base[3 * i:3 * i + 3]
                u = (bx - x0) / (x1 - x0)
                v.co = (bx, by, bz + 0.22 * (x1 - x0) * u * math.sin(2 * math.pi * (t * 2 - u * 1.3 + k * 0.17)))
            f.data.update()
    on_frame(update)


def camera(location, target, lens):
    bpy.ops.object.camera_add(location=location)
    cam = bpy.context.active_object
    cam.data.lens = lens
    cam.rotation_euler = (Vector(target) - cam.location).to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.camera = cam
    return cam


def render_settings(scene, width=1080, height=2340, samples=64):
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items] else "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = samples
    # 体积雾的格子沿深度分布：不设自定义范围时按相机裁剪面铺开，格子太粗，远处的雾会渗到近处的人马身上。
    if hasattr(scene.eevee, "use_volume_custom_range"):
        scene.eevee.use_volume_custom_range = True
    scene.eevee.volumetric_start = 4
    scene.eevee.volumetric_end = 600
    scene.eevee.volumetric_samples = 128
    scene.eevee.volumetric_tile_size = "4"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.render.resolution_x = width
    scene.render.resolution_y = height
    scene.render.use_lock_interface = True
    scene.render.image_settings.file_format = "PNG"


# ---- 场景里常用的几样：朝向、挂件、骑马、逐帧动画 ----

LOOP = 50  # 循环动画 50 帧（2 秒，25 帧/秒）；人走一步 12.5 帧，两个步伐周期正好一圈


def yaw_toward(direction):
    """让面朝 -Y 的模型转向 direction（水平方向）。"""
    return math.atan2(direction.y, direction.x) + math.pi / 2


def keep_parent(obj, parent):
    """挂到 parent 下面，世界坐标不变。"""
    bpy.context.view_layer.update()
    mw = obj.matrix_world.copy()
    obj.parent = parent
    obj.matrix_world = mw


def prop(add, mat, parent=None, **kwargs):
    """调用一个 primitive_*_add，上材质，可选挂到 parent 下。返回新物体。"""
    add(**kwargs)
    o = bpy.context.active_object
    o.data.materials.append(mat)
    if parent is not None:
        keep_parent(o, parent)
    return o


def mount(horse, horse_arm, rider, rider_arm):
    """让骑手（坐姿）的臀部落在马背最凹处，挂到马的 Torso 骨骼上随马起伏。两者先设好朝向。"""
    seat = saddle(horse)
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


def bone_point(arm, bone, tail=False):
    bpy.context.view_layer.update()
    b = arm.pose.bones[bone]
    return arm.matrix_world @ (b.tail if tail else b.head)


_updates = []


def on_frame(fn):
    """注册逐帧更新函数 fn(t)，t 是循环里的进度 0~1；用 sin(2πt·k) 之类保证首尾相接。"""
    _updates.append(fn)
    if len(_updates) == 1:
        bpy.app.handlers.frame_change_pre.append(lambda scene, *_: [f(scene.frame_current / LOOP) for f in _updates])
    fn(0.0)


def sway(obj, axis, amp, phase=0.0, cycles=1):
    """让物体绕自身某轴来回摆动（芦苇、柳枝），首尾无缝。"""
    base = obj.rotation_euler.copy()
    i = "xyz".index(axis)

    def update(t):
        r = base.copy()
        r[i] += amp * math.sin(2 * math.pi * (t * cycles + phase))
        obj.rotation_euler = r
    on_frame(update)


def bob(obj, amp, phase=0.0, cycles=1):
    """上下起伏（船随波）。"""
    base = obj.location.copy()

    def update(t):
        obj.location = base + Vector((0, 0, amp * math.sin(2 * math.pi * (t * cycles + phase))))
    on_frame(update)


# ---- 景物：日、雾带、河、崖、飞鸟 ----

def emissive(name, color, strength=1.0):
    """自发光、不受光影的颜色：太阳、天光。"""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.remove(nt.nodes["Principled BSDF"])
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = color
    em.inputs["Strength"].default_value = strength
    nt.links.new(em.outputs["Emission"], nt.nodes["Material Output"].inputs["Surface"])
    return m


def sun(location, radius, color=(0.75, 0.16, 0.06, 1)):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius, location=location, segments=48, ring_count=24)
    s = bpy.context.active_object
    s.name = "sun"
    s.data.materials.append(emissive("sun", color, 1.0))
    s.visible_shadow = False
    return s


def mist_band(center, size, z0, z1, density=0.05, scale=0.02, drift=6.0, color=PAPER):
    """一条横在山腰的雾带（半山居雾若带）：z0~z1 之间中间浓、上下淡，噪声成团，每个循环绕一个小圈飘动。"""
    cx, cy = center
    bpy.ops.mesh.primitive_cube_add(size=1, location=(cx, cy, (z0 + z1) / 2))
    band = bpy.context.active_object
    band.name = "mist_band"
    band.scale = (size[0], size[1], z1 - z0)
    m = bpy.data.materials.new("mist_band")
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.remove(nt.nodes["Principled BSDF"])
    vol = nt.nodes.new("ShaderNodeVolumePrincipled")
    vol.inputs["Color"].default_value = color
    gen = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(gen.outputs["Generated"], sep.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")  # 竖直方向：两头 0，中间 1
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (0, 0, 0, 1)
    ramp.color_ramp.elements[1].position = 0.45
    ramp.color_ramp.elements[1].color = (1, 1, 1, 1)
    e = ramp.color_ramp.elements.new(1.0)
    e.color = (0, 0, 0, 1)
    nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    mapping = nt.nodes.new("ShaderNodeMapping")
    nt.links.new(gen.outputs["Object"], mapping.inputs["Vector"])
    tex = nt.nodes.new("ShaderNodeTexNoise")
    tex.inputs["Scale"].default_value = 1.0
    tex.inputs["Detail"].default_value = 6
    mapping.inputs["Scale"].default_value = (size[0] * scale, size[1] * scale, (z1 - z0) * scale * 3)
    nt.links.new(mapping.outputs["Vector"], tex.inputs["Vector"])
    clump = nt.nodes.new("ShaderNodeMapRange")  # 噪声 0.4 以下为空，成一团一团
    clump.inputs["From Min"].default_value = 0.42
    clump.inputs["From Max"].default_value = 0.65
    nt.links.new(tex.outputs["Fac"], clump.inputs["Value"])
    mul = nt.nodes.new("ShaderNodeMath")
    mul.operation = "MULTIPLY"
    nt.links.new(ramp.outputs["Color"], mul.inputs[0])
    nt.links.new(clump.outputs["Result"], mul.inputs[1])
    mul2 = nt.nodes.new("ShaderNodeMath")
    mul2.operation = "MULTIPLY"
    mul2.inputs[1].default_value = density
    nt.links.new(mul.outputs["Value"], mul2.inputs[0])
    nt.links.new(mul2.outputs["Value"], vol.inputs["Density"])
    nt.links.new(vol.outputs["Volume"], nt.nodes["Material Output"].inputs["Volume"])
    band.data.materials.append(m)
    k = drift / max(size[0], 1)  # Object 坐标是 -1~1，把米换算过去

    def update(t):
        a = 2 * math.pi * t
        mapping.inputs["Location"].default_value = (k * math.cos(a), k * 0.4 * math.sin(a), 0)
    on_frame(update)
    return band


def ribbon(name, points, width, z, mat):
    """沿折线铺一条带子（河、路），points 是平面上的点。"""
    bm = bmesh.new()
    prev = None
    for i, p in enumerate(points):
        a = Vector(points[max(i - 1, 0)])
        b = Vector(points[min(i + 1, len(points) - 1)])
        d = (b - a).normalized()
        n = Vector((-d.y, d.x)) * (width if not callable(width) else width(i / (len(points) - 1))) / 2
        l = bm.verts.new((p[0] + n.x, p[1] + n.y, z))
        r = bm.verts.new((p[0] - n.x, p[1] - n.y, z))
        if prev:
            bm.faces.new((prev[0], prev[1], r, l))
        prev = (l, r)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    mesh.materials.append(mat)
    return obj


def cliff(name, center, radius, height, seed, mat, res=120, rough=0.12, peaked=False):
    """一座孤立的崖或山头：圆形包络，顶上平缓、四周陡（peaked 时是尖顶），表面脊状噪声。"""
    cx, cy = center
    bm = bmesh.new()
    rows = []
    for j in range(res + 1):
        row = []
        for i in range(res + 1):
            x = (i / res - 0.5) * radius * 2.4
            y = (j / res - 0.5) * radius * 2.4
            r = math.hypot(x, y) / radius
            env = max(1 - r, 0) ** 1.3 if peaked else max(1 - r ** 4, 0) ** 0.5  # peaked：尖顶的山；否则顶上平缓能站人
            n = noise.ridged_multi_fractal(Vector((x / radius * 2.5 + seed, y / radius * 2.5, seed)), 0.9, 2.1, 5, 0.9, 2.2, noise_basis="PERLIN_ORIGINAL")
            row.append(bm.verts.new((cx + x, cy + y, height * env * (1 - rough + rough * min(max(n, 0), 1.2)) - 1.5 * (1 - env))))
        rows.append(row)
    for j in range(res):
        for i in range(res):
            bm.faces.new((rows[j][i], rows[j][i + 1], rows[j + 1][i + 1], rows[j + 1][i]))
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    mesh.materials.append(mat)
    for f in mesh.polygons:
        f.use_smooth = True
    return obj


def surface_z(obj, x, y):
    """物体表面在 (x, y) 处的高度，用于让人站在崖上。"""
    bpy.context.view_layer.update()
    inv = obj.matrix_world.inverted()
    hit, loc, *_ = obj.ray_cast(inv @ Vector((x, y, 1000)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
    return (obj.matrix_world @ loc).z if hit else 0.0


def birds(center, radius, z, count, size, mat, cycles=1):
    """几只远处的鸟绕圈飞，翅膀扇动；每个循环绕 cycles 圈，首尾无缝。"""
    flock = []
    for k in range(count):
        bm = bmesh.new()
        c = bm.verts.new((0, 0, 0))
        tip_l = bm.verts.new((-size, -size * 0.25, 0))
        tip_r = bm.verts.new((size, -size * 0.25, 0))
        mid_l = bm.verts.new((-size * 0.45, size * 0.1, 0))
        mid_r = bm.verts.new((size * 0.45, size * 0.1, 0))
        tail = bm.verts.new((0, size * 0.3, 0))
        bm.faces.new((c, mid_l, tip_l))
        bm.faces.new((c, tip_r, mid_r))
        bm.faces.new((c, mid_r, tail, mid_l))
        mesh = bpy.data.meshes.new(f"bird{k}")
        bm.to_mesh(mesh)
        o = bpy.data.objects.new(f"bird{k}", mesh)
        bpy.context.collection.objects.link(o)
        mesh.materials.append(mat)
        flock.append(o)

    def update(t):
        for k, o in enumerate(flock):
            a = 2 * math.pi * (t * cycles + k / count * 0.35)
            r = radius * (1 + 0.15 * math.sin(k * 2.1))
            o.location = (center[0] + r * math.cos(a), center[1] + r * math.sin(a) * 0.6, z + k * size * 0.8 + 0.6 * size * math.sin(a * 2 + k))
            o.rotation_euler = (0, 0, a)  # 鸟头沿切线（模型朝 -Y）
            flap = math.sin(2 * math.pi * (t * 8 + k * 0.3))
            o.scale = (1, 1, 1)
            o.data.vertices[1].co.z = o.data.vertices[2].co.z = size * 0.5 * flap
            o.data.vertices[3].co.z = o.data.vertices[4].co.z = size * 0.2 * flap
            o.data.update()
    on_frame(update)
    return flock


def snowy(mat, line, soft=6.0, flat_from=0.45):
    """给山石材质加积雪：高于 line 米、坡度平缓的地方盖白，边缘用噪声打散（苍山负雪）。"""
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    rock_color = bsdf.inputs["Base Color"].links[0].from_socket
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    sep_p = nt.nodes.new("ShaderNodeSeparateXYZ")
    sep_n = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Position"], sep_p.inputs["Vector"])
    nt.links.new(geo.outputs["Normal"], sep_n.inputs["Vector"])
    tex = nt.nodes.new("ShaderNodeTexNoise")
    tex.inputs["Scale"].default_value = 0.15
    tex.inputs["Detail"].default_value = 8
    nt.links.new(geo.outputs["Position"], tex.inputs["Vector"])
    jitter = nt.nodes.new("ShaderNodeMath")  # 雪线上下抖动
    jitter.operation = "MULTIPLY_ADD"
    jitter.inputs[1].default_value = soft * 3
    jitter.inputs[2].default_value = -soft * 1.5
    nt.links.new(tex.outputs["Fac"], jitter.inputs[0])
    h = nt.nodes.new("ShaderNodeMath")
    h.operation = "ADD"
    nt.links.new(sep_p.outputs["Z"], h.inputs[0])
    nt.links.new(jitter.outputs["Value"], h.inputs[1])
    by_height = nt.nodes.new("ShaderNodeMapRange")
    by_height.inputs["From Min"].default_value = line - soft
    by_height.inputs["From Max"].default_value = line + soft
    nt.links.new(h.outputs["Value"], by_height.inputs["Value"])
    by_slope = nt.nodes.new("ShaderNodeMapRange")
    by_slope.inputs["From Min"].default_value = flat_from
    by_slope.inputs["From Max"].default_value = flat_from + 0.25
    nt.links.new(sep_n.outputs["Z"], by_slope.inputs["Value"])
    amount = nt.nodes.new("ShaderNodeMath")
    amount.operation = "MULTIPLY"
    nt.links.new(by_height.outputs["Result"], amount.inputs[0])
    nt.links.new(by_slope.outputs["Result"], amount.inputs[1])
    mix = nt.nodes.new("ShaderNodeMix")
    mix.data_type = "RGBA"
    mix.inputs["B"].default_value = (0.97, 0.965, 0.95, 1)
    nt.links.new(amount.outputs["Value"], mix.inputs["Factor"])
    nt.links.new(rock_color, mix.inputs["A"])
    nt.links.new(mix.outputs["Result"], bsdf.inputs["Base Color"])
    return mat
