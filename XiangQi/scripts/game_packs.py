"""Shared deterministic view of the shipped collection manifest."""
import hashlib, json, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'

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
