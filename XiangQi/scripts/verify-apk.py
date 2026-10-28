"""Check game pack and exact upstream engine payload in a built APK."""
import argparse, hashlib, json, pathlib, zipfile
p=argparse.ArgumentParser()
p.add_argument('apk', type=pathlib.Path)
p.add_argument('--portable', action='store_true')
a=p.parse_args()
with zipfile.ZipFile(a.apk) as z:
    assert z.testzip() is None, 'Damaged ZIP entry'
    games=json.loads(z.read('assets/games.json'))['games']
    assert len(games)==285 and sum(len(g['moves']) for g in games)==23340
    assert all(g['timeControl']=='3600+30' for g in games)
    for name,digest in {
        'lib/arm64-v8a/libpikafish.so':'6c06b8752e10c1ed605fa836d2c9bbf885e9c402b216023040ddf4586f4320b1',
        'assets/pikafish.nnue':'7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e'
    }.items():
        if a.portable: assert name not in z.namelist(), 'Portable APK contains native payload'
        else: assert hashlib.sha256(z.read(name)).hexdigest()==digest, 'Upstream payload changed: '+name
    assert 'assets/licenses/Copying.txt' in z.namelist()
print('PASS',a.apk.name,'285 classical games / 23,340 plies;',a.apk.stat().st_size,'bytes')
print('SHA256',hashlib.file_digest(a.apk.open('rb'),'sha256').hexdigest())
