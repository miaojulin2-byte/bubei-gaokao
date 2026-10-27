"""Freeze existing approved content into the unified app; never change source apps."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PARENT = ROOT.parent
ASSETS = ROOT / 'android/assets'
VOLUMES = ['必修上', '必修下', '选择性必修上', '选择性必修中', '选择性必修下']
BIO = ['分子与细胞', '遗传与进化', '稳态与调节', '生物与环境', '生物技术与工程']
CHAPTERS = [
 ['走近细胞','组成细胞的分子','细胞的基本结构','细胞的物质输入和输出','细胞的能量供应和利用','细胞的生命历程'],
 ['遗传因子的发现','基因和染色体的关系','基因的本质','基因的表达','基因突变及其他变异','生物的进化'],
 ['人体的内环境与稳态','神经调节','体液调节','免疫调节','植物生命活动的调节'],
 ['种群及其动态','群落及其演替','生态系统及其稳定性','人与环境'],
 ['发酵工程','细胞工程','基因工程','生物技术的安全性与伦理问题']
]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n')


def main():
    manifest = {}
    for subject, original in [('wenyan', PARENT), ('gushi', PARENT.parent / '古诗文背诵项目')]:
        dest = ASSETS / subject
        dest.mkdir(exist_ok=True)
        for file in (original / 'android/assets').glob('*.json'):
            shutil.copy2(file, dest / file.name)
        library = json.loads((dest / 'wenyan-library.json').read_text())
        if subject == 'gushi':
            for text in library['texts']:
                text['volume'] = text.get('textbook_volume') or '课标与教材补充'
                text['stage'] = text['volume']
        write(dest / 'wenyan-library.json', library)
        cards = json.loads((dest / 'textbook-word-cards.json').read_text())
        write(dest / 'word-cards.json', cards)
        manifest[subject] = {'cards': len(cards['cards']), 'texts': len(library['texts'])}

    apk = ROOT / 'sources/biology-0.6.0.apk'
    if not apk.exists():
        raise SystemExit('Missing frozen biology APK; retrieve the user installed cn.bubei.biology APK first')
    with zipfile.ZipFile(apk) as archive:
        source = archive.read('assets/reviewed-data.js').decode()
        data = json.loads(source[source.index('=') + 1:].strip().rstrip(';'))
        for name in archive.namelist():
            if name.startswith(('assets/covers/', 'assets/media/')) and not name.endswith('/'):
                dest = ASSETS / 'biology' / name.removeprefix('assets/')
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(archive.read(name))
    texts, cards = {}, []
    for n, original in enumerate(data['cards']):
        tid = f"bio-{original['book']}-{original['chapter']}"
        text = texts.setdefault(tid, {
            'id': tid, 'title': f"第{original['chapter']}章 · " + CHAPTERS[original['book']][original['chapter']-1], 'volume': BIO[original['book']],
            'stage': BIO[original['book']], 'book': '人教版高中生物', 'textbook': True,
            'sections': [], 'learning_ready': False,
        })
        parts = original['parts']
        if original['kind'] == 'basic-self-report':
            prompt, answer = parts[0]['text'], parts[1]['text']
        else:
            prompt = ''.join('____' if p.get('hidden') else p['text'] for p in parts)
            answer = ''.join(p['text'] for p in parts)
        context = {'text_id': tid, 'title': BIO[original['book']] + ' · ' + text['title'],
                   'sentence': prompt, 'meaning': answer, 'source_kind': 'biology',
                   'note_order': n, 'curriculum_order': original['book'] * 100 + original['chapter']}
        cards.append({'id': original['id'], 'word': prompt[:48], 'pos': '生物回忆', 'gloss': answer,
                      'contexts': [context], 'biology': original})
    dest = ASSETS / 'biology'
    write(dest / 'textbook-word-cards.json', {'count': len(cards), 'cards': cards})
    write(dest / 'word-cards.json', {'count': len(cards), 'cards': cards})
    write(dest / 'wenyan-library.json', {'texts': list(texts.values()), 'text_count': len(texts)})
    write(dest / 'reading-translations.json', {'translations': {}})
    write(dest / 'sentence-translations.json', {'translations': {}})
    write(dest / 'paragraph-layout.json', {})
    write(ROOT / 'data/biology-evidence.json', data['evidence'])
    manifest['biology'] = {'cards': len(cards), 'chapters': len(texts),
                           'source_apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
    cover_dir = ASSETS / 'covers'
    cover_dir.mkdir(exist_ok=True)
    pdfs = [PARENT / 'output/context-batch-01-20261024/sources/语文必修上册.pdf'] + [
        PARENT / 'output/context-completion-20261024/sources' / name
        for name in ['必修下.pdf', '选必上.pdf', '选必中.pdf', '选必下.pdf']]
    cover_sources = []
    for n, pdf in enumerate(pdfs):
        output = cover_dir / f'cn{n}'
        if not output.with_suffix('.jpg').exists():
            args = ['pdftoppm', '-f', '1', '-singlefile', '-scale-to', '960' if n == 2 else '480']
            if n == 2:  # This PDF starts with a printer spread; extract the actual front cover.
                args += ['-x', '488', '-y', '22', '-W', '448', '-H', '648']
            subprocess.run(args + ['-jpeg', str(pdf), str(output)], check=True, stdout=subprocess.DEVNULL)
        cover_sources.append({'volume': VOLUMES[n], 'sha256': hashlib.sha256(pdf.read_bytes()).hexdigest()})
    manifest['chinese_covers'] = cover_sources
    write(ROOT / 'data/content-manifest.json', manifest)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
