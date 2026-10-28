#!/usr/bin/env python3
"""Reproduce the screened 2009/2016 Vietnamese masters classical pack.

Only public factual game data is fetched. The policy pins normalized source
facts, event clocks, section identity and independent result/calendar evidence.
Notation is resolved against production Position, admitting exactly one entire
legal mainline. Ambiguous or changed source data is never silently repaired.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request

from extract_futa_trenh import JAVA as BASE_CONVERTER
from game_packs import read_packs

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache/vietnamese-classics'
POLICY = ROOT / 'data/vietnamese-classics-policy.json'
ASSET = ROOT / 'app/src/main/assets/vietnamese-classics.json'
MANIFEST = ROOT / 'data/vietnamese-classics-manifest.json'
SOURCE_URL = 'https://vietnamchess.vn/databank/xqbase-vietnamchess?id={}&view=game'

# The existing converter supplies piece/file/direction semantics. Unlike its
# original single-ply loop, this explores subsequent recorded moves as evidence
# when a source token omits a front/back marker. Two complete paths prove
# ambiguity; stopping then cannot turn an ambiguous game into an admitted one.
CONVERTER = BASE_CONVERTER.split('  public static void main')[0] + r'''
  static int visited;
  static List<List<String>> answers;
  static void solve(Position p,String[] words,int ply,boolean vn,List<String> path,Pattern pattern){
    if(++visited>25000)throw new IllegalArgumentException("Notation branching limit; manual source review required");
    if(answers.size()>1)return;
    if(ply==words.length){answers.add(new ArrayList<>(path));return;}
    Matcher token=pattern.matcher(words[ply]);
    if(!token.matches())throw new IllegalArgumentException("Bad token "+words[ply]);
    for(String move:p.legalMoves())if(matches(p,move,token,vn)){
      path.add(move);solve(p.play(move),words,ply+1,vn,path,pattern);path.remove(path.size()-1);
    }
  }
  public static void main(String[]args)throws Exception{
    BufferedReader in=new BufferedReader(new InputStreamReader(System.in,"UTF-8"));String line;
    while((line=in.readLine())!=null){String[] parts=line.split("\\t",3);boolean vn=parts[1].equals("vn");
      Pattern pattern=Pattern.compile(vn?"(Tg|[PMXBTS])([1-9ts])([./-])([1-9])":"([RHEAKCPNB])([1-9+-])([.+-])([1-9])");
      visited=0;answers=new ArrayList<>();
      solve(Position.initial(),parts[2].split(" "),0,vn,new ArrayList<>(),pattern);
      if(answers.size()!=1){System.out.println(parts[0]+"\tREJECT\t"+answers.size());continue;}
      List<String> moves=answers.get(0);Position p=Position.initial();for(String move:moves)p=p.play(move);
      System.out.println(parts[0]+"\t"+String.join(" ",moves)+"\t"+p.toFen());
    }
  }
}
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def facts_digest(source, keys):
    facts = {key: source[key].replace('\r\n', '\n') if isinstance(source[key], str)
             else source[key] for key in keys}
    return sha(json.dumps(facts, ensure_ascii=False, sort_keys=True,
                          separators=(',', ':')).encode('utf-8'))


def source_game(game_id, refresh):
    path = CACHE / f'game-{game_id}.html'
    if refresh or not path.exists():
        request = urllib.request.Request(SOURCE_URL.format(game_id),
                                         headers={'User-Agent': 'XiangqiStudy-source-audit/1.0'})
        with urllib.request.urlopen(request, timeout=40) as response:
            path.write_bytes(response.read())
    html = path.read_text(encoding='utf-8')
    match = re.search(r'CUR_GAME:\s*(\{.*?\}),\s*\n', html, re.S)
    if not match:
        raise ValueError(f'{game_id}: missing public structured game data')
    return json.loads(match[1])


def notation(source):
    body = re.sub(r'\[[^\]]*\]', '', source['pgn_text'])
    vn = bool(re.search(r'\b(?:Tg|M|X)[1-9ts][./-]', body))
    pattern = r'(?:Tg|[PMXBTS])[1-9ts][./-][1-9]' if vn else r'[RHEAKCPNB][1-9+-][.+-][1-9]'
    tokens = re.findall(pattern, body)
    residue = re.sub(pattern, ' ', body)
    residue = re.sub(r'\d+\.|\b(?:1-0|0-1|1/2-1/2)\b|[+*\s]', '', residue)
    if residue or not tokens:
        raise ValueError(f"{source['id']}: unparsed notation {residue!r}")
    return 'vn' if vn else 'wxf', tokens


def java_tools(explicit):
    options = [explicit, os.environ.get('JAVA_HOME'), r'C:\Dev\Tools\jdk-21.0.12.1+1']
    for option in options:
        if not option:
            continue
        directory = Path(option)
        if (directory / 'bin').is_dir():
            directory /= 'bin'
        suffix = '.exe' if os.name == 'nt' else ''
        if (directory / ('javac' + suffix)).exists():
            return str(directory / ('javac' + suffix)), str(directory / ('java' + suffix))
    if shutil.which('javac') and shutil.which('java'):
        return shutil.which('javac'), shutil.which('java')
    raise RuntimeError('A JDK is required; pass --java-bin with its bin directory')


def convert(sources, java_bin):
    javac, java = java_tools(java_bin)
    classes = CACHE / 'classes'
    classes.mkdir(exist_ok=True)
    converter = CACHE / 'FutaConvert.java'
    converter.write_text(CONVERTER, encoding='utf-8', newline='\n')
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(classes),
                    str(ROOT / 'app/src/main/java/org/xiangqi/study/core/Position.java'),
                    str(converter)], check=True)
    rows = []
    for source in sources:
        style, tokens = notation(source)
        rows.append(f"{source['id']}\t{style}\t{' '.join(tokens)}")
    run = subprocess.run([java, '-cp', str(classes), 'FutaConvert'],
                         input='\n'.join(rows) + '\n', encoding='utf-8',
                         capture_output=True, check=True)
    result = {}
    for line in run.stdout.splitlines():
        game_id, moves, fen = line.split('\t')
        result[int(game_id)] = (None, int(fen)) if moves == 'REJECT' else (moves.split(), fen)
    if len(result) != len(sources):
        raise ValueError('Converter did not return every candidate')
    return result


def verify_source(source, candidate, policy):
    game_id = candidate['sourceGameId']
    if facts_digest(source, policy['sourceFactsKeys']) != candidate['sourceFactsSha256']:
        raise ValueError(f'{game_id}: source facts changed; review before updating policy')
    listing = candidate['sourceListing']
    for field in ['round', 'round_num', 'board_num', 'white_player_name',
                  'black_player_name', 'result', 'date_played']:
        if source[field] != listing[field]:
            raise ValueError(f'{game_id}: viewer/listing disagreement for {field}')
    if listing['tiebreak'] or source['result'] not in ['1-0', '0-1', '1/2-1/2']:
        raise ValueError(f'{game_id}: tiebreak or missing result')
    if source['fen_initial'] != 'rheakaehr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RHEAKAEHR w - - 0 1':
        raise ValueError(f'{game_id}: source is not a complete game from the initial position')
    if candidate['year'] == '2009':
        day = {1: 21, 2: 21, 3: 22, 4: 22, 5: 23, 6: 23, 7: 24, 8: 25, 9: 25}[source['round_num']]
        if listing['tournament_id'] != 24 or source['date_played'] != f'2009-03-{day}':
            raise ValueError(f'{game_id}: outside admitted Swiss stage or official calendar')
        if str(game_id) not in policy['heldOut'] or policy['heldOut'][str(game_id)]['type'] != 'player-identity-conflict':
            cross = candidate['sourceCrossTable']
            red_score, black_score = {'1-0': ('1', '0'), '0-1': ('0', '1'),
                                      '1/2-1/2': ('½', '½')}[source['result']]
            if (cross['round'] != source['round_num']
                    or cross['redEntry'] != f"{cross['blackRank']}w{red_score}"
                    or cross['blackEntry'] != f"{cross['redRank']}b{black_score}"):
                raise ValueError(f'{game_id}: official cross-table opponent/color/result mismatch')
    elif listing['tournament_id'] not in [17, 18]:
        raise ValueError(f'{game_id}: outside 2016 knockout/classification stages')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true', help='refetch the 39 pinned public records')
    parser.add_argument('--check', action='store_true', help='validate without changing shipped outputs')
    parser.add_argument('--java-bin')
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    policy = json.loads(POLICY.read_text(encoding='utf-8'))
    curation_path = ROOT / policy['sharedCurationPolicy']
    curation = json.loads(curation_path.read_text(encoding='utf-8'))
    candidates = policy['candidates']
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        sources = list(pool.map(lambda item: source_game(item['sourceGameId'], args.refresh), candidates))
    for source, candidate in zip(sources, candidates):
        verify_source(source, candidate, policy)
    converted = convert(sources, args.java_bin)
    existing = {tuple(game['moves']) for entry, games, _ in read_packs()
                if entry['file'] != ASSET.name for game in games}
    games, records, rejected, seen = [], [], [], set()
    for source, candidate in zip(sources, candidates):
        game_id = source['id']
        holdout = policy['heldOut'].get(str(game_id))
        moves, final_fen = converted[game_id]
        if moves is None:
            if not holdout or holdout['type'] != 'notation-ambiguity' or final_fen != 2:
                raise ValueError(f'{game_id}: newly ambiguous or illegal source')
            rejected.append({'sourceGameId': game_id, 'completeLegalInterpretations': 2,
                             'reason': holdout['reason']})
            continue
        if holdout:
            if holdout['type'] == 'player-identity-conflict':
                rejected.append({'sourceGameId': game_id, 'completeLegalInterpretations': 1,
                                 'reason': holdout['reason']})
                continue
            raise ValueError(f'{game_id}: held-out interpretation changed; manual review required')
        if len(moves) != candidate['sourceListing']['ply_count']:
            raise ValueError(f'{game_id}: complete source move count mismatch')
        red = source['white_player_name_en'].split(',')[-1].strip()
        black = source['black_player_name_en'].split(',')[-1].strip()
        if red in curation['excludedNames'] or black in curation['excludedNames']:
            rejected.append({'sourceGameId': game_id, 'reason': 'Shared excluded-player policy'})
            continue
        if tuple(moves) in existing or tuple(moves) in seen:
            raise ValueError(f'{game_id}: duplicate complete mainline')
        seen.add(tuple(moves))
        event = policy['sources'][candidate['year']]
        round_label = (f"Swiss · Round {source['round']}" if candidate['year'] == '2009'
                       else f"{candidate['phase']} · Game {source['round']}")
        game = {
            'id': f"vietnam-classics-{candidate['year']}-{game_id}",
            'collection': policy['collection'], 'event': event['event'], 'round': round_label,
            'date': source['date_played'], 'datePrecision': 'day', 'sourceDate': source['date_played'],
            'red': red, 'black': black, 'sourceRed': source['white_player_name'],
            'sourceBlack': source['black_player_name'],
            'sourceRedBilingual': source['white_player_name_en'],
            'sourceBlackBilingual': source['black_player_name_en'],
            'result': source['result'], 'timeControl': event['timeControl'],
            'sourceTimeControl': '80 minutes + 30 seconds per move' if candidate['year'] == '2009'
                                 else '60 minutes + 30 seconds per move from move one',
            'classicalRegulationsUrl': event['classicalRegulationsUrl'],
            'classicalEvidence': event['classicalEvidence'],
            'sourceUrl': SOURCE_URL.format(game_id),
            'sourceTitle': source['white_player_name_en'] + ' – ' + source['black_player_name_en'],
            'sourceRound': source['round'], 'sourceBoardNumber': source['board_num'],
            'sourceSection': source['tournament_title'],
            'metadataStatus': 'Viewer/listing metadata and official results/calendar or bracket checked',
            'moves': moves,
        }
        games.append(game)
        records.append({'id': game['id'], 'sourceGameId': game_id,
                        'sourceFactsSha256': candidate['sourceFactsSha256'],
                        'mainlineSha256': sha(' '.join(moves).encode()),
                        'plies': len(moves), 'finalFen': final_fen})
    games.sort(key=lambda game: (game['date'], game['id']), reverse=True)
    asset = encoded({'collection': policy['collection'],
                     'source': 'Official VietnamChess federation database, regulations and results',
                     'games': games})
    manifest = encoded({'schemaVersion': 1, 'screeningDate': policy['screeningDate'],
                        'policySha256': sha(POLICY.read_bytes()),
                        'sharedCurationPolicySha256': sha(curation_path.read_bytes()),
                        'asset': ASSET.name, 'assetSha256': sha(asset), 'games': len(games),
                        'halfMoves': sum(len(game['moves']) for game in games),
                        'sourceCandidateCount': len(candidates),
                        'legalValidation': 'Production Position; exactly one complete legal notation trace; all source tokens and ply counts retained',
                        'deduplication': 'Exact full ICCS mainline against every other shipped pack and within this pack',
                        'sources': policy['sources'], 'records': records, 'rejected': rejected})
    for path, content in [(ASSET, asset), (MANIFEST, manifest)]:
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                raise ValueError(f'{path.name}: shipped output differs from validated source reproduction')
        else:
            path.write_bytes(content)
    print(f"{'Checked' if args.check else 'Wrote'} {len(games)} games, "
          f"{sum(len(game['moves']) for game in games)} plies; {len(rejected)} held out")


if __name__ == '__main__':
    main()
