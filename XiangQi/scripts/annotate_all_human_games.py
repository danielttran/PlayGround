import os
import sys
import time
import json
import subprocess
from concurrent.futures import ProcessPoolExecutor

PIKA_BIN = os.path.abspath('.cache/pikafish/Pikafish-Windows-x86-64-universal.exe')
NNUE_FILE = os.path.abspath('app/src/main/assets/pikafish.nnue')

PIECES = {
    'R': 'Chariot', 'N': 'Horse', 'B': 'Elephant', 'A': 'Advisor', 'K': 'General', 'C': 'Cannon', 'P': 'Soldier',
    'r': 'Chariot', 'n': 'Horse', 'b': 'Elephant', 'a': 'Advisor', 'k': 'General', 'c': 'Cannon', 'p': 'Pawn'
}

def init_board():
    b = {}
    for c in 'abcdefghi':
        for r in '0123456789':
            b[c+r] = None
    b['a0']='R'; b['b0']='N'; b['c0']='B'; b['d0']='A'; b['e0']='K'; b['f0']='A'; b['g0']='B'; b['h0']='N'; b['i0']='R'
    b['b2']='C'; b['h2']='C'
    b['a3']='P'; b['c3']='P'; b['e3']='P'; b['g3']='P'; b['i3']='P'
    b['a9']='r'; b['b9']='n'; b['c9']='b'; b['d9']='a'; b['e9']='k'; b['f9']='a'; b['g9']='b'; b['h9']='n'; b['i9']='r'
    b['b7']='c'; b['h7']='c'
    b['a6']='p'; b['c6']='p'; b['e6']='p'; b['g6']='p'; b['i6']='p'
    return b

def format_move(board, move):
    src, dst = move[:2], move[2:4]
    p = board.get(src)
    name = PIECES.get(p, "Piece")
    return f"{name} {src}–{dst}"

def play(board, move):
    src, dst = move[:2], move[2:4]
    p = board.get(src)
    board[src] = None
    board[dst] = p

worker_pika = None

def init_worker():
    global worker_pika
    worker_pika = subprocess.Popen([PIKA_BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    worker_pika.stdin.write(f'uci\nsetoption name Threads value 1\nsetoption name Hash value 16\nsetoption name EvalFile value {NNUE_FILE}\nisready\n')
    worker_pika.stdin.flush()
    while 'readyok' not in worker_pika.stdout.readline():
        pass

def analyze_single_game(game):
    global worker_pika
    moves = game.get('moves', [])
    if not moves:
        return game

    # Evaluate each position along mainline
    evals = [] # (bestmove, score_cp_for_mover, pv)
    for i in range(len(moves) + 1):
        hist = ' '.join(moves[:i])
        cmd = f"position startpos moves {hist}\n" if hist else "position startpos\n"
        worker_pika.stdin.write(cmd + "go depth 10\n")
        worker_pika.stdin.flush()
        bestmove = None
        score = 0
        pv = []
        while True:
            line = worker_pika.stdout.readline().strip()
            if 'score cp ' in line:
                try:
                    parts = line.split()
                    score = int(parts[parts.index('cp') + 1])
                    if ' pv ' in line:
                        pv = parts[parts.index('pv') + 1: parts.index('pv') + 5]
                except Exception:
                    pass
            elif 'score mate ' in line:
                try:
                    parts = line.split()
                    dist = int(parts[parts.index('mate') + 1])
                    score = 30000 if dist > 0 else -30000
                except Exception:
                    pass
            if line.startswith('bestmove'):
                bestmove = line.split()[1]
                break
        evals.append((bestmove, score, pv))

    # Track board state to name pieces
    board = init_board()
    board_history = []
    for m in moves:
        board_history.append(dict(board))
        play(board, m)

    # Collect candidate errors
    candidates = []
    for i in range(len(moves)):
        best_move, pre_score, pv = evals[i]
        played = moves[i]
        post_score = -evals[i+1][1]
        delta = pre_score - post_score
        is_turning_point = (pre_score >= 40 and post_score <= -120)

        if played != best_move and (delta >= 60 or is_turning_point):
            candidates.append({
                'ply': i + 1,
                'delta': delta,
                'played': played,
                'best_move': best_move,
                'pre_score': pre_score,
                'post_score': post_score,
                'is_turning_point': is_turning_point,
                'board_snapshot': board_history[i]
            })

    # Strict threshold: keep only genuine errors (Inaccuracies >= 60 cp, Mistakes, Blunders, Turning points)
    # If completely clean (0 errors >= 60), include at most 1 notable alternative if delta >= 35
    if len(candidates) == 0:
        for i in range(4, len(moves)):
            best_move, pre_score, pv = evals[i]
            played = moves[i]
            post_score = -evals[i+1][1]
            delta = pre_score - post_score
            if played != best_move and delta >= 35:
                candidates.append({
                    'ply': i + 1,
                    'delta': delta,
                    'played': played,
                    'best_move': best_move,
                    'pre_score': pre_score,
                    'post_score': post_score,
                    'is_turning_point': False,
                    'board_snapshot': board_history[i]
                })
        candidates.sort(key=lambda x: -x['delta'])
        candidates = candidates[:1]

    # Upper bound of 8 in wild games to keep UI clean, but no artificial lower cap
    if len(candidates) > 8:
        candidates.sort(key=lambda x: -x['delta'])
        candidates = candidates[:8]

    curated = sorted(candidates, key=lambda x: x['ply'])

    annotations = {}
    tp_move = None

    for item in curated:
        ply = item['ply']
        delta = item['delta']
        is_red = ((ply - 1) % 2 == 0)
        mover = "Red" if is_red else "Black"
        move_num = (ply + 1) // 2

        loss = delta / 100.0
        loss_str = f"-{loss:.2f}"

        if delta >= 250:
            glyph = "??"
            tag = f"Critical blunder ({loss_str})"
            desc = f"Decisive error by {mover}."
        elif delta >= 120:
            glyph = "?"
            tag = f"Mistake ({loss_str})"
            desc = f"Loses the advantage for {mover}."
        elif delta >= 60:
            glyph = "?!"
            tag = f"Inaccuracy ({loss_str})"
            desc = f"Inaccurate move by {mover}."
        else:
            glyph = "!?"
            tag = f"Alternative line ({loss_str})"
            desc = f"Suboptimal choice by {mover}."

        if item['is_turning_point']:
            tag += " · ⚡ Turning point"
            if tp_move is None:
                tp_move = move_num

        best_desc = format_move(item['board_snapshot'], item['best_move'])
        comment = f"{tag}. {desc} Pikafish preferred {best_desc}."
        annotations[str(ply)] = {
            'glyph': glyph,
            'bestMove': item['best_move'],
            'comment': comment
        }

    # Generate game summary
    result = game.get('result', '')
    if result == '1-0':
        summary = f"Red victory. Decisive tactical turning point around move {tp_move or (len(moves)//4)}."
    elif result == '0-1':
        summary = f"Black victory. Successful counterattack seizing the initiative around move {tp_move or (len(moves)//4)}."
    elif result in ('1/2-1/2', 'draw'):
        summary = "Master-level draw. Balanced defense and piece coordination throughout the endgame."
    else:
        summary = "Classical master tournament clash."

    game['annotations'] = annotations
    game['summary'] = summary
    return game

def process_pack(pack_file):
    filepath = os.path.join('app/src/main/assets', pack_file)
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)

    is_dict = isinstance(data, dict)
    games = data['games'] if is_dict else data

    print(f"\nAnalyzing {pack_file} ({len(games)} games)...")
    t0 = time.time()

    annotated = []
    # Use 6 parallel workers
    with ProcessPoolExecutor(max_workers=6, initializer=init_worker) as executor:
        for idx, res in enumerate(executor.map(analyze_single_game, games), 1):
            annotated.append(res)
            if idx % 20 == 0 or idx == len(games):
                print(f"  [{pack_file}] {idx}/{len(games)} games analyzed (elapsed {time.time()-t0:.1f}s)...")

    if is_dict:
        data['games'] = annotated
    else:
        data = annotated

    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    total_ann = sum(len(g.get('annotations', {})) for g in annotated)
    print(f"Saved {pack_file}: {len(annotated)} games, {total_ann} curated annotations ({time.time()-t0:.1f}s)")

def main():
    packs = [
        'tactical-miniatures.json',
        'games.json',
        'world-2023-games.json',
        'historical-games.json',
        'vietnamese-games.json',
        'vietnamese-classics.json',
        'futa2025-games.json',
        'master-classics.json'
    ]

    t_all = time.time()
    for p in packs:
        process_pack(p)

    print(f"\n==========================================")
    print(f"ALL 8 HUMAN PACKS ANNOTATED IN {time.time()-t_all:.1f}s!")
    print(f"==========================================")

if __name__ == '__main__':
    main()
