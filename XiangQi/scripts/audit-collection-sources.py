"""Reproduce every shipped human pack without writing to its assets or manifests.

Uses previously downloaded, attributed source records and reviewed curation
policies. This verifies source agreement, not a fresh independent confirmation
of every historical date or result. Network refresh is a separate operation.
"""
import argparse
import contextlib
from datetime import date
import hashlib
import io
import json
from pathlib import Path
import re
import sys
from types import SimpleNamespace
from unittest.mock import patch

from game_packs import ROOT, read_packs


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'test-results/collection-source-audit.json')
    args = parser.parse_args()
    packs = read_packs()
    targets = {}
    ids, lines = set(), set()
    summaries, limitations = [], []
    for entry, games, digest in packs:
        if entry.get('engineGame'):
            raise ValueError('Add an engine-specific source reproducer before auditing engine packs')
        asset = ROOT / 'app/src/main/assets' / entry['file']
        manifest = ROOT / entry['sourceManifest']
        targets[asset.resolve()] = asset.read_bytes()
        targets[manifest.resolve()] = manifest.read_bytes()
        proof = json.loads(targets[manifest.resolve()])
        if proof['assetSha256'] != digest or proof['games'] != len(games):
            raise ValueError(f'{asset.name}: manifest mismatch')
        plies = sum(len(game['moves']) for game in games)
        if proof.get('halfMoves', proof.get('plies')) != plies:
            raise ValueError(f'{asset.name}: move count mismatch')
        partial = 0
        for game in games:
            ident = game['id']
            if ident in ids:
                raise ValueError(f'Duplicate ID: {ident}')
            ids.add(ident)
            initial = 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1'
            fen = ' '.join((game.get('startFen') or initial).split()).translate(str.maketrans('hHeE', 'nNbB'))
            key = fen, tuple(game['moves'])
            if key in lines:
                raise ValueError(f'Duplicate complete mainline: {ident}')
            lines.add(key)
            if not game['moves'] or not all(re.fullmatch('[a-i][0-9][a-i][0-9]', m) for m in game['moves']):
                raise ValueError(f'{ident}: missing or malformed moves')
            if game['result'] not in ('1-0', '0-1', '1/2-1/2'):
                raise ValueError(f'{ident}: unknown result')
            value = game['date']
            if not re.fullmatch(r'\d{4}(?:-\d{2}){0,2}', value):
                raise ValueError(f'{ident}: malformed date')
            date.fromisoformat(value + ('-01-01' if len(value) == 4 else '-01' if len(value) == 7 else ''))
            precision = {4: 'year', 7: 'month', 10: 'day'}[len(value)]
            if game.get('datePrecision', precision) != precision:
                raise ValueError(f'{ident}: date precision mismatch')
            partial += precision != 'day'
            if value[:4] not in game['event']:
                raise ValueError(f'{ident}: event/date year mismatch')
            if not game['red'].strip() or not game['black'].strip() or game['red'] == game['black']:
                raise ValueError(f'{ident}: missing or identical players')
            if not game['sourceUrl'].startswith('https://') or not game['timeControl']:
                raise ValueError(f'{ident}: missing provenance or time control')
            notes = {k: game[k] for k in ('dateNote', 'metadataNote', 'timeControlNote', 'roundNote') if k in game}
            if precision != 'day' or notes:
                limitations.append({'id': ident, 'datePrecision': precision, 'notes': notes})
        summaries.append({'asset': asset.name, 'games': len(games), 'halfMoves': plies,
                          'assetSha256': digest, 'partialDates': partial})

    import download_wxf
    import download_world_2023
    import download_historical
    import download_vietnamese
    import download_vietnamese_classics
    import download_futa2025
    captured, log = {}, io.StringIO()
    original_write = Path.write_bytes

    def capture(path, content):
        resolved = path.resolve()
        if resolved in targets:
            captured[resolved] = bytes(content)
            return len(content)
        return original_write(path, content)

    with contextlib.redirect_stdout(log), patch.object(Path, 'write_bytes', capture):
        with patch.object(sys, 'argv', ['download_wxf.py', '--offline']):
            download_wxf.main()
        download_world_2023.build(SimpleNamespace(cache=ROOT / '.cache/world-2023', offline=True, refresh=False))
        download_historical.build(True)
        download_vietnamese.build(True, False)
        # These pinned source converters already provide strict check-only mode.
        for module in (download_vietnamese_classics, download_futa2025):
            with patch.object(sys, 'argv', [module.__name__, '--check']):
                module.main()
    if len(captured) != 8:
        raise ValueError(f'Expected four regenerated assets and manifests, got {len(captured)}')
    for path, raw in captured.items():
        if raw != targets[path]:
            raise ValueError(f'{path.relative_to(ROOT)} differs from source reproduction')
    for path, raw in targets.items():
        if path.read_bytes() != raw:
            raise ValueError(f'Shipped file changed during read-only audit: {path}')
    report = {'status': 'PASS', 'games': sum(p['games'] for p in summaries),
              'halfMoves': sum(p['halfMoves'] for p in summaries), 'packs': summaries,
              'scope': 'Exact source reproduction and metadata/schema consistency against cached public records and reviewed policies; not fresh confirmation of all source claims',
              'duplicates': 0, 'preservedUncertainties': limitations}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    args.output.with_suffix('.log').write_text(log.getvalue(), encoding='utf-8')
    print(f"PASS: {report['games']} games / {report['halfMoves']} half-moves; all six human packs reproduce exactly; no duplicates")
    print(f'Preserved uncertainty/metadata-note records: {len(limitations)}. Report: {args.output}')


if __name__ == '__main__':
    main()
