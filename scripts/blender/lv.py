"""「率」（率领）：蹑足行伍之间……率疲弊之卒，将数百之众，转而攻秦（《过秦论》）。

用 Blender 渲染：远近三重山（噪声位移地形）加体积雾，山谷里一队持矛步卒，
领头的将军骑马举红旗。整体按水墨处理：纸色天空、墨色灰阶、雾把远山融进纸里，只有旗用朱砂红。

运行：/Applications/Blender.app/Contents/MacOS/Blender -b -P scripts/blender/lv.py -- out.png
"""
import math
import sys

import bpy
from mathutils import Vector

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "/tmp/lv.png"
PAPER = (0.95, 0.925, 0.875, 1)
INK = (0.008, 0.007, 0.006, 1)
RED = (0.42, 0.07, 0.04, 1)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene


def material(name, color, rough=0.9, emission=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = color
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Specular IOR Level"].default_value = 0.0
    if emission:
        bsdf.inputs["Emission Color"].default_value = color
        bsdf.inputs["Emission Strength"].default_value = emission
    return m


def ink_rock(name, dark, light, scale):
    """山石材质：按噪声和朝向在浓墨和淡墨之间变化，像皴法。"""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = 1.0
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 12
    noise.inputs["Roughness"].default_value = 0.65
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.35
    ramp.color_ramp.elements[0].color = dark
    ramp.color_ramp.elements[1].position = 0.7
    ramp.color_ramp.elements[1].color = light
    nt.links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    return m


def terrain(name, width, depth, y, peak, scale, seed, mat, res=(260, 90)):
    """一重山：脊状分形噪声生成的高度场，中间高、前后两边落下去，山头尖、山腰陡，像国画里的山。"""
    import bmesh
    from mathutils import noise
    nx, ny = res
    bm = bmesh.new()
    verts = []
    for j in range(ny + 1):
        row = []
        for i in range(nx + 1):
            x = (i / nx - 0.5) * width
            v = j / ny
            yy = y + (v - 0.5) * depth
            envelope = math.sin(math.pi * v) ** 1.5
            n = noise.ridged_multi_fractal(Vector((x / scale + seed * 13.1, yy / scale, seed)), 0.9, 2.1, 6, 0.9, 2.2, noise_basis="PERLIN_ORIGINAL")
            h = peak * envelope * max(n - 0.45, 0) ** 1.4
            row.append(bm.verts.new((x, yy, h)))
        verts.append(row)
    for j in range(ny):
        for i in range(nx):
            bm.faces.new((verts[j][i], verts[j][i + 1], verts[j + 1][i + 1], verts[j + 1][i]))
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    mesh.materials.append(mat)
    for f in mesh.polygons:
        f.use_smooth = True
    return obj


rock_far = ink_rock("rock_far", (0.3, 0.3, 0.3, 1), (0.7, 0.69, 0.66, 1), 3)
rock_mid = ink_rock("rock_mid", (0.1, 0.1, 0.095, 1), (0.5, 0.49, 0.46, 1), 5)
rock_near = ink_rock("rock_near", (0.07, 0.065, 0.06, 1), (0.38, 0.37, 0.35, 1), 8)

terrain("far", 800, 140, 320, 150, 130, 3, rock_far)
terrain("mid", 460, 90, 165, 85, 70, 7, rock_mid)
terrain("near_left", 180, 50, 66, 34, 30, 1, rock_near, res=(160, 60))

# 山谷地面：平缓起伏。
bpy.ops.mesh.primitive_grid_add(x_subdivisions=120, y_subdivisions=120, size=1)
ground = bpy.context.active_object
ground.scale = (200, 160, 1)
ground.location = (0, 40, 0)
gtex = bpy.data.textures.new("ground_tex", type="CLOUDS")
gtex.noise_scale = 0.6
gd = ground.modifiers.new("disp", "DISPLACE")
gd.texture = gtex
gd.strength = 1.6
gd.texture_coords = "GLOBAL"
ground.data.materials.append(ink_rock("ground", (0.35, 0.34, 0.32, 1), (0.78, 0.76, 0.71, 1), 14))
bpy.ops.object.shade_smooth()

ink = material("ink", INK)
red = material("red", RED, emission=0.15)


def cylinder(r, depth, loc, rot=(0, 0, 0), mat=ink, verts=10):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=depth, location=loc, rotation=rot, vertices=verts)
    o = bpy.context.active_object
    o.data.materials.append(mat)
    return o


def sphere(r, loc, scale=(1, 1, 1), mat=ink):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=loc, segments=16, ring_count=8)
    o = bpy.context.active_object
    o.scale = scale
    o.data.materials.append(mat)
    return o


def join(objs, name):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = name
    return o


def soldier():
    """一名持矛步卒，原点在脚底，面朝 +x。"""
    parts = [
        cylinder(0.11, 0.85, (0.06, 0.1, 0.42), rot=(0, 0.25, 0)),
        cylinder(0.11, 0.85, (-0.06, -0.1, 0.42), rot=(0, -0.25, 0)),
        cylinder(0.24, 0.75, (0, 0, 1.2), verts=12),
        sphere(0.26, (0, 0, 1.45), scale=(1.1, 1.2, 0.6)),
        sphere(0.15, (0.02, 0, 1.75)),
    ]
    bpy.ops.mesh.primitive_cone_add(radius1=0.2, depth=0.22, location=(0.02, 0, 1.92), vertices=12)
    helmet = bpy.context.active_object
    helmet.data.materials.append(ink)
    parts.append(helmet)
    parts.append(cylinder(0.025, 3.2, (0.35, -0.25, 1.9), rot=(0, 0.28, 0), verts=6))
    bpy.ops.mesh.primitive_cone_add(radius1=0.06, depth=0.3, location=(0.78, -0.25, 3.45), rotation=(0, 0.28, 0), vertices=6)
    tip = bpy.context.active_object
    tip.data.materials.append(ink)
    parts.append(tip)
    return join(parts, "soldier")


def flag_pole(x, y, z, height, size, phase):
    pole = cylinder(0.04, height, (x, y, z + height / 2), verts=8)
    bpy.ops.mesh.primitive_grid_add(x_subdivisions=16, y_subdivisions=8, size=1, location=(x + size * 0.75, y, z + height - size * 0.35))
    flag = bpy.context.active_object
    flag.scale = (size * 1.5, size * 0.7, 1)
    flag.rotation_euler = (math.pi / 2, 0, 0.1 * phase)
    bpy.ops.object.transform_apply(scale=True)
    wave = flag.modifiers.new("wave", "WAVE")
    wave.use_normal = True
    wave.height = size * 0.18
    wave.width = size * 0.7
    wave.speed = 0.08
    wave.time_offset = phase * 30
    wave.start_position_x = x
    flag.data.materials.append(red)
    return pole, flag


def horse_and_rider(loc):
    """骑马的将军，面朝 +x。用基本形拼，放在中景，靠体量和姿态读出来。"""
    x, y, z = loc
    parts = [
        sphere(1.0, (x, y, z + 1.55), scale=(1.25, 0.48, 0.55)),
        cylinder(0.26, 1.1, (x + 1.25, y, z + 2.15), rot=(0, 0.75, 0)),
        sphere(0.3, (x + 1.65, y, z + 2.55), scale=(1.5, 0.6, 0.65)),
        cylinder(0.11, 1.2, (x + 0.85, y + 0.25, z + 0.6), rot=(0, -0.12, 0)),
        cylinder(0.11, 1.2, (x + 0.75, y - 0.25, z + 0.6), rot=(0, 0.2, 0)),
        cylinder(0.11, 1.2, (x - 0.85, y + 0.25, z + 0.6), rot=(0, 0.2, 0)),
        cylinder(0.11, 1.2, (x - 0.95, y - 0.25, z + 0.6), rot=(0, -0.15, 0)),
        cylinder(0.08, 1.0, (x - 1.45, y, z + 1.45), rot=(0, -0.5, 0)),
        cylinder(0.24, 0.85, (x - 0.05, y, z + 2.45)),
        sphere(0.17, (x - 0.02, y, z + 3.05)),
        sphere(0.34, (x - 0.1, y, z + 2.1), scale=(1.2, 1.5, 0.55)),
    ]
    bpy.ops.mesh.primitive_cone_add(radius1=0.22, depth=0.3, location=(x - 0.02, y, z + 3.27), vertices=12)
    helm = bpy.context.active_object
    helm.data.materials.append(ink)
    parts.append(helm)
    return join(parts, "general")


template = soldier()
soldiers = []
# 两路纵队，从画面左后方沿山谷走向右前方。
for i in range(26):
    for col in (-0.9, 0.9):
        t = i / 25
        p = Vector((-26 + t * 30, 30 - t * 12 + col, 0))
        o = template.copy()
        o.data = template.data
        o.location = p + Vector((math.sin(i * 1.7) * 0.25, math.cos(i * 2.3) * 0.2, 0))
        o.rotation_euler = (0, 0, -0.38)
        bpy.context.collection.objects.link(o)
        soldiers.append(o)
bpy.data.objects.remove(template)

for i, (x, y) in enumerate([(-20, 27.6), (-11, 24), (-2, 20.4)]):
    flag_pole(x, y + 1.6, 0, 4.6, 1.0, i * 0.4)

general = horse_and_rider((7.5, 16.5, 0))
general.rotation_euler = (0, 0, -0.38)
flag_pole(7.6, 15.6, 0, 6.6, 1.5, 1.1)

# 让所有东西落到地面上：给地面高度做个粗略补偿（地面起伏不到 2 米，人马统一抬 0.6 米）。
for o in bpy.data.objects:
    if o.type == "MESH" and o.name not in {"far", "mid", "near_left", "mist"} and not o.name.startswith("Grid"):
        o.location.z += 0.6

# 世界：纸色背景，加体积雾让远山淡入纸色。
world = bpy.data.worlds.new("paper")
scene.world = world
world.use_nodes = True
wn = world.node_tree
wn.nodes["Background"].inputs["Color"].default_value = PAPER
wn.nodes["Background"].inputs["Strength"].default_value = 1.0

# 山谷低雾：一个大体积块，密度随高度减小，山脚埋在雾里、山头露出来。
bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 260, 16))
mist = bpy.context.active_object
mist.name = "mist"
mist.scale = (1000, 400, 32)
mm = bpy.data.materials.new("mist")
mm.use_nodes = True
nt = mm.node_tree
nt.nodes.remove(nt.nodes["Principled BSDF"])
vol = nt.nodes.new("ShaderNodeVolumePrincipled")
vol.inputs["Color"].default_value = PAPER
vol.inputs["Anisotropy"].default_value = 0.1
coord = nt.nodes.new("ShaderNodeTexCoord")
sep = nt.nodes.new("ShaderNodeSeparateXYZ")
nt.links.new(coord.outputs["Generated"], sep.inputs["Vector"])
ramp = nt.nodes.new("ShaderNodeMapRange")
ramp.inputs["From Min"].default_value = 0.0
ramp.inputs["From Max"].default_value = 1.0
ramp.inputs["To Min"].default_value = 0.022
ramp.inputs["To Max"].default_value = 0.0
nt.links.new(sep.outputs["Z"], ramp.inputs["Value"])
nt.links.new(ramp.outputs["Result"], vol.inputs["Density"])
nt.links.new(vol.outputs["Volume"], nt.nodes["Material Output"].inputs["Volume"])
mist.data.materials.append(mm)

bpy.ops.object.light_add(type="SUN", location=(0, 0, 50))
sun = bpy.context.active_object
sun.data.energy = 2.2
sun.data.angle = math.radians(20)
sun.rotation_euler = (math.radians(55), 0, math.radians(-40))

# 竖屏整幅：上方留纸色天空写字，中间群山，下方山谷里的队伍在 Learn 卡片上面。
bpy.ops.object.camera_add(location=(-4, -24, 14))
cam = bpy.context.active_object
cam.data.lens = 22
direction = Vector((2, 30, 12)) - cam.location
cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items] else "BLENDER_EEVEE"
scene.eevee.taa_render_samples = 64
scene.eevee.volumetric_end = 600
scene.eevee.volumetric_tile_size = "4"
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.render.resolution_x = 1080
scene.render.resolution_y = 2340
scene.render.filepath = OUT
scene.render.image_settings.file_format = "PNG"
bpy.ops.render.render(write_still=True)
print("rendered", OUT)
