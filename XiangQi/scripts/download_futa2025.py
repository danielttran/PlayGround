#!/usr/bin/env python3
"""Reproduce the screened 2025 Phuong Trang classical collection."""
import argparse
import copy
import hashlib
import html
import json
from pathlib import Path
import re
import urllib.request

import download_vietnamese_classics as notation_converter
from game_packs import ROOT, read_packs

POLICY = ROOT / 'data/futa2025-policy.json'
ASSET = ROOT / 'app/src/main/assets/futa2025-games.json'
MANIFEST = ROOT / 'data/futa2025-manifest.json'
CACHE = ROOT / '.cache/futa2025'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encoded(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def facts(source, keys):
    value = {key: source[key].replace('\r\n', '\n') if isinstance(source[key], str)
             else source[key] for key in keys}
    return digest(json.dumps(value, ensure_ascii=False, sort_keys=True,
                             separators=(',', ':')).encode('utf-8'))


def canonical_name(source, side, policy):
    value = source[side + '_player_name_en'].split(',')[-1].strip()
    return policy['nameNormalizations'].get(value, value)


def pairing_rows(text, policy):
    def name(value):
        chinese = ''.join(re.findall(r'[\u3400-\u9fff]+', value))
        return policy['nameNormalizations'].get(chinese, chinese)
    parts = re.split(r'id="scn-ta-panel-round-(\d+)"', text)
    rows = []
    for index in range(1, len(parts), 2):
        for row in re.findall(r'<tr[^>]*>(.*?)</tr>', parts[index + 1], re.S):
            cells = [html.unescape(re.sub(r'<[^>]+>', ' ', cell)).strip()
                     for cell in re.findall(r'<t[dh][^>]*>(.*?)</t[dh]>', row, re.S)]
            federations = ('VIE', 'CHN', 'SGP', 'MAS', 'HKG', 'TPE')
            if (len(cells) >= 9 and cells[0].isdigit() and cells[3] in federations
                    and cells[8] in federations and name(cells[2]) and name(cells[7])):
                rows.append({'round': int(parts[index]), 'red': name(cells[2]),
                             'black': name(cells[7]), 'board': int(cells[0]),
                             'result': cells[5].replace(' ', '').replace('½', '1/2')})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--java-bin')
    args = parser.parse_args()
    policy = json.loads(POLICY.read_text(encoding='utf-8'))
    CACHE.mkdir(parents=True, exist_ok=True)
    listing = CACHE / 'tournament-2.json'
    if args.refresh or not listing.exists():
        request = urllib.request.Request(policy['listingUrl'],
                                         headers={'User-Agent': 'XiangqiStudy-source-audit/1.0'})
        with urllib.request.urlopen(request, timeout=40) as response:
            listing.write_bytes(response.read())
    response = json.loads(listing.read_text(encoding='utf-8'))
    if response.get('success') is not True:
        raise ValueError('Public game listing failed')
    sources = response['data']['games']
    result_page = CACHE / 'results.html'
    if args.refresh or not result_page.exists():
        with urllib.request.urlopen(policy['pairingsUrl'], timeout=40) as response:
            result_page.write_bytes(response.read())
    pairings = pairing_rows(result_page.read_text(encoding='utf-8'), policy)
    if digest(encoded(pairings)) != policy['pairingsFactsSha256']:
        raise ValueError('Official pairing facts changed; review before updating policy')
    pairing_index = {(row['round'], row['red'], row['black']): row for row in pairings}
    expected = {item['sourceGameId']: item for item in policy['records']}
    if len({source['id'] for source in sources}) != len(sources) or set(expected) != {g['id'] for g in sources}:
        raise ValueError('Source game inventory changed; review before updating policy')
    shared = ROOT / policy['sharedCurationPolicy']
    denied = set(json.loads(shared.read_text(encoding='utf-8'))['excludedNames'])
    classical, rejected = [], []
    for source in sources:
        ident = source['id']
        if facts(source, policy['sourceFactsKeys']) != expected[ident]['sourceFactsSha256']:
            raise ValueError(f'{ident}: source facts changed')
        if ident in policy['rapidGameIds']:
            if source['round'] not in ('8.r1', '9.r1'):
                raise ValueError(f'{ident}: unexpected tiebreak marker')
            rejected.append({'sourceGameId': ident, 'reason': 'Rapid tiebreak'})
            continue
        number = source['round_num']
        if str(number) != source['round'] or str(number) not in policy['roundDates']:
            raise ValueError(f'{ident}: unexpected classical round')
        if source['date_played'] != policy['roundDates'][str(number)]:
            raise ValueError(f'{ident}: date disagrees with official schedule')
        if source['fen_initial'] != policy['sourceInitialFen']:
            raise ValueError(f'{ident}: not a full initial-position game')
        for side in ('white', 'black'):
            name = canonical_name(source, side, policy)
            if name not in policy['participants'] or name in denied:
                raise ValueError(f'{ident}: unknown or excluded player {name}')
        red, black = (canonical_name(source, side, policy) for side in ('white', 'black'))
        knockout = number >= 8 and red in policy['finalFour'] and black in policy['finalFour']
        if not knockout:
            row = pairing_index.get((number, red, black))
            if row is None or row['result'] != source['result']:
                raise ValueError(f'{ident}: Swiss color/opponent/result mismatch')
        if ident in policy['incompleteGameIds']:
            if 'thất lạc biên bản' not in source['pgn_text']:
                raise ValueError(f'{ident}: missing-scoresheet evidence changed')
            rejected.append({'sourceGameId': ident, 'reason': 'Source explicitly marks the scoresheet lost; only two half-moves'})
            continue
        item = copy.deepcopy(source)
        # Diagram markers carry no moves. Unknown comments or unparsed text
        # continue to fail rather than hiding a missing-score annotation.
        item['pgn_text'] = re.sub(r'\{\s*(?:\[D\])?\s*\}', '', item['pgn_text'])
        classical.append(item)
    notation_converter.CACHE = CACHE
    converted = notation_converter.convert(classical, args.java_bin)
    packs = read_packs(lambda name: b'{"games":[]}' if name == ASSET.name else (ASSET.parent / name).read_bytes())
    existing = {(game.get('startFen') or policy['canonicalInitialFen'], tuple(game['moves']))
                for entry, games, _ in packs if entry['file'] != ASSET.name for game in games}
    games, records, seen = [], [], set()
    for source in classical:
        ident = source['id']
        moves, final_fen = converted[ident]
        if ident in policy['ambiguousGameIds']:
            if moves is not None or final_fen != 2:
                raise ValueError(f'{ident}: held-out ambiguity changed')
            rejected.append({'sourceGameId': ident, 'reason': 'Two complete legal notation interpretations'})
            continue
        if moves is None:
            raise ValueError(f'{ident}: newly ambiguous or illegal notation')
        supplied_count = re.search(r'\[PlyCount "(\d+)"\]', source['pgn_text'])
        if supplied_count and int(supplied_count[1]) != len(moves):
            raise ValueError(f'{ident}: incomplete source move count')
        key = (policy['canonicalInitialFen'], tuple(moves))
        if key in existing or key in seen:
            rejected.append({'sourceGameId': ident, 'reason': 'Duplicate starting position and full mainline'})
            continue
        seen.add(key)
        red, black = (canonical_name(source, side, policy) for side in ('white', 'black'))
        phase = 'Swiss'
        if source['round_num'] >= 8 and red in policy['finalFour'] and black in policy['finalFour']:
            phase = ('Semifinal' if source['round_num'] == 8 else
                     'Final' if policy['vietnameseFinalist'] in (red, black) else 'Third-place match')
        game = {
            'id': f'futa2025-{ident}', 'collection': policy['collection'],
            'event': policy['event'], 'round': f"{phase} · Round {source['round']}",
            'date': source['date_played'], 'datePrecision': 'day', 'sourceDate': source['date_played'],
            'red': red, 'black': black, 'result': source['result'],
            'sourceRed': source['white_player_name'], 'sourceBlack': source['black_player_name'],
            'sourceRedBilingual': source['white_player_name_en'],
            'sourceBlackBilingual': source['black_player_name_en'],
            'sourceRound': source['round'], 'sourceBoardNumber': source['board_num'],
            'sourceSection': source['tournament_title'], 'timeControl': '3600+30',
            'sourceTimeControl': '60 minutes + 30 seconds per move from move one',
            'classicalRegulationsUrl': policy['regulationsUrl'],
            'classicalEvidence': policy['classicalEvidence'],
            'sourceUrl': policy['viewerUrl'].format(ident),
            'metadataStatus': 'Official public record; date/stage checked against regulations; ' +
                              ('Swiss colors/results checked against pairing table' if phase == 'Swiss' else
                               'classical-game result retained separately from aggregate match result'),
            'moves': moves,
        }
        games.append(game)
        records.append({'id': game['id'], 'sourceGameId': ident,
                        'sourceFactsSha256': expected[ident]['sourceFactsSha256'],
                        'mainlineSha256': digest(' '.join(moves).encode()),
                        'plies': len(moves), 'finalFen': final_fen})
    games.sort(key=lambda game: (game['date'], game['id']), reverse=True)
    asset = encoded({'collection': policy['collection'], 'source': 'Official VietnamChess records and regulations', 'games': games})
    manifest = encoded({'schemaVersion': 1, 'screeningDate': policy['screeningDate'],
                        'policySha256': digest(POLICY.read_bytes()),
                        'sharedCurationPolicySha256': digest(shared.read_bytes()),
                        'asset': ASSET.name, 'assetSha256': digest(asset),
                        'games': len(games), 'halfMoves': sum(len(game['moves']) for game in games),
                        'sourceCandidateCount': len(sources),
                        'legalValidation': 'Production Position; exactly one complete legal notation trace',
                        'deduplication': 'Starting FEN and full mainline against all other packs and within this pack',
                        'records': records, 'rejected': sorted(rejected, key=lambda item: item['sourceGameId'])})
    for path, content in ((ASSET, asset), (MANIFEST, manifest)):
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                raise ValueError(f'{path.name}: output differs from reviewed source reproduction')
        else:
            path.write_bytes(content)
    print(f"{'Checked' if args.check else 'Wrote'} {len(games)} games / {sum(len(g['moves']) for g in games)} plies; {len(rejected)} excluded")


if __name__ == '__main__':
    main()
