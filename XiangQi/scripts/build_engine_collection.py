"""Normalize reviewed Dongping exports; never infer engines from account names.

--prepare screens the browser-export cache and writes Java replay input.
--publish pins exactly 200 legally replayed, distinct candidates as source facts.
--check reproduces the shipped pack from those pinned facts without writing it.
"""
import argparse
from collections import Counter
from datetime import date
import hashlib
import html
import json
from pathlib import Path
import re
import subprocess

from download_wxf import iccs_moves
from game_packs import ROOT, all_games

CACHE = ROOT / '.cache/dongping'
FACTS = ROOT / 'data/dongping-engine-records.json'
OUTPUT = ROOT / 'app/src/main/assets/engine-games.json'
MANIFEST = ROOT / 'data/engine-games-manifest.json'
INITIAL = 'rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1'
FIELDS = ('binit', 'movelist', 'length', 'firstnum', 'type', 'gametype', 'title',
          'class', 'event', 'group', 'round', 'table', 'place', 'date', 'timerule',
          'red', 'redname', 'redeng', 'redlevel', 'redrating',
          'black', 'blackname', 'blackeng', 'blacklevel', 'blackrating', 'result', 'endtype')


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def initial_fen(binit):
    if not re.fullmatch('[0-9]{64}', binit):
        raise ValueError('Malformed starting position')
    board = [[''] * 9 for _ in range(10)]
    for piece, offset in zip('RNBAKABNRCCPPPPPrnbakabnrccppppp', range(0, 64, 2)):
        x, y = map(int, binit[offset:offset + 2])
        if (x, y) == (9, 9):
            continue
        if x > 8 or board[y][x]:
            raise ValueError('Invalid or overlapping starting pieces')
        board[y][x] = piece
    ranks = []
    for rank in board:
        text, empty = '', 0
        for piece in rank:
            if piece:
                text += (str(empty) if empty else '') + piece
                empty = 0
            else:
                empty += 1
        ranks.append(text + (str(empty) if empty else ''))
    return '/'.join(ranks) + ' w - - 0 1'


def game_from(record):
    t, ident = record['tags'], f"dpxq-u-{record['sourceId']}"
    day = date.fromisoformat(t.get('date', '')[:10]).isoformat()
    if not '2017-01-01' <= day <= '2026-10-31':
        raise ValueError('Outside approved 2017 onward range')
    if t.get('firstnum', '0') != '0' or initial_fen(t.get('binit', '')) != INITIAL:
        raise ValueError('Not a complete game from the standard initial position')
    moves = iccs_moves(t.get('movelist', ''))
    if not 60 <= len(moves) <= 1500 or int(t.get('length', '-1')) != len(moves):
        raise ValueError('Short, malformed or inconsistent move record')
    result = {'红胜': '1-0', '黑胜': '0-1', '和棋': '1/2-1/2', '和局': '1/2-1/2'}.get(t.get('result'))
    if result is None:
        raise ValueError('Missing result')
    end = t.get('endtype', '')
    if end not in ('', '认负', '绝杀', '困毙', '议和', '判和'):
        raise ValueError('Technical or insufficiently documented ending: ' + end)
    declared = t.get('event') == '软件对弈' or '象棋软件' in t.get('red', '') + t.get('black', '')
    names = [t.get(s + 'name') or t.get(s, '') for s in ('red', 'black')]
    if not all(names):
        raise ValueError('Missing participant names')
    if not declared and not re.search('皮卡鱼|pikafish|旋风|cyclone|佳佳|ggnnue|名手|小虫|南奥|南澳|alpha|阿尔法', ' '.join(names), re.I):
        raise ValueError('No reviewed engine-named account')
    # redeng/blackeng are English account names, NOT engine identifiers.
    readable = []
    for side, name in zip(('red', 'black'), names):
        account = t.get(side + 'eng', '')
        if account and re.fullmatch(r'[\x20-\x7e]+', account):
            readable.append(account + ' (' + name + ')')
        else:
            readable.append(name)
    year = day[:4]
    event = ('Software match ' + year) if t.get('event') == '软件对弈' else ('Yitian online ' + year if not declared else t.get('event', 'Software match ' + year))
    note = ('Source labels this as a software match. ' if declared else
            'These are online account names, not verified engine identities. Either participant may have used a different engine or played manually. ')
    note += 'Engine versions, networks, hardware and per-move search depth are unrecorded. No strength or NNUE claim is verified. '
    note += 'Date and result follow the uploaded source; legal replay does not independently verify them.'
    # A monthly online grouping is not a tournament round.
    round_name = t.get('round', '')
    if re.fullmatch(r'\d{4}[-/]\d{1,2}', round_name):
        round_name = ''
    clock = t.get('timerule', '')
    note += (' Source clock notation: ' + clock + '; preserved without interpreting its fields.') if clock else ' Exact time control is unrecorded.'
    return {'id': ident, 'collection': 'Dongping engine and engine-named online games',
            'engineGame': True, 'engineIdentity': 'source_declared' if declared else 'accounts_unverified',
            'metadataNote': note, 'event': event, 'round': round_name, 'date': day, 'datePrecision': 'day',
            'red': readable[0], 'black': readable[1], 'sourceRed': t.get('red', ''), 'sourceBlack': t.get('black', ''),
            'sourceDate': t.get('date', ''), 'sourceEvent': t.get('event', ''), 'sourceRound': t.get('round', ''),
            'sourceTimeControl': clock, 'timeControl': 'Unverified clock: ' + clock if clock else 'Time control unrecorded',
            'result': result, 'sourceUrl': f"https://www.dpxq.com/hldcg/search/view_u_{record['sourceId']}.html",
            'sourceTitle': t.get('title', ''), 'moves': moves}


def prepare():
    rows, excluded = [], []
    for capture in json.loads((CACHE / 'browser-exports.json').read_bytes()):
        tags = {k: html.unescape(v).strip() for k, v in re.findall(r'\[DhtmlXQ_(\w+)\](.*?)\[/DhtmlXQ_\1\]', capture['text'], re.S)}
        record = {'sourceId': capture['id'], 'sourceExportSha256': sha(capture['text'].encode()),
                  'tags': {k: tags[k] for k in FIELDS if k in tags}}
        try:
            game = game_from(record)
            rows.append((record, game))
        except (ValueError, KeyError) as error:
            excluded.append({'sourceId': capture['id'], 'reason': str(error)})
    CACHE.mkdir(parents=True, exist_ok=True)
    (CACHE / 'candidates.json').write_bytes(encoded([r for r, _ in rows]))
    (CACHE / 'candidates.tsv').write_bytes(''.join(g['id'] + '\t\t' + ' '.join(g['moves']) + '\n' for _, g in rows).encode())
    (CACHE / 'screening.json').write_bytes(encoded(excluded))
    print(json.dumps({'captured': len(rows) + len(excluded), 'screened': len(rows),
                      'excluded': len(excluded), 'years': dict(Counter(g['date'][:4] for _, g in rows)),
                      'endRejections': dict(Counter(x['reason'] for x in excluded))}, ensure_ascii=True))


def build(check=False):
    facts_raw = FACTS.read_bytes()
    facts = json.loads(facts_raw)
    games = [game_from(r) for r in facts['records']]
    if len(games) != 200:
        raise ValueError('Delivery requires exactly 200 selected games')
    if len({g['id'] for g in games}) != len(games) or len({tuple(g['moves']) for g in games}) != len(games):
        raise ValueError('Duplicate engine record')
    games.sort(key=lambda g: (g['date'], g['id']), reverse=True)
    raw = encoded({'collection': 'Dongping engine and engine-named online games',
                   'source': 'Dongping public uploads; source claims and account identities are unverified', 'games': games})
    manifest = {'schemaVersion': 1, 'assetSha256': sha(raw), 'sourceFactsSha256': sha(facts_raw),
                'games': len(games), 'plies': sum(len(g['moves']) for g in games),
                'byYear': dict(sorted(Counter(g['date'][:4] for g in games).items())),
                'identity': dict(Counter(g['engineIdentity'] for g in games)),
                'scope': 'Source-labelled software matches plus explicitly unverified engine-named online accounts. Not a verified NNUE tournament archive. No per-move search logs supplied.',
                'sourceRecords': [{'sourceId': r['sourceId'], 'sourceExportSha256': r['sourceExportSha256']} for r in facts['records']]}
    manifest_raw = encoded(manifest)
    if check:
        if OUTPUT.read_bytes() != raw or MANIFEST.read_bytes() != manifest_raw:
            raise ValueError('Engine pack differs from pinned source facts')
    else:
        OUTPUT.write_bytes(raw)
        MANIFEST.write_bytes(manifest_raw)
    print(json.dumps({k: manifest[k] for k in ('games', 'plies', 'byYear', 'identity', 'assetSha256')}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--prepare', action='store_true')
    p.add_argument('--check', action='store_true')
    args = p.parse_args()
    if args.prepare:
        prepare()
    else:
        build(args.check)
