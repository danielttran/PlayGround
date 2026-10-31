"""Reproduce 100 selected classical master games from pinned factual records.

The source repository is read as data only. Annotations, book chapters, browser
code and images are not bundled. Selection and bounded engine review are pinned
separately; rebuilding never silently substitutes a different set of games.
"""
import argparse
from collections import Counter
from datetime import date
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import quote

from download_wxf import STANDARD_BINIT, iccs_moves
from game_packs import ROOT, read_packs, retain_full_length

FACTS = ROOT / 'data/master-classics-records.json'
POLICY = ROOT / 'data/master-classics-policy.json'
REVIEW = ROOT / 'data/master-classics-engine-review.json'
ASSET = ROOT / 'app/src/main/assets/master-classics.json'
MANIFEST = ROOT / 'data/master-classics-manifest.json'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    facts = json.loads(FACTS.read_bytes())
    policy = json.loads(POLICY.read_bytes())
    review = json.loads(REVIEW.read_bytes())
    shared_path = ROOT / 'data/curation-policy.json'
    shared = json.loads(shared_path.read_bytes())
    denied = set(shared['excludedNames'])
    approved = set(policy['masters'])
    engine_records = {row['sha']: row for row in review['records']}
    existing = {tuple(game['moves']) for entry, games, _ in read_packs()
                if entry['file'] != ASSET.name for game in games}
    games, proof, seen = [], [], set()
    for source in facts['records']:
        tags = source['tags']
        red, black = source['red'], source['black']
        if red not in approved or black not in approved or {red, black} & denied:
            raise ValueError('Unapproved master identity: ' + source['path'])
        if tags['binit'] != STANDARD_BINIT or tags.get('firstnum', '0') != '0':
            raise ValueError('Source is not a complete standard-start game')
        if tags['gametype'] != '慢棋':
            raise ValueError('Missing explicit classical source marker')
        moves = iccs_moves(tags['movelist'])
        if len(moves) < 70 or tuple(moves) in existing or tuple(moves) in seen:
            raise ValueError('Short or duplicate selected game')
        seen.add(tuple(moves))
        clock = tags.get('timerule', '').strip()
        if clock not in policy['clocks']:
            raise ValueError('Unreviewed or conflicting source clock: ' + clock)
        played = date.fromisoformat(source['date']).isoformat()
        year = source['path'].split('/')[2][:4]
        if played[:4] != year:
            raise ValueError('Recorded date conflicts with tournament year')
        event = policy['events'][tags['event']]
        if year not in event:
            raise ValueError('Event label lost its year')
        source_round = tags['round'].strip()
        found = re.fullmatch(r'(?:第)?0*(\d+)(?:轮)?', source_round)
        round_label = 'Round ' + str(int(found[1])) if found else source_round
        if not round_label:
            raise ValueError('Missing source round')
        ident = 'masters-' + source['gitBlobSha1'][:12]
        record_review = engine_records[source['gitBlobSha1']]
        if record_review.get('rejected') or len(record_review.get('samples', [])) < 4:
            raise ValueError('Missing bounded engine screening: ' + ident)
        source_url = (facts['repository'] + '/blob/' + facts['commit'] + '/' +
                      quote(source['path'], safe='/'))
        game = {'id': ident, 'collection': policy['collection'], 'event': event,
                'round': round_label, 'date': played, 'datePrecision': 'day',
                'red': red, 'black': black,
                'result': {'红胜': '1-0', '黑胜': '0-1', '和局': '1/2-1/2',
                           '和棋': '1/2-1/2'}[tags['result']],
                'sourceUrl': source_url, 'sourceTitle': tags['title'],
                'sourceEvent': tags['event'], 'sourceDate': tags['date'],
                'sourceRound': source_round, 'sourceRed': tags['red'],
                'sourceBlack': tags['black'], 'sourceGameType': tags['gametype'],
                'sourceTimeControl': clock, 'timeControl': policy['clocks'][clock],
                'classicalEvidence': 'The pinned full-game source explicitly marks 慢棋 '
                                     '(classical); conflicting short clocks are excluded.',
                'metadataNote': source['metadataNote'], 'moves': moves}
        if source.get('roundLabel'):
            game['round'] = source['roundLabel']
        games.append(game)
        proof.append({'id': ident, 'gitBlobSha1': source['gitBlobSha1'],
                      'sourceSha256': source['sourceSha256'], 'halfMoves': len(moves),
                      'mainlineSha256': digest(' '.join(moves).encode())})
    rejected = []
    if len(retain_full_length(games, rejected)) != len(games):
        raise ValueError('Selected game violates current minimum-length policy')
    if len(games) != 100 or len({game['id'] for game in games}) != 100:
        raise ValueError('Exactly 100 distinct reviewed additions are required')
    games.sort(key=lambda game: (game['date'], game['id']), reverse=True)
    asset = encoded({'collection': policy['collection'], 'games': games})
    manifest = encoded({'schemaVersion': 1, 'collection': policy['collection'],
                        'asset': ASSET.name, 'assetSha256': digest(asset),
                        'games': len(games), 'halfMoves': sum(len(g['moves']) for g in games),
                        'sourceRepository': facts['repository'], 'sourceCommit': facts['commit'],
                        'sourceFactsSha256': digest(FACTS.read_bytes()),
                        'policySha256': digest(POLICY.read_bytes()),
                        'sharedCurationPolicySha256': digest(shared_path.read_bytes()),
                        'engineReviewSha256': digest(REVIEW.read_bytes()),
                        'byEvent': dict(sorted(Counter(g['event'] for g in games).items())),
                        'byYear': dict(sorted(Counter(g['date'][:4] for g in games).items())),
                        'records': proof,
                        'legalValidation': 'Every source half-move replayed through production Position; '
                                           'selected histories also searched by official Pikafish.',
                        'qualityScope': policy['selectionMethod'],
                        'rights': 'Attributed factual main-line moves and game metadata only. '
                                  'No annotations, source images, instructional prose or viewer code copied. '
                                  'No blanket license over the source collection is asserted.'})
    for path, raw in ((ASSET, asset), (MANIFEST, manifest)):
        if args.check:
            if not path.exists() or path.read_bytes() != raw:
                raise ValueError('Selected master pack differs from pinned facts: ' + path.name)
        else:
            path.write_bytes(raw)
    print(('Checked' if args.check else 'Wrote'), len(games), 'selected master games /',
          sum(len(g['moves']) for g in games), 'half-moves')


if __name__ == '__main__':
    main()
