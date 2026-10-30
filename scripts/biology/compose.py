"""
把找来的生物图拼成 1080x2340 的首页背景：深色底，油画铺满屏宽压在下半屏，示意图放在中下部的圆角底板上。
原图按下面 items 里的链接下载（Commons 取 1280px 缩略图，PDB-101 取 png-800），放在本脚本旁的 cm/、gs/、tif/ 里，
文件名照 items 第二列。输出 bio-XX.jpg、bio-XX-full.jpg（点开看的整图）、bio-15.webp（质壁分离延时，正放接倒放）和 sources.json。
  python3 compose.py app/src/main/assets/biology [编号...]
"""
import json, os, subprocess, sys, tempfile
from PIL import Image

W, H = 1080, 2340
BG = (11, 20, 27)
src = os.path.dirname(os.path.abspath(__file__))
out = sys.argv[1]
os.makedirs(out, exist_ok=True)

G = "David S. Goodsell, RCSB PDB-101"
OS = "OpenStax"
B = "Blausen Medical (BruceBlaus)，WikiJournal of Medicine 2014，doi:10.15347/wjm/2014.010"
LH = "Mariana Ruiz (LadyofHats)"
C = "https://commons.wikimedia.org/wiki/File:"
GG = "https://pdb101.rcsb.org/sci-art/goodsell-gallery/"
# (编号, 来源文件, 摆法, 图注, 作者, 许可, 链接)
items = [
    (1, "cm/05.bin", "plate", "DNA 双螺旋：碱基互补配对", B, "CC BY 3.0", C + "Blausen_0322_DNA_2.png"),
    (2, "cm/09.bin", "plate", "DNA 复制：解旋后两条链各作模板", LH, "公有领域", C + "DNA_replication_en.svg"),
    (3, "cm/01.bin", "plate", "转录：RNA 聚合酶以一条链为模板合成 RNA", OS + "《Biology 2e》", "CC BY 4.0", C + "0325_Transcription.jpg"),
    (4, "cm/15.bin", "plate", "翻译：核糖体沿 mRNA 移动，tRNA 运来氨基酸", LH, "公有领域", C + "Ribosome_mRNA_translation_en.svg"),
    (5, "cm/x1.bin", "plate", "百合花粉母细胞减数分裂（显微照片）", "Josef Reischig", "CC BY-SA 3.0", C + "Meiosis_(261_33).jpg"),
    (6, "cm/08.bin", "plate", "细胞膜的流动镶嵌模型", LH, "公有领域", C + "Cell_membrane_detailed_diagram_en.svg"),
    (7, "tif/chloroplast.tif", "fill", "叶绿体内部：类囊体膜与基质里的分子", G + "，doi:10.2210/rcsb_pdb/goodsell-gallery-010", "CC BY 4.0", GG + "chloroplast"),
    (8, "cm/17.bin", "plate", "光反应：类囊体膜上的光系统与 ATP 合酶", "Somepics", "CC BY-SA 4.0", C + "Thylakoid_membrane_3.svg"),
    (9, "cm/06.bin", "plate", "线粒体：内膜折叠成嵴", B, "CC BY 3.0", C + "Blausen_0644_Mitochondria.png"),
    (10, "gs/excitatory-and-inhibitory-synapses.png", "fill", "突触：突触小泡里的神经递质", G + "，doi:10.2210/rcsb_pdb/goodsell-gallery-016", "CC BY 4.0", GG + "excitatory-and-inhibitory-synapses"),
    (11, "cm/04.bin", "plate", "动作电位：Na⁺ 内流、K⁺ 外流", B, "CC BY 3.0", C + "Blausen_0011_ActionPotential_Nerve.png"),
    (12, "cm/13.bin", "plate", "红细胞在高渗、等渗、低渗溶液中", LH, "公有领域", C + "Osmotic_pressure_on_blood_cells_diagram.svg"),
    (13, "gs/bacteriophage-t4-infection.png", "fill", "T4 噬菌体侵染大肠杆菌", G + "，doi:10.2210/rcsb_pdb/goodsell-gallery-048", "CC BY 4.0", GG + "bacteriophage-t4-infection"),
    (14, "gs/sars-cov-2-and-neutralizing-antibodies.png", "fill", "抗体（黄色）结合在病毒表面", G + "，doi:10.2210/rcsb_pdb/goodsell-gallery-025", "CC BY 4.0", GG + "sars-cov-2-and-neutralizing-antibodies"),
    (15, "cm/14.bin", "video", "紫色洋葱表皮细胞质壁分离及复原（实拍延时）", "Gabe Classon", "CC0", C + "Red_onion_plasmolysis_timelapse.webm"),
    (16, "gs/autophagy.png", "fill-trim", "自噬：溶酶体分解细胞内的旧结构", G + "，doi:10.2210/rcsb_pdb/goodsell-gallery-012", "CC BY 4.0", GG + "autophagy"),
    (17, "cm/x0.bin", "plate", "ATP 分子（按晶体结构绘制）", "Ben Mills", "公有领域", C + "ATP-xtal-3D-balls.png"),
    (18, "gs/biosites-nucleus.png", "fill", "细胞核内：染色质与核孔", G + "，doi:10.2210/rcsb_pdb/goodsell-gallery-004", "CC BY 4.0", GG + "biosites-nucleus"),
    (19, "cm/07.bin", "plate", "渗透：水透过半透膜进入溶液一侧", OS + "《Chemistry 2e》", "CC BY 4.0", C + "CNX_Chem_11_04_osmosis.png"),
    (20, "cm/00.bin", "plate", "自由扩散：小分子顺浓度梯度穿过磷脂双分子层", OS + "《Biology 2e》", "CC BY 4.0", C + "0305_Simple_Diffusion_Across_Plasma_Membrane.jpg"),
]
only = set(int(a) for a in sys.argv[2:])

def flat(im):
    # 透明底的矢量图先垫白，统一按白底处理（plate 里再把白去掉）。
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA"); bg = Image.new("RGBA", im.size, (255, 255, 255, 255)); bg.alpha_composite(im); im = bg
    return im.convert("RGB")

def cover(im):
    s = max(W / im.width, H / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    x, y = (im.width - W) // 2, (im.height - H) // 2
    return im.crop((x, y, x + W, y + H))

def wide(im, trim=0.0):
    # 油画：铺满屏宽，压在下半屏，上沿渐隐到深色底里。扫描件四周的纸边先裁掉（trim 为边距比例）。
    if trim:
        m = round(im.width * trim)
        im = im.crop((m, m, im.width - m, im.height - m))
    s = W / im.width
    im = im.resize((W, round(im.height * s)), Image.LANCZOS)
    h = min(im.height, int(H * 0.72))
    im = im.crop((0, (im.height - h) // 2, W, (im.height - h) // 2 + h))
    canvas = Image.new("RGB", (W, H), BG)
    y0 = H - h
    canvas.paste(im, (0, y0))
    fade = 260
    mask = Image.linear_gradient("L").resize((W, fade))
    canvas.paste(Image.new("RGB", (W, fade), BG), (0, y0), Image.eval(mask, lambda v: 255 - v))
    return canvas

def knockout(im):
    """去掉白底：接近白色的像素变透明；黑灰色的字和线条提亮成浅灰，放在深色底上看得见；彩色部分不动。"""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            lo, hi = min(r, g, b), max(r, g, b)
            if lo > 200:  # 白和近白：越白越透明
                a = a * max(0, 250 - lo) // 50
            elif hi - lo < 28 and hi < 140:  # 黑灰色字和线
                v = 235 - hi // 3
                r = g = b = v
            px[x, y] = (r, g, b, a)
    return im

def plate(im, knock=True):
    # 示意图放在中下部：首页卡片占左上，下面是 Learn/Review。不垫白底，直接放在深色底上；实拍照片不动。
    maxw, maxh = W - 2 * 40, int(H * 0.34)
    s = min(maxw / im.width, maxh / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    im = knockout(im) if knock else im.convert("RGBA")
    canvas = Image.new("RGBA", (W, H), BG + (255,))
    x0, cy = (W - im.width) // 2, int(H * 0.56)
    y0 = min(cy - im.height // 2, int(H * 0.72) - im.height)
    canvas.alpha_composite(im, (x0, y0))
    return canvas.convert("RGB")

def full(im, path, how="plate"):
    # 点开看的整图也放在深色底上。
    im = im.copy(); im.thumbnail((1400, 1400), Image.LANCZOS)
    if how == "plate":
        k = knockout(im); im = Image.new("RGBA", k.size, BG + (255,)); im.alpha_composite(k); im = im.convert("RGB")
    im.save(path, quality=85)

sources = []
for n, f, how, what, who, lic, url in items:
    name = "bio-%02d" % n
    sources.append({"image": "biology/%s.jpg" % name, "full": "biology/%s-full.jpg" % name, "what": what, "credit": who, "license": lic, "url": url})
    if only and n not in only: continue
    if how == "video":
        with tempfile.TemporaryDirectory() as d:
            subprocess.run(["ffmpeg", "-v", "error", "-i", os.path.join(src, f), "-vf", "fps=8", os.path.join(d, "%03d.png")], check=True)
            frames = sorted(os.listdir(d))
            seq = frames + frames[-2:0:-1]  # 质壁分离后倒放，就是复原
            paths = []
            for i, fr in enumerate(seq):
                p = os.path.join(d, "o%03d.png" % i)
                if not os.path.exists(p): plate(flat(Image.open(os.path.join(d, fr))), False).save(p)
                paths.append(p)
            plate(flat(Image.open(os.path.join(d, frames[0]))), False).save(os.path.join(out, name + ".jpg"), quality=82)
            full(flat(Image.open(os.path.join(d, frames[len(frames) // 2]))), os.path.join(out, name + "-full.jpg"), "video")
            subprocess.run(["img2webp", "-loop", "0", "-lossy", "-q", "70", "-m", "6", "-d", "125", *paths, "-o", os.path.join(out, name + ".webp")], check=True, capture_output=True)
        print(name, "video", len(seq)); continue
    im = flat(Image.open(os.path.join(src, f)))
    (wide(im, 0.07 if how == "fill-trim" else 0) if how.startswith("fill") else plate(im, how != "photo")).save(os.path.join(out, name + ".jpg"), quality=82)
    full(im, os.path.join(out, name + "-full.jpg"), how)
    print(name, how, im.size)
json.dump(sources, open(os.path.join(out, "sources.json"), "w"), ensure_ascii=False, indent=1)
