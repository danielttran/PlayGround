#!/usr/bin/env python3
"""Replay the optional full-game collection and compare it with bundled games.

Uses the production Java Position and PGN parser. No network requests, Android
build, device access, or changes to application assets are performed.
"""
import argparse
from collections import defaultdict
import hashlib
import json
import os
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
START = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
JAVA = r'''
package org.xiangqi.study.core;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class IngeniousCollectionAudit {
  public static void main(String[] args)throws Exception {
    int delivered=Integer.parseInt(args[2]);
    List<Pgn.Game> pgn=Pgn.parse(new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8));
    if(pgn.size()!=delivered)throw new IllegalArgumentException("PGN count differs from JSON");
    int row=0;
    for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)) {
      String[] f=line.split("\t",-1);Position p=Position.fromFen(f[1]);
      List<String> moves=Arrays.asList(f[2].split(" "));
      if(row<delivered) {
        if(!p.toFen().equals(Position.START_FEN))throw new IllegalArgumentException("Study starting position in delivered collection");
        Pgn.Game g=pgn.get(row);
        if(!g.moves.equals(moves)||!g.result.equals(f[3]))throw new IllegalArgumentException("PGN/JSON moves or result differ at "+f[0]);
        if(!g.round.equals(f[4])||!g.event.equals(f[5])||!g.date.equals(f[6])||!g.red.equals(f[7])||!g.black.equals(f[8]))throw new IllegalArgumentException("PGN/JSON chapter or display metadata differ at "+f[0]);
      }
      StringBuilder states=new StringBuilder(key(p));
      for(String move:moves){p=p.play(move);states.append('|').append(key(p));}
      if(row<delivered&&p.legalMoves().isEmpty()){
        String winner=p.redToMove?"0-1":"1-0";
        if(!f[3].equals(winner))throw new IllegalArgumentException("Terminal winner mismatch at "+f[0]);
      }
      System.out.println(f[0]+"\t"+states);row++;
    }
  }
  static String key(Position p){String[] f=p.toFen().split(" ");return f[0]+" "+f[1];}
}
'''


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(java_home):
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    for name, expected in manifest["files"].items():
        if sha(HERE / name) != expected["sha256"]:
            raise ValueError("Delivery file differs from source manifest: " + name)
    games = json.loads((HERE / "Ingenious-Xiangqi-full-games.json").read_text(encoding="utf-8"))["games"]
    if len(games) != manifest["games"] or sum(len(g["moves"]) for g in games) != manifest["halfMoves"]:
        raise ValueError("Delivery counts differ from manifest")
    numbers = [g["number"] for g in games]
    if numbers != sorted(set(numbers)) or numbers != manifest["sourceChapters"]:
        raise ValueError("Chapter order or identities changed")
    if any(g["round"] != "Book game " + str(g["number"]) for g in games):
        raise ValueError("Searchable book chapter labels changed")
    bundled = []
    packs = json.loads((ROOT / "app/src/main/assets/game-packs.json").read_text(encoding="utf-8"))["packs"]
    for entry in packs:
        if not entry.get("engineGame", False):
            bundled += json.loads((ROOT / "app/src/main/assets" / entry["file"]).read_text(encoding="utf-8"))["games"]
    work = ROOT / "build/ingenious-collection-verification"
    classes = work / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    source = work / "IngeniousCollectionAudit.java"
    source.write_bytes(JAVA.encode("utf-8"))
    rows = games + bundled
    if len({g["id"] for g in rows}) != len(rows):
        raise ValueError("Game ID collision")
    records = work / "records.tsv"
    records.write_bytes("".join("\t".join([g["id"], g.get("startFen") or START, " ".join(g["moves"]), g["result"],
                                          g.get("round", ""), g.get("event") or "Historical game", g.get("date", ""), g["red"], g["black"]]) + "\n" for g in rows).encode("utf-8"))
    core = ROOT / "app/src/main/java/org/xiangqi/study/core"
    subprocess.run([str(java_home / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(classes),
                    str(core / "Position.java"), str(core / "Pgn.java"), str(source)], check=True, timeout=60)
    result = subprocess.run([str(java_home / "bin/java.exe"), "-Xmx512m", "-cp", str(classes),
                             "org.xiangqi.study.core.IngeniousCollectionAudit", str(records),
                             str(HERE / "Ingenious-Xiangqi-full-games.pgn"), str(len(games))],
                            capture_output=True, text=True, encoding="utf-8", check=True, timeout=120)
    states = {line.split("\t", 1)[0]: line.split("\t", 1)[1].split("|") for line in result.stdout.splitlines()}
    if len(states) != len(rows):
        raise ValueError("Incomplete Java replay output")

    def fingerprint(game):
        return states[game["id"]][0] + "|" + " ".join(game["moves"])

    bundled_exact = defaultdict(list)
    bundled_positions = defaultdict(list)
    for game in bundled:
        bundled_exact[fingerprint(game)].append(game["id"])
        for ply, state in enumerate(states[game["id"]]):
            bundled_positions[state].append((game, ply))
    exact, excerpts, later = [], [], []
    within = []
    for index, game in enumerate(games):
        for ident in bundled_exact[fingerprint(game)]:
            exact.append({"chapter": game["number"], "bundledId": ident})
        for other, start in bundled_positions[states[game["id"]][0]]:
            count = 0
            for left, right in zip(game["moves"], other["moves"][start:]):
                if left != right:
                    break
                count += 1
            if count >= 8 and count == min(len(game["moves"]), len(other["moves"]) - start) and fingerprint(game) != fingerprint(other):
                excerpts.append({"chapter": game["number"], "bundledId": other["id"], "bundledStartPly": start, "matchingPlies": count})
        for ply, state in enumerate(states[game["id"]]):
            if ply < 16:
                continue
            for other, start in bundled_positions.get(state, []):
                if start < 16:
                    continue
                count = 0
                for left, right in zip(game["moves"][ply:], other["moves"][start:]):
                    if left != right:
                        break
                    count += 1
                if count >= 12:
                    later.append({"chapter": game["number"], "bundledId": other["id"], "collectionPly": ply, "bundledPly": start, "matchingPlies": count})
        for other in games[index + 1:]:
            shorter = min(len(game["moves"]), len(other["moves"]))
            if game["moves"][:shorter] == other["moves"][:shorter]:
                within.append({"chapters": [game["number"], other["number"]], "matchingPlies": shorter})
    report = {"status": "PASS", "games": len(games), "halfMoves": sum(len(g["moves"]) for g in games),
              "allStartAtNormalInitialPosition": True, "jsonPgnMainLinesAgree": True, "jsonPgnChapterAndDisplayMetadataAgree": True,
              "bundledGamesCompared": len(bundled), "exactDuplicateMatches": exact,
              "excerptOrTruncationMatches": excerpts, "laterSharedContinuationCandidates": later,
              "withinCollectionFullOrPrefixDuplicates": within,
              "scope": "Legal replay and canonical factual overlap comparison; completeness and metadata evidence remain in manifest.json."}
    output = ROOT / "test-results/ingenious-delivery-audit.json"
    output.parent.mkdir(exist_ok=True)
    output.write_bytes((json.dumps(report, indent=2) + "\n").encode("utf-8"))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, default=Path(os.environ.get("JAVA_HOME", r"C:\Dev\Tools\jdk-21.0.12.1+1")))
    args = parser.parse_args()
    main(args.java_home)
