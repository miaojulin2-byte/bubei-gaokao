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


def rock(name, dark, light, scale):
    """山石：噪声在浓墨和淡墨之间过渡，像皴法。"""
    m = flat(name, light)
    nt = m.node_tree
    tex = nt.nodes.new("ShaderNodeTexNoise")
    tex.inputs["Scale"].default_value = scale
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


def valley_mist(y=260, depth=400, height=32, density=0.022):
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
    vol.inputs["Color"].default_value = PAPER
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
    bpy.ops.object.transform_apply(scale=True)
    f.data.materials.append(mat_flag)
    f["base"] = [c for v in f.data.vertices for c in v.co]
    return pole, f


def wave_flags(flags, loop):
    """每帧按 sin 重算旗面，周期正好是 loop 帧，循环时无缝。"""
    def update(scene, *_):
        t = scene.frame_current / loop
        for k, f in enumerate(flags):
            base = list(f["base"])
            xs = base[0::3]
            x0, x1 = min(xs), max(xs)
            for i, v in enumerate(f.data.vertices):
                bx, by, bz = base[3 * i:3 * i + 3]
                u = (bx - x0) / (x1 - x0)
                v.co = (bx, by, bz + 0.22 * (x1 - x0) * u * math.sin(2 * math.pi * (t * 2 - u * 1.3 + k * 0.17)))
            f.data.update()
    bpy.app.handlers.frame_change_pre.append(update)
    update(bpy.context.scene)


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
    scene.eevee.volumetric_end = 600
    scene.eevee.volumetric_tile_size = "4"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.render.resolution_x = width
    scene.render.resolution_y = height
    scene.render.use_lock_interface = True
    scene.render.image_settings.file_format = "PNG"
