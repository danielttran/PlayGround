#!/usr/bin/env python3
"""Smoke-test the bundled release's UCI protocol using its official Windows executable.

This validates the same command sequence as the Android adapter, not Android execution.
Usage: python scripts/test-pikafish.py --engine PATH/TO/Pikafish-Windows-x86-64-universal.exe
"""
import argparse
import os
from pathlib import Path
import queue
import subprocess
import threading
import time


ROOT = Path(__file__).resolve().parents[1]
START = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"


def run_case(engine, network, history, name):
    process = subprocess.Popen([str(engine)], cwd=network.parent, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace", bufsize=1)
    pending = queue.Queue()
    transcript = []

    def reader():
        try:
            for line in process.stdout:
                pending.put(line.rstrip("\r\n"))
        finally:
            pending.put(None)

    threading.Thread(target=reader, daemon=True).start()

    def send(command):
        transcript.append("> " + command)
        process.stdin.write(command + "\n")
        process.stdin.flush()

    def read_until(prefix, timeout):
        deadline = time.monotonic() + timeout
        while True:
            left = deadline - time.monotonic()
            if left <= 0:
                raise TimeoutError("Pikafish timeout waiting for " + prefix)
            line = pending.get(timeout=left)
            if line is None:
                raise RuntimeError("Pikafish exited before " + prefix)
            transcript.append(line)
            if "CRITICAL ERROR" in line or line.startswith("ERROR"):
                raise RuntimeError(line)
            if line.startswith(prefix):
                return line

    try:
        send("uci")
        read_until("uciok", 30)
        send("setoption name Threads value 1")
        send("setoption name Hash value 32")
        send("setoption name MultiPV value 1")
        send("setoption name EvalFile value " + str(network))
        send("isready")
        read_until("readyok", 30)
        send("ucinewgame")
        send("position startpos moves " + history)
        send("go depth 8 movetime 1500")
        best = read_until("bestmove ", 10)
        if best.split()[1] in ("(none)", "0000"):
            raise RuntimeError("Unexpected empty bestmove in normal opening")
        return transcript
    finally:
        try:
            send("stop")
            send("quit")
        except (BrokenPipeError, OSError):
            pass
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)
        (ROOT / "test-results" / ("native-protocol-" + name + ".txt")).write_text(
            "\n".join(transcript) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", type=Path, required=True)
    parser.add_argument("--network", type=Path)
    parser.add_argument("--java-home", type=Path, default=Path(os.environ.get(
        "JAVA_HOME", r"C:\Dev\Tools\jdk-21.0.12.1+1")))
    args = parser.parse_args()
    engine = args.engine.resolve()
    network = (args.network or engine.parent / "pikafish.nnue").resolve()
    if not engine.is_file() or not network.is_file():
        parser.error("Engine and NNUE network must both exist")
    (ROOT / "test-results").mkdir(exist_ok=True)
    classes = ROOT / "build" / "native-protocol-tests"
    classes.mkdir(parents=True, exist_ok=True)
    sources = list((ROOT / "app/src/main/java/org/xiangqi/study/core").glob("*.java"))
    sources.append(ROOT / "tests/core/NativeProtocolTest.java")
    subprocess.run([str(args.java_home / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(classes),
                    *map(str, sources)], check=True, timeout=60)
    summaries = ["Official Pikafish Windows UCI smoke; Android runtime is not exercised."]
    for name, history in (("red", "b2e2 b7e7"), ("black", "b2e2 b7e7 g3g4")):
        run_case(engine, network, history, name)
        result = subprocess.run([str(args.java_home / "bin/java.exe"), "-cp", str(classes),
                                 "org.xiangqi.study.core.NativeProtocolTest",
                                 str(ROOT / "test-results" / ("native-protocol-" + name + ".txt")),
                                 history], check=True, capture_output=True, text=True, timeout=30)
        summaries.append(result.stdout.strip())
    summary = "\n".join(summaries) + "\n"
    (ROOT / "test-results/native-protocol.txt").write_text(summary, encoding="utf-8")
    print(summary, end="")


if __name__ == "__main__":
    main()
