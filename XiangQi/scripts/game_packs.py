"""Shared deterministic view of the shipped collection manifest."""
import hashlib, json, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'

def retain_full_length(games, excluded):
    """Apply the user's numbered-move cutoff, preserving an exclusion trail."""
    minimum = json.loads((ROOT/'data/curation-policy.json').read_text(encoding='utf-8')).get('minimumNumberedMoves', 0)
    kept = []
    for game in games:
        if (len(game['moves']) + 1) // 2 < minimum:
            excluded.append({'id': game['id'], 'halfMoves': len(game['moves']),
                             'sourceUrl': game['sourceUrl'],
                             'reason': f'User curation: game ends before numbered move {minimum}'})
        else:
            kept.append(game)
    return kept

def read_packs(read=None):
    read=read or (lambda name:(ASSETS/name).read_bytes())
    manifest=json.loads(read('game-packs.json'))
    packs=[]
    for entry in manifest['packs']:
        name=entry['file']
        if pathlib.PurePath(name).name!=name or not name.endswith('.json'):raise ValueError('Invalid pack asset path')
        data=read(name);games=json.loads(data)['games']
        packs.append((entry,games,hashlib.sha256(data).hexdigest()))
    return packs

def all_games():return [g for _,games,_ in read_packs() for g in games]
