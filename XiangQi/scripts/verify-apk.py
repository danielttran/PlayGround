"""Check game pack and exact upstream engine payload in a built APK."""
import argparse, hashlib, json, pathlib, re, zipfile
from urllib.parse import urlparse
ROOT=pathlib.Path(__file__).resolve().parents[1]
def require(value,message):
    if not value:raise SystemExit('FAIL: '+message)
p=argparse.ArgumentParser()
p.add_argument('apk', type=pathlib.Path)
p.add_argument('--portable', action='store_true')
a=p.parse_args()
with zipfile.ZipFile(a.apk) as z:
    require(z.testzip() is None,'Damaged ZIP entry')
    game_bytes=z.read('assets/games.json');games=json.loads(game_bytes)['games']
    manifest=json.loads((ROOT/'data/wxf-manifest.json').read_text(encoding='utf-8'))
    policy=json.loads((ROOT/'data/curation-policy.json').read_text(encoding='utf-8'))
    require(hashlib.sha256(game_bytes).hexdigest()==manifest['assetSha256'],'Game pack differs from the audited source manifest')
    require(len(games)==manifest['games']==285 and sum(len(g['moves']) for g in games)==manifest['halfMoves']==23340,'Game/ply count mismatch')
    require(len({g['id'] for g in games})==len(games),'Duplicate game IDs')
    require(all(g['timeControl']=='3600+30' for g in games),'Non-classical game in starter pack')
    source_names={alias:name for name,alias in policy['aliases'].items()}
    denied=set(policy['excludedNames'])
    for game in games:
        require(urlparse(game['sourceUrl']).hostname in ('www.wxf-xiangqi.org','wxf-xiangqi.org'),'Unexpected game source host')
        require(all(re.fullmatch(r'[a-i][0-9][a-i][0-9]',m) for m in game['moves']),'Non-canonical move in '+game['id'])
        require(game['result'] in ('1-0','0-1','1/2-1/2','*'),'Unsupported game result')
        require(not any(source_names.get(game[side],game[side]) in denied for side in ('red','black')),'Excluded source-name match in starter pack')
    for name,digest in {
        'lib/arm64-v8a/libpikafish.so':'6c06b8752e10c1ed605fa836d2c9bbf885e9c402b216023040ddf4586f4320b1',
        'assets/pikafish.nnue':'7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e'
    }.items():
        if a.portable: require(name not in z.namelist(),'Portable APK contains native payload')
        else: require(hashlib.sha256(z.read(name)).hexdigest()==digest,'Upstream payload changed: '+name)
    require(all(name in z.namelist() for name in ('assets/licenses/Copying.txt','assets/licenses/NNUE-License.md','assets/licenses/UPSTREAM.md')),'Engine provenance/license notices missing')
print('PASS',a.apk.name,'285 classical games / 23,340 plies;',a.apk.stat().st_size,'bytes')
print('SHA256',hashlib.file_digest(a.apk.open('rb'),'sha256').hexdigest())
