"""Verify shipped collections, licenses and exact official engine/network bytes."""
import argparse, hashlib, importlib.util, json, pathlib, re, zipfile
from urllib.parse import urlparse
from game_packs import ROOT, read_packs

def require(value,message):
    if not value:raise SystemExit('FAIL: '+message)
p=argparse.ArgumentParser()
p.add_argument('apk',type=pathlib.Path)
modes=p.add_mutually_exclusive_group()
modes.add_argument('--portable',action='store_true',help='Internal emulator audit only')
modes.add_argument('--require-latest',action='store_true',help='Production verification always checks the latest official release')
a=p.parse_args()
if not a.portable:
    spec=importlib.util.spec_from_file_location('sync_pikafish',ROOT/'scripts/sync-pikafish.py');sync=importlib.util.module_from_spec(spec);spec.loader.exec_module(sync)
    sync.sync(check_only=True)
with zipfile.ZipFile(a.apk) as z:
    require(len(z.namelist())==len(set(z.namelist())),'Duplicate ZIP entry names')
    require(z.testzip() is None,'Damaged ZIP entry')
    require(z.read('assets/game-packs.json')==(ROOT/'app/src/main/assets/game-packs.json').read_bytes(),'Collection manifest changed')
    packs=read_packs(lambda name:z.read('assets/'+name));all_games=[]
    policy=json.loads((ROOT/'data/curation-policy.json').read_text(encoding='utf-8'))
    source_names={alias:name for name,alias in policy['aliases'].items()};denied=set(policy['excludedNames'])
    for entry,games,sha in packs:
        manifest=json.loads((ROOT/entry['sourceManifest']).read_text(encoding='utf-8'))
        require(sha==manifest['assetSha256'],'Pack differs from audited source manifest: '+entry['file'])
        require(len(games)==manifest['games'] and sum(len(g['moves']) for g in games)==manifest.get('halfMoves',manifest.get('plies')),'Game/ply count mismatch')
        for g in games:
            require(urlparse(g['sourceUrl']).hostname in ('www.wxf-xiangqi.org','wxf-xiangqi.org','vietnamchess.vn'),'Unexpected game source host')
            require(g['timeControl'] in ('3600+30','4800+30') or g.get('sourceGameType')=='慢棋','Missing classical source evidence')
            require(all(re.fullmatch(r'[a-i][0-9][a-i][0-9]',m) for m in g['moves']),'Non-canonical moves: '+g['id'])
            require(g['result'] in ('1-0','0-1','1/2-1/2','*'),'Unsupported result')
            require(not any(source_names.get(g[side],g[side]) in denied for side in ('red','black')),'Excluded source-name match')
        all_games+=games
    require(len({g['id'] for g in all_games})==len(all_games),'Duplicate game IDs')
    release=z.read('assets/pikafish-release.json');require(release==(ROOT/'app/src/main/assets/pikafish-release.json').read_bytes(),'Engine release metadata changed')
    meta=json.loads(release)
    payload={'lib/arm64-v8a/libpikafish.so':meta['android']['sha256'],'assets/pikafish.nnue':meta['network']['sha256']}
    for name,sha in payload.items():
        if a.portable:require(name not in z.namelist(),'Audit APK contains native payload')
        else:require(hashlib.sha256(z.read(name)).hexdigest()==sha,'Official payload changed: '+name)
    if not a.portable:require(z.getinfo('assets/pikafish.nnue').file_size==meta['network']['bytes'],'NNUE size mismatch')
    for name in ('Copying.txt','NNUE-License.md','UPSTREAM.md','AUTHORS'):
        require(z.read('assets/licenses/'+name)==(ROOT/'app/src/main/assets/licenses'/name).read_bytes(),'Engine notice changed: '+name)
print('PASS',a.apk.name,len(all_games),'classical games /',sum(len(g['moves']) for g in all_games),'plies;',a.apk.stat().st_size,'bytes')
with a.apk.open('rb') as stream:print('SHA256',hashlib.file_digest(stream,'sha256').hexdigest())
