#!/usr/bin/env python3
"""把旧版（v0.1.1）的内容资源转换成新版应用使用的紧凑卡片格式。

用法：python3 scripts/prepare_content.py <旧版 android/assets 目录>
旧版资源可从提交 a8cf597 取出：git archive a8cf597 android/assets | tar -x -C /tmp/legacy
"""
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets"

# 只收现行统编版高中语文五册；旧人教版选修和初中篇目不收。
WENYAN_ORDER = ["必修上", "必修下", "选择性必修上", "选择性必修中", "选择性必修下"]


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def decks_in(cards, order):
    counts = {}
    for card in cards:
        counts[card["d"]] = counts.get(card["d"], 0) + 1
    keys = sorted(counts, key=lambda k: order.index(k) if k in order else len(order))
    return [{"id": k, "title": k, "count": counts[k]} for k in keys]


def wenyan(src):
    library = load(src / "wenyan/wenyan-library.json")["texts"]
    texts = {t["id"]: t for t in library}
    texts.update({t["legacy_id"]: t for t in library if t.get("legacy_id")})
    cards = []
    for card in load(src / "wenyan/word-cards.json")["cards"]:
        contexts = card.get("contexts", [])
        text = next(texts[c["text_id"]] for c in contexts if c["text_id"] in texts)
        if text["volume"] not in WENYAN_ORDER:
            continue
        order = min(c.get("curriculum_order", 999) for c in contexts) if contexts else 999
        seen, examples = set(), []
        for c in contexts:
            sentence = c["sentence"].strip()
            if sentence in seen:
                continue
            seen.add(sentence)
            examples.append([c["title"], sentence, c.get("meaning", "")])
        cards.append({
            "id": card["id"], "d": text["volume"], "o": order,
            "w": card["word"], "p": card.get("pos", ""), "x": examples[:4],
            # 原数据把 ⑩ 以后的序号写成 ○10，统一成圈码。
            "g": re.sub(r"○(\d+)", lambda m: chr(0x2460 + int(m.group(1)) - 1), card["gloss"]),
        })
    cards.sort(key=lambda c: (WENYAN_ORDER.index(c["d"]), c["o"]))
    write("wenyan.json", {"decks": decks_in(cards, WENYAN_ORDER), "cards": cards})
    return len(cards)


def sentence_units(text):
    """按高考默写的粒度切句：以句号、问号、叹号、分号断开；超长句再按逗号切成约 20 字一块；过短的并入下一句。"""
    units = []
    for sentence in re.findall(r"[^。！？；]+[。！？；」”’]*", text):
        sentence = sentence.strip()
        if not sentence:
            continue
        if len(sentence) <= 24:
            units.append(sentence)
            continue
        current = ""
        for clause in re.findall(r"[^，：]+[，：]?", sentence):
            if current and len(current) + len(clause) > 20:
                units.append(current)
                current = clause
            else:
                current += clause
        if current:
            units.append(current)
    merged = []
    for unit in units:
        if merged and len(merged[-1]) < 6:
            merged[-1] += unit
        else:
            merged.append(unit)
    return merged


def han(text):
    return re.sub(r"[^\u4e00-\u9fff]", "", text)


def passages(src):
    """课文原文，按默写粒度切句：{标题: (作者, [句, ...])}。用于给默写题配前后文。"""
    out = {}
    by_text = {}
    for card in load(src / "gushi/word-cards.json")["cards"]:
        by_text.setdefault(card["contexts"][0]["text_id"], []).append(card)
    texts = {t["id"]: t for t in load(src / "gushi/wenyan-library.json")["texts"]}
    for text_id, group in by_text.items():
        group.sort(key=lambda c: c["contexts"][0].get("note_order", 0))
        author = group[0]["contexts"][0].get("author", texts[text_id].get("author", "")).strip("（）")
        old = texts[text_id].get("volume") == "选修"
        out[texts[text_id]["title"]] = (author, [u for c in group for u in sentence_units(c["gloss"])], old)
    for t in load(src / "wenyan/wenyan-library.json")["texts"]:
        body = "".join(p["text"] for sec in t.get("sections", []) for p in sec.get("paragraphs", []))
        if body and t["title"] not in out:
            out[t["title"]] = (t.get("author", ""), sentence_units(body), t.get("volume") not in WENYAN_ORDER)
    return out


def locate(texts, lines):
    """在原文中找到答案句，返回出处与前后各一句；找不到返回 None（课外或初中篇目）。"""
    first, last = han(lines[0]), han(lines[-1])
    for title, (author, units, old) in texts.items():
        plain = [han(u) for u in units]
        if han("".join(lines)) not in "".join(plain):
            continue
        i = next(n for n, u in enumerate(plain) if first[:4] in u)
        j = next(n for n in range(i, len(plain)) if last[-4:] in plain[n])
        return {"t": title, "a": re.sub(r"^\[.*?\]", "", author), "b": units[i - 1] if i else "", "m": "".join(units[i:j + 1]),
                "f": units[j + 1] if j + 1 < len(units) else "", "old": old}
    return None


DICTATION_DECKS = ["新高考Ⅰ卷", "其他高考卷", "广东模拟", "外省模拟"]
GUANGDONG = ("广东", "广州", "深圳", "佛山", "东莞", "惠州", "珠海", "汕头", "湛江", "肇庆", "江门", "中山", "茂名", "梅州", "清远", "韶关", "揭阳", "潮州")


def gushi(src):
    """古诗文：按高考理解性默写出题。题目来自历年真题与各地模拟（scripts/dictation_source.json）。"""
    texts = passages(src)
    cards = []
    for n, q in enumerate(load(ROOT / "scripts/dictation_source.json")):
        year = q["src"][:4]
        if "高考" in q["src"] or "卷" in q["src"]:
            deck = "新高考Ⅰ卷" if "新高考Ⅰ卷" in q["src"] else "其他高考卷"
        else:
            deck = "广东模拟" if any(c in q["src"] for c in GUANGDONG) else "外省模拟"
        assert q["stem"].count("{}") == len(q["ans"][0]), q["stem"]
        cards.append({
            "id": f"mx-{n + 1:03d}", "d": deck, "o": -int(year),
            "src": q["src"], "stem": q["stem"], "ans": q["ans"], "open": q.get("open", False),
            "note": q.get("note", ""), "ctx": [locate(texts, alt) for alt in q["ans"]],
        })
    cards.sort(key=lambda c: (DICTATION_DECKS.index(c["d"]), c["o"]))
    write("gushi.json", {"decks": decks_in(cards, DICTATION_DECKS), "cards": cards})
    return len(cards), texts


def biology(src):
    library = load(src / "biology/wenyan-library.json")["texts"]
    order = [f'{t["volume"]} · {t["title"]}' for t in library]
    media = OUT / "bio"
    media.mkdir(exist_ok=True)
    cards = []
    for card in load(src / "biology/word-cards.json")["cards"]:
        b = card["biology"]
        chapter = library[[t["id"] for t in library].index(card["contexts"][0]["text_id"])]
        images = []
        for image in b.get("images", []):
            name = Path(image["src"]).name
            shutil.copyfile(src / "biology" / image["src"], media / name)
            images.append(name)
        cards.append({
            "id": card["id"], "d": f'{chapter["volume"]} · {chapter["title"]}',
            "o": card["contexts"][0].get("curriculum_order", 0), "n": b["note_id"],
            "parts": [[p["text"], 1 if p["hidden"] else 0] for p in b["parts"]], "img": images,
        })
    cards.sort(key=lambda c: (order.index(c["d"]), c["o"]))
    write("biology.json", {"decks": decks_in(cards, order), "cards": cards})
    return len(cards)


# 首页风景图与内容一一对应：图里画的就是首页文字写的景。
# 每张图给三科各配一样内容：古诗文是这句原文（附前后文），文言是该景句子里的一个词，生物是画面能说明的知识点。
SCENES = [
    {"line": "白露横江，水光接天。", "wenyan": ("白露", "白露横江"), "biology": "anki-1710684090481-c1"},
    {"line": "三五之夜，明月半墙，桂影斑驳，风移影动，珊珊可爱。", "wenyan": ("珊珊", "三五之夜"), "biology": "anki-1701615514137-c1"},
    {"line": "鹰击长空，鱼翔浅底，万类霜天竞自由。", "wenyan": ("鼓", "微风鼓浪"), "biology": "anki-1709448774428-c1"},
    {"line": "落霞与孤鹜齐飞，秋水共长天一色。", "wenyan": ("兴", "水波不兴"), "biology": "anki-1711891131762-c1"},
    {"line": "苍山负雪，明烛天南。", "wenyan": ("负", "苍山负雪"), "biology": "anki-1705239513498-c1"},
    {"line": "稍见云中白若樗蒱数十立者，山也。", "wenyan": ("稍", "稍见云中"), "biology": "anki-1710075028066-c1"},
    {"line": "此地有崇山峻岭，茂林修竹，又有清流激湍，映带左右。", "wenyan": ("映带", "映带左右"), "biology": "anki-supp-x2-direct-basic"},
]


def scenes(texts):
    wenyan_cards = load(OUT / "wenyan.json")["cards"]
    biology_ids = {c["id"] for c in load(OUT / "biology.json")["cards"]}
    out = []
    for n, scene in enumerate(SCENES):
        poem = locate(texts, [scene["line"]])
        assert poem, scene["line"]
        word, fragment = scene["wenyan"]
        hits = [(c, i) for c in wenyan_cards for i, x in enumerate(c["x"]) if c["w"] == word and fragment in x[1]]
        assert hits, word
        card, example = hits[0]
        assert scene["biology"] in biology_ids, scene["biology"]
        out.append({
            "img": f"scenes/{n}.jpg", "line": scene["line"], "title": poem["t"], "author": poem["a"],
            "before": poem["b"], "after": poem["f"],
            "wenyan": card["id"], "example": example, "biology": scene["biology"],
        })
    write("scenes.json", out)


def main():
    src = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    scene_dir = OUT / "scenes"
    scene_dir.mkdir(exist_ok=True)
    for n in range(len(SCENES)):
        shutil.copyfile(src / f"scenes/{n}.jpg", scene_dir / f"{n}.jpg")
    dictation, texts = gushi(src)
    print("文言", wenyan(src), "古诗文默写", dictation, "生物", biology(src))
    scenes(texts)


if __name__ == "__main__":
    main()
