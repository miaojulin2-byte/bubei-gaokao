from pathlib import Path
import json
R=Path(__file__).resolve().parents[1];totals={}
for subject in ['wenyan','gushi','biology']:
 d=json.loads((R/'android/assets'/subject/'textbook-word-cards.json').read_text());cards=d['cards'];ids=[c['id'] for c in cards]
 assert len(ids)==len(set(ids))==d['count']
 lib=json.loads((R/'android/assets'/subject/'wenyan-library.json').read_text());texts={t['id'] for t in lib['texts']}
 for card in cards:
  assert card['contexts']
  for context in card['contexts']:assert context['text_id'] in texts and context['sentence'] and (context['meaning'] or (subject=='biology' and any(i.get('field')==1 for i in card['biology'].get('images',[]))))
  if subject=='biology':
   for image in card['biology'].get('images',[]):assert (R/'android/assets/biology'/image['src']).is_file()
 totals[subject]=len(ids)
assert totals=={'wenyan':1444,'gushi':1312,'biology':1121},totals
for name in ['cn0','cn1','cn2','cn3','cn4']:assert (R/'android/assets/covers'/f'{name}.jpg').stat().st_size>1000
for name in ['b1','b2','x1','x2','x3']:assert (R/'android/assets/biology/covers'/f'{name}.jpg').stat().st_size>1000
print('PASS: 3877 cards, unique per-subject IDs, context references, biology images, ten textbook covers')
