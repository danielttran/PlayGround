"""Bounded Pikafish screening of legally replayed master-game candidates.

This is selection evidence, not a full accuracy analysis or a ranking of players.
Uses one thread and fixed node counts so it does not occupy the whole computer.
"""
import argparse
import hashlib
import json
from pathlib import Path
import queue
import re
import subprocess
import threading
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', type=Path, required=True)
    parser.add_argument('--network', type=Path, required=True)
    parser.add_argument('--nodes', type=int, default=50000)
    parser.add_argument('--candidates', type=Path, required=True)
    parser.add_argument('--replay', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    candidates = json.loads(args.candidates.read_text(encoding='utf-8'))
    replay = {line.split('\t')[0]: line.split('\t')[1:]
              for line in args.replay.read_text(encoding='utf-8-sig').splitlines()}
    engine = args.engine.resolve()
    network = args.network.resolve()
    process = subprocess.Popen([str(engine)], cwd=network.parent,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True,
                               encoding='utf-8', errors='replace', bufsize=1)
    pending = queue.Queue()

    def reader():
        for line in process.stdout:
            pending.put(line.rstrip())
        pending.put(None)

    threading.Thread(target=reader, daemon=True).start()

    def send(command):
        process.stdin.write(command + '\n')
        process.stdin.flush()

    def until(prefix):
        deadline, lines = time.monotonic() + 45, []
        while True:
            line = pending.get(timeout=max(0.1, deadline - time.monotonic()))
            if line is None:
                raise RuntimeError('Pikafish exited during screening')
            if time.monotonic() > deadline:
                raise TimeoutError(prefix)
            if 'ERROR' in line or 'CRITICAL' in line:
                raise RuntimeError(line)
            lines.append(line)
            if line.startswith(prefix):
                return lines

    reviewed = []
    proof = {'engineSha256': hashlib.sha256(engine.read_bytes()).hexdigest(),
             'networkSha256': hashlib.sha256(network.read_bytes()).hexdigest(),
             'nodesPerSample': args.nodes, 'threads': 1, 'hashMB': 64,
             'method': 'Recorded histories at plies 20, 40, 60, three quarters and final. '
                       'Bounded search supports selection; it is not exhaustive game grading.',
             'records': reviewed}
    try:
        send('uci'); until('uciok')
        send('setoption name Threads value 1')
        send('setoption name Hash value 64')
        send('setoption name EvalFile value ' + str(network))
        send('isready'); until('readyok')
        for index, candidate in enumerate(candidates):
            ident, moves = candidate['sha'], candidate['moves']
            if replay[ident][0] != 'OK':
                reviewed.append({'sha': ident, 'rejected': 'Illegal source mainline'})
                continue
            send('ucinewgame')
            samples = []
            for ply in sorted({20, 40, 60, len(moves) * 3 // 4, len(moves)}):
                send('position startpos moves ' + ' '.join(moves[:ply]))
                send('go nodes ' + str(args.nodes))
                lines = until('bestmove')
                score = None
                depth = 0
                for line in lines:
                    match = re.search(r'\bscore (cp|mate) (-?\d+)', line)
                    if match and ' lowerbound ' not in line and ' upperbound ' not in line:
                        kind, value = match[1], int(match[2])
                        value = value if kind == 'cp' else (10000 if value > 0 else -10000)
                        score = value if ply % 2 == 0 else -value
                        found = re.search(r'\bdepth (\d+)', line)
                        depth = int(found[1]) if found else 0
                # A terminal position can report only bestmove (none).
                if score is None:
                    if ply != len(moves) or replay[ident][-1] != '0':
                        raise ValueError('No engine score: ' + ident)
                    score = -10000 if ply % 2 == 0 else 10000
                samples.append({'ply': ply, 'redCp': score, 'depth': depth})
            reviewed.append({'sha': ident, 'samples': samples})
            args.output.write_bytes((json.dumps(proof, ensure_ascii=False, indent=2) + '\n').encode())
            if (index + 1) % 20 == 0:
                print(f'Screened {index + 1}/{len(candidates)} master-game candidates', flush=True)
    finally:
        try:
            send('quit'); process.wait(timeout=10)
        except (OSError, subprocess.TimeoutExpired):
            process.kill(); process.wait()
    print('Screened', len(reviewed), 'candidates:', args.output)


if __name__ == '__main__':
    main()
