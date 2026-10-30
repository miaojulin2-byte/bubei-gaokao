"""生成文言实词插图（水墨剪影风格的 SVG），输出到 app/src/main/assets/illustrations/。

每幅图对应一个实词卡片和其中一个例句（svg 根节点的 data-example），画的是这个例句里的词义。
画布 400x300，底边贴着首页的 Learn/Review 卡片上沿。动画写在 <g data-anim> 上，见 ui/Illustration.kt。
"""
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/illustrations"
INK = "#26231f"
RED = "#b8492f"


def svg(example, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300" data-example="{example}">\n'
            f'{body}\n</svg>\n')


def g(body, anim=None, amp=None, period=None, phase=None, cx=None, cy=None, **style):
    attrs = []
    for k, v in [("data-anim", anim), ("data-amp", amp), ("data-period", period), ("data-phase", phase), ("data-cx", cx), ("data-cy", cy)]:
        if v is not None:
            attrs.append(f'{k}="{v}"')
    for k, v in style.items():
        attrs.append(f'{k.replace("_", "-")}="{v}"')
    return f'<g {" ".join(attrs)}>{body}</g>'


def ground(y=262, opacity=0.8, back=0.22):
    """前景地面：一道浅墨远坡加一道深墨近坡。"""
    return (f'<path d="M0 {y - 18} Q 110 {y - 34} 210 {y - 22} T 400 {y - 26} L400 300 L0 300 Z" fill="{INK}" opacity="{back}"/>'
            f'<path d="M0 {y} Q 120 {y - 10} 220 {y - 2} T 400 {y - 6} L400 300 L0 300 Z" fill="{INK}" opacity="{opacity}"/>')


def soldier(x, y, s=1.0, phase=0.0, opacity=0.9):
    """持矛步卒，朝右走。(x, y) 是脚底中点。"""
    def p(dx, dy):
        return f"{x + dx * s:.1f} {y + dy * s:.1f}"
    body = (f'<circle cx="{x:.1f}" cy="{y - 32 * s:.1f}" r="{5 * s:.1f}"/>'
            f'<path d="M{p(-5, -26)} L{p(5, -26)} L{p(7, -11)} L{p(-7, -11)} Z"/>'
            f'<line x1="{x - 3 * s:.1f}" y1="{y - 11 * s:.1f}" x2="{x - 6 * s:.1f}" y2="{y:.1f}" stroke="{INK}" stroke-width="{2.6 * s:.1f}"/>'
            f'<line x1="{x + 3 * s:.1f}" y1="{y - 11 * s:.1f}" x2="{x + 6 * s:.1f}" y2="{y:.1f}" stroke="{INK}" stroke-width="{2.6 * s:.1f}"/>'
            f'<line x1="{x + 4 * s:.1f}" y1="{y - 12 * s:.1f}" x2="{x + 14 * s:.1f}" y2="{y - 64 * s:.1f}" stroke="{INK}" stroke-width="{1.6 * s:.1f}"/>'
            f'<polygon points="{x + 14 * s:.1f},{y - 72 * s:.1f} {x + 11.5 * s:.1f},{y - 63 * s:.1f} {x + 16.5 * s:.1f},{y - 64 * s:.1f}"/>')
    return g(body, anim="march", amp=2, period=1.4, phase=phase, fill=INK, opacity=opacity)


def horse(x, y, s=1.0, gallop=False, opacity=0.92):
    """朝右的马，(x, y) 是前后蹄之间的地面点。"""
    def p(dx, dy):
        return f"{x + dx * s:.1f} {y + dy * s:.1f}"
    legs = ([(26, -28, 40, -6), (18, -26, 30, 0), (-18, -26, -36, -8), (-26, -30, -46, -14)] if gallop
            else [(26, -28, 30, 0), (18, -26, 16, 0), (-18, -28, -22, 0), (-26, -30, -34, -2)])
    leg_svg = "".join(
        f'<line x1="{x + a * s:.1f}" y1="{y + b * s:.1f}" x2="{x + c * s:.1f}" y2="{y + d * s:.1f}" stroke="{INK}" stroke-width="{4.2 * s:.1f}"/>'
        for a, b, c, d in legs)
    return (f'<ellipse cx="{x:.1f}" cy="{y - 36 * s:.1f}" rx="{34 * s:.1f}" ry="{14 * s:.1f}"/>'
            f'<path d="M{p(24, -44)} Q {p(34, -62)} {p(40, -74)} L{p(50, -70)} Q {p(44, -56)} {p(34, -34)} Z"/>'
            f'<path d="M{p(40, -74)} L{p(60, -66)} Q {p(64, -62)} {p(58, -59)} L{p(44, -62)} Z"/>'
            f'<polygon points="{x + 43 * s:.1f},{y - 74 * s:.1f} {x + 45 * s:.1f},{y - 82 * s:.1f} {x + 48 * s:.1f},{y - 73 * s:.1f}"/>'
            f'<path d="M{p(38, -74)} Q {p(28, -70)} {p(24, -56)}" fill="none" stroke="{INK}" stroke-width="{3 * s:.1f}"/>'
            f'<path d="M{p(-33, -42)} Q {p(-50, -38)} {p(-48, -16)}" fill="none" stroke="{INK}" stroke-width="{4 * s:.1f}"/>'
            + leg_svg)


def rider(x, y, s=1.0):
    """马上的人，(x, y) 是马背上坐的位置。"""
    def p(dx, dy):
        return f"{x + dx * s:.1f} {y + dy * s:.1f}"
    return (f'<path d="M{p(-4, 0)} L{p(-10, -26)} L{p(4, -30)} L{p(10, -2)} Z"/>'
            f'<circle cx="{x - 2 * s:.1f}" cy="{y - 37 * s:.1f}" r="{7 * s:.1f}"/>'
            f'<polygon points="{x - 10 * s:.1f},{y - 39 * s:.1f} {x - 2 * s:.1f},{y - 50 * s:.1f} {x + 6 * s:.1f},{y - 39 * s:.1f}"/>'
            f'<line x1="{x + 6 * s:.1f}" y1="{y:.1f}" x2="{x + 10 * s:.1f}" y2="{y + 18 * s:.1f}" stroke="{INK}" stroke-width="{5 * s:.1f}"/>')


def flag(x, y, color=RED, s=1.0, period=1.6):
    """旗杆顶端 (x, y) 处一面飘动的旗。"""
    body = (f'<path d="M{x} {y} L {x + 34 * s:.1f} {y + 6 * s:.1f} L {x + 28 * s:.1f} {y + 16 * s:.1f} '
            f'L {x + 34 * s:.1f} {y + 26 * s:.1f} L {x - 1:.1f} {y + 24 * s:.1f} Z" fill="{color}"/>')
    return g(body, anim="flutter", amp=10, period=period, cx=x, cy=y)


def scholar(x, y, s=1.0, opacity=0.92):
    """站立的读书人，朝右；(x, y) 是脚底。"""
    def p(dx, dy):
        return f"{x + dx * s:.1f} {y + dy * s:.1f}"
    return (f'<circle cx="{x:.1f}" cy="{y - 50 * s:.1f}" r="{7 * s:.1f}"/>'
            f'<circle cx="{x - 4 * s:.1f}" cy="{y - 57 * s:.1f}" r="{3.2 * s:.1f}"/>'
            f'<path d="M{p(-6, -42)} Q {p(-10, -16)} {p(-16, 0)} L{p(12, 0)} Q {p(8, -18)} {p(6, -42)} Z"/>')


def seated(x, y, s=1.0):
    """盘腿坐着的人，朝右；(x, y) 是坐处。"""
    def p(dx, dy):
        return f"{x + dx * s:.1f} {y + dy * s:.1f}"
    return (f'<circle cx="{x:.1f}" cy="{y - 30 * s:.1f}" r="{6 * s:.1f}"/>'
            f'<circle cx="{x - 3.5 * s:.1f}" cy="{y - 36 * s:.1f}" r="{2.8 * s:.1f}"/>'
            f'<path d="M{p(-6, -23)} L{p(6, -23)} L{p(9, -6)} L{p(-8, -6)} Z"/>'
            f'<path d="M{p(-12, 0)} Q {p(0, -10)} {p(16, -2)} L{p(14, 2)} L{p(-12, 2)} Z"/>')


# ---- 率：率领。蹑足行伍之间……率疲弊之卒，将数百之众，转而攻秦（《过秦论》） ----
def lv():
    back = "".join(soldier(40 + i * 30, 238, s=0.75, phase=i * 0.13, opacity=0.45) for i in range(6))
    front = "".join(soldier(30 + i * 34, 258, s=1.0, phase=0.5 + i * 0.11) for i in range(6))
    leader = g(horse(300, 258) + rider(298, 214) +
               f'<line x1="312" y1="208" x2="320" y2="112" stroke="{INK}" stroke-width="2.6"/>' + flag(320, 112),
               anim="bob", amp=1.5, period=1.4, fill=INK, opacity=0.95)
    return svg(2, ground() + back + front + leader)


# ---- 望：远望。望晚日照城郭，汶水、徂徕如画（《登泰山记》） ----
def wang():
    city = ("".join(f'<rect x="{220 + i * 9}" y="214" width="5" height="5"/>' for i in range(18))
            + '<rect x="220" y="218" width="166" height="16"/>'
            + '<rect x="288" y="202" width="26" height="16"/><path d="M278 204 Q 301 198 324 204 L 316 196 L 286 196 Z"/>')
    sun = g(f'<circle cx="330" cy="170" r="20" fill="{RED}" opacity="0.8"/>', anim="rise", amp=10, period=16)
    river = f'<path d="M180 252 Q 280 240 400 246 L 400 256 Q 290 252 186 262 Z" fill="{INK}" opacity="0.18"/>'
    cliff = f'<path d="M0 300 L0 196 Q 60 186 120 190 L 150 196 Q 160 230 175 300 Z" fill="{INK}" opacity="0.88"/>'
    person = g(scholar(126, 190) +
               f'<path d="M131 146 L 143 140 L 138 136" fill="none" stroke="{INK}" stroke-width="3"/>' +
               g(f'<path d="M120 152 Q 104 160 98 174" fill="none" stroke="{INK}" stroke-width="4"/>', anim="sway", amp=6, period=3, cx=120, cy=152),
               fill=INK)
    return svg(2, sun + g(city, fill=INK, opacity=0.38) + river + cliff + person)


# ---- 负：背着。及既上，苍山负雪，明烛天南（《登泰山记》） ----
def porter(x, y, s=1.0):
    """背着柴捆、拄杖上山的樵夫，朝右；(x, y) 是脚底。"""
    def p(dx, dy):
        return f"{x + dx * s:.1f} {y + dy * s:.1f}"
    load = (f'<path d="M{p(-26, -46)} L{p(-2, -58)} L{p(4, -40)} L{p(-20, -28)} Z" fill="{INK}"/>'
            + "".join(f'<line x1="{x + (-30 + k * 3) * s:.1f}" y1="{y + (-44 + k * -2.2) * s:.1f}" x2="{x + (6 + k * 3) * s:.1f}" y2="{y + (-62 + k * -2.2) * s:.1f}" stroke="{INK}" stroke-width="{1.6 * s:.1f}"/>' for k in range(4)))
    body = (f'<circle cx="{x + 8 * s:.1f}" cy="{y - 50 * s:.1f}" r="{6.5 * s:.1f}"/>'
            f'<path d="M{p(-4, -44)} Q {p(8, -46)} {p(10, -36)} L{p(8, -18)} L{p(-6, -18)} Z"/>'
            f'<line x1="{x - 2 * s:.1f}" y1="{y - 18 * s:.1f}" x2="{x - 8 * s:.1f}" y2="{y:.1f}" stroke="{INK}" stroke-width="{3.4 * s:.1f}"/>'
            f'<line x1="{x + 5 * s:.1f}" y1="{y - 18 * s:.1f}" x2="{x + 10 * s:.1f}" y2="{y:.1f}" stroke="{INK}" stroke-width="{3.4 * s:.1f}"/>'
            f'<line x1="{x + 10 * s:.1f}" y1="{y - 34 * s:.1f}" x2="{x + 24 * s:.1f}" y2="{y + 2 * s:.1f}" stroke="{INK}" stroke-width="{2 * s:.1f}"/>')
    return load + body


def fu():
    mountain = (f'<path d="M120 262 L 220 128 L 252 154 L 296 100 L 400 262 Z" fill="{INK}" opacity="0.6"/>'
                '<path d="M220 128 L 204 150 L 216 147 L 226 158 L 236 145 L 252 154 L 242 138 Z" fill="#ffffff" opacity="0.95"/>'
                '<path d="M296 100 L 274 128 L 288 125 L 296 138 L 306 124 L 320 132 L 312 114 Z" fill="#ffffff" opacity="0.95"/>')
    path = f'<path d="M40 288 Q 120 270 200 262" fill="none" stroke="#f2ecdf" stroke-width="6" opacity="0.5"/>'
    return svg(0, mountain + ground(270, 0.85, 0.15) + path + g(porter(110, 272, 1.5), anim="march", amp=3, period=2.4, fill=INK))


# ---- 乘：骑。乘纤离之马，建翠凤之旗（《谏逐客书》） ----
def cheng():
    dust = "".join(f'<circle cx="{110 - i * 16}" cy="{262 - (i % 2) * 5}" r="{6 - i}" fill="{INK}" opacity="{0.18 - i * 0.03:.2f}"/>' for i in range(5))
    rider_group = g(horse(220, 262, s=1.35, gallop=True) + rider(214, 204, s=1.2) +
                    f'<line x1="228" y1="198" x2="236" y2="92" stroke="{INK}" stroke-width="3"/>' + flag(236, 92, color="#3e7a6a", s=1.2, period=0.9),
                    anim="bob", amp=4, period=0.6, fill=INK)
    return svg(2, ground(266, 0.75, 0.15) + g(dust, anim="march", amp=6, period=0.6) + rider_group)


# ---- 徐：慢慢地。清风徐来，水波不兴（《赤壁赋》） ----
def xu():
    water = "".join(f'<path d="M{20 + i * 60} {244 + (i % 3) * 14} q 20 -2 40 0" fill="none" stroke="{INK}" stroke-width="1.4" opacity="0.25"/>' for i in range(7))
    boat = (f'<path d="M150 236 Q 210 254 276 232 Q 214 246 150 236 Z" fill="{INK}"/>'
            f'<path d="M168 236 L 252 234 L 248 226 L 172 228 Z" fill="{INK}" opacity="0.85"/>'
            + seated(196, 230, 0.9) + seated(232, 228, 0.9))
    reeds = "".join(
        g(f'<path d="M{20 + i * 9} 300 Q {16 + i * 9} {230 - (i % 3) * 12} {26 + i * 10} {180 - (i % 4) * 14}" fill="none" stroke="{INK}" stroke-width="2" opacity="0.7"/>'
          f'<ellipse cx="{26 + i * 10}" cy="{184 - (i % 4) * 14}" rx="3" ry="9" fill="{INK}" opacity="0.7"/>',
          anim="sway", amp=4, period=5, phase=i * 0.08, cx=20 + i * 9, cy=300)
        for i in range(7))
    return svg(0, water + g(boat, anim="bob", amp=2, period=5, fill=INK) + reeds)


# ---- 坐：坐着。与子颍坐日观亭，待日出（《登泰山记》） ----
def zuo():
    sun = g(f'<circle cx="330" cy="236" r="24" fill="{RED}" opacity="0.85"/>', anim="rise", amp=26, period=14, phase=0.75)
    clouds = "".join(g(f'<ellipse cx="{300 + i * 30}" cy="{246 + i * 8}" rx="80" ry="7" fill="#f2ecdf" opacity="0.95"/>', anim="march", amp=12, period=10 + i * 3, phase=i * 0.3) for i in range(3))
    peak = f'<path d="M0 300 L 0 220 Q 60 196 120 200 Q 180 206 230 300 Z" fill="{INK}" opacity="0.88"/>'
    pavilion = (f'<path d="M48 168 Q 100 152 152 168 L 140 156 Q 100 144 60 156 Z"/>'
                f'<polygon points="96,146 100,136 104,146"/>'
                f'<rect x="58" y="168" width="4" height="34"/><rect x="138" y="168" width="4" height="34"/>'
                f'<rect x="52" y="200" width="96" height="5"/>')
    people = seated(88, 200, 0.85) + seated(116, 200, 0.85)
    return svg(1, sun + clouds + peak + g(pavilion + people, fill=INK))


ART = {
    "gk-shi-078": lv,     # 率
    "gk-shi-087": wang,   # 望
    "gk-shi-024": fu,     # 负
    "gk-shi-012": cheng,  # 乘
    "gk-shi-098": xu,     # 徐
    "gk-shi-120": zuo,    # 坐
}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for card_id, draw in ART.items():
        (OUT / f"{card_id}.svg").write_text(draw(), encoding="utf-8")
    print(f"wrote {len(ART)} illustrations to {OUT}")
