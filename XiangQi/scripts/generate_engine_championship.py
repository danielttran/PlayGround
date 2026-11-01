import os
import sys
import re
import time
import json
import hashlib
import subprocess
from concurrent.futures import ProcessPoolExecutor

# Load 60 seeds from .cache/verify_60_seeds.py
with open('.cache/verify_60_seeds.py', 'r', encoding='utf-8') as f:
    code = f.read()
idx = code.find('OPENING_SEEDS = [')
idx2 = code.find('\n]', idx) + 2
exec(code[idx:idx2], globals())

TARGET_DEPTH = 16
MOVETIME_MS = 350
MAX_PLIES = 160

PIKA_BIN = os.path.abspath('.cache/pikafish/Pikafish-Windows-x86-64-universal.exe')
FAIRY_BIN = os.path.abspath('.cache/fairy-stockfish/fairy-stockfish-nnue.exe')
NNUE_FILE = os.path.abspath('app/src/main/assets/pikafish.nnue')
JAVA_BIN = r'C:\Dev\Tools\jdk-21.0.12.1+1\bin\java.exe'

def iccs_to_fairy(m):
    return f"{m[0]}{int(m[1])+1}{m[2]}{int(m[3])+1}"

def fairy_to_iccs(m):
    match = re.fullmatch(r'([a-i])(\d+)([a-i])(\d+)', m)
    return f"{match.group(1)}{int(match.group(2))-1}{match.group(3)}{int(match.group(4))-1}"

def play_match(args):
    game_idx, opening_name, seed_moves, red_is_pika, match_num, leg = args

    pika = subprocess.Popen([PIKA_BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    fairy = subprocess.Popen([FAIRY_BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)

    pika.stdin.write(f'uci\nsetoption name Threads value 1\nsetoption name Hash value 32\nsetoption name EvalFile value {NNUE_FILE}\nisready\n')
    pika.stdin.flush()
    while 'readyok' not in pika.stdout.readline(): pass

    fairy.stdin.write('uci\nsetoption name UCI_Variant value xiangqi\nsetoption name Threads value 1\nsetoption name Hash value 32\nisready\n')
    fairy.stdin.flush()
    while 'readyok' not in fairy.stdout.readline(): pass

    moves = list(seed_moves)
    red_name = "Pikafish NNUE 2026.09" if red_is_pika else "Fairy-Stockfish 14 NNUE"
    black_name = "Fairy-Stockfish 14 NNUE" if red_is_pika else "Pikafish NNUE 2026.09"

    red_engine = pika if red_is_pika else fairy
    black_engine = fairy if red_is_pika else pika

    ply = len(moves)
    result = "1/2-1/2"

    while ply < MAX_PLIES:
        side_red = (ply % 2 == 0)
        curr_engine = red_engine if side_red else black_engine
        is_pika_turn = (side_red and red_is_pika) or (not side_red and not red_is_pika)

        if is_pika_turn:
            hist = ' '.join(moves)
        else:
            hist = ' '.join([iccs_to_fairy(m) for m in moves])

        curr_engine.stdin.write(f'position startpos moves {hist}\ngo depth {TARGET_DEPTH} movetime {MOVETIME_MS}\n')
        curr_engine.stdin.flush()

        bestmove = None
        eval_cp = 0
        while True:
            line = curr_engine.stdout.readline()
            if not line: break
            line = line.strip()
            if 'score cp ' in line:
                try:
                    parts = line.split()
                    eval_cp = int(parts[parts.index('cp') + 1])
                except Exception:
                    pass
            elif 'score mate ' in line:
                try:
                    parts = line.split()
                    dist = int(parts[parts.index('mate') + 1])
                    eval_cp = 30000 if dist > 0 else -30000
                except Exception:
                    pass
            if line.startswith('bestmove'):
                raw = line.split()[1]
                bestmove = raw if is_pika_turn else fairy_to_iccs(raw)
                break

        if not bestmove or bestmove == '(none)':
            result = "0-1" if side_red else "1-0"
            break

        if eval_cp >= 1800:
            result = "1-0" if side_red else "0-1"
            moves.append(bestmove)
            break
        elif eval_cp <= -1800:
            result = "0-1" if side_red else "1-0"
            moves.append(bestmove)
            break

        moves.append(bestmove)
        ply += 1

        # Check threefold repetition
        if len(moves) >= 12:
            if moves[-1] == moves[-5] == moves[-9] and moves[-2] == moves[-6] == moves[-10]:
                result = "1/2-1/2"
                break

    try:
        pika.stdin.write('quit\n'); pika.stdin.flush()
        fairy.stdin.write('quit\n'); fairy.stdin.flush()
        pika.terminate(); fairy.terminate()
    except Exception:
        pass

    return {
        "id": f"modern-engine-{game_idx:03d}",
        "collection": "modern-engine",
        "engineGame": True,
        "engineIdentity": "source_declared",
        "event": f"{opening_name} (Match {match_num:02d} • Leg {leg})",
        "round": f"Match {match_num} - Leg {leg} ({'Pika Red' if red_is_pika else 'Fairy Red'})",
        "date": "2026-10-31",
        "red": red_name,
        "black": black_name,
        "result": result,
        "moves": moves,
        "metadataNote": f"Opening {match_num}/60 (Leg {leg}/2): {opening_name}. Depth {TARGET_DEPTH} NNUE clash. Red: {red_name}, Black: {black_name}.",
        "timeControl": f"Depth {TARGET_DEPTH}, NNUE"
    }

def main():
    print("=== Modern Xiangqi Engine Grand Championship (120 NNUE Games) ===")
    print("Format: Paired 2-Legged Match Per Opening (Leg 1: Pika Red / Leg 2: Fairy Red)")
    print(f"Engine 1: Pikafish NNUE 2026.09 (50MB NNUE neural evaluation)")
    print(f"Engine 2: Fairy-Stockfish 14 NNUE (Embedded Xiangqi NNUE)")
    print(f"Depth: {TARGET_DEPTH} | Max Move Time: {MOVETIME_MS}ms")
    print(f"Total Openings: {len(OPENING_SEEDS)} x 2 legs = {len(OPENING_SEEDS) * 2} games")

    matches = []
    idx = 1
    match_num = 1
    # Arrange consecutively per opening: Leg 1 then Leg 2
    for name, seed in OPENING_SEEDS:
        matches.append((idx, name, seed, True, match_num, 1))
        idx += 1
        matches.append((idx, name, seed, False, match_num, 2))
        idx += 1
        match_num += 1

    t0 = time.time()
    results = []

    workers = 4
    print(f"Running tournament with {workers} parallel worker processes...")
    with ProcessPoolExecutor(max_workers=workers) as executor:
        for i, game in enumerate(executor.map(play_match, matches), 1):
            results.append(game)
            elapsed = time.time() - t0
            print(f"[{i:03d}/{len(matches):03d}] {elapsed:.1f}s ({len(game['moves'])} plies, {game['result']}) - {game['event']}")

    total_time = time.time() - t0
    print(f"\nAll {len(results)} games finished in {total_time/60:.2f} minutes!")

    # Verify all games against Java ReplayCheck
    print("Verifying 100% legal moves in Java core...")
    tsv_lines = [f"{g['id']}\t{' '.join(g['moves'])}" for g in results]
    test_tsv = '.cache/championship_verify.tsv'
    with open(test_tsv, 'w', encoding='utf-8') as f:
        f.write('\n'.join(tsv_lines))

    proc = subprocess.run([JAVA_BIN, '-cp', 'build/engine-replay-check', 'org.xiangqi.study.core.ReplayCheck', test_tsv],
                          capture_output=True, text=True)
    print("Java Validator OUT:", proc.stdout.strip())
    if proc.returncode != 0 or "FAIL" in proc.stderr:
        print("Java Validator ERR:", proc.stderr.strip())
        sys.exit(1)

    out_file = 'app/src/main/assets/engine-games.json'
    with open(out_file, 'w', encoding='utf-8') as f:
        json.dump({"games": results}, f, ensure_ascii=False, indent=2)

    content = open(out_file, 'rb').read()
    sha256 = hashlib.sha256(content).hexdigest()

    manifest = {
        "schemaVersion": 1,
        "asset": "engine-games.json",
        "assetSha256": sha256,
        "games": len(results),
        "halfMoves": sum(len(g['moves']) for g in results),
        "generatedAt": "2026-10-31T19:20:00Z",
        "status": "verified"
    }

    with open('data/engine-games-manifest.json', 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2)

    print(f"Wrote {len(results)} games to {out_file} (SHA: {sha256[:12]})")
    print(f"Updated data/engine-games-manifest.json")

if __name__ == "__main__":
    main()
