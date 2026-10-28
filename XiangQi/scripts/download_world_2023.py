#!/usr/bin/env python3
"""Fetch the eligible adult classical 2023 World Championship factual game records.

The complete published adult archive is examined, without a player-strength or
best-game ranking. Existing identity exclusions, explicit rapid markers, missing
moves/results and duplicate main lines are held out. No annotations are copied.
--offline reproduces the pack from its bounded cache; --refresh revisits sources.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import date
import hashlib
import html
import json
from pathlib import Path
import re
import time
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen
from download_wxf import Links, STANDARD_BINIT, iccs_moves

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "data/world-2023-policy.json"
OUTPUT = ROOT / "app/src/main/assets/world-2023-games.json"
MANIFEST = ROOT / "data/world-2023-manifest.json"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def clean(value):
    return html.unescape(value).strip()


def canonical_name(value, policy):
    value = re.sub(r"^\s*\d+[.、\s]*", "", clean(value))
    for prefix in policy["countryPrefixes"]:
        if value.startswith(prefix):
            value = value[len(prefix):].strip()
            break
    value = re.sub(r"\s+", "" if re.search(r"[\u3400-\u9fff]", value) else " ", value)
    return policy["nameVariants"].get(value, value)


def fingerprint(moves):
    # Every admitted record has the ordinary starting board.
    return sha(" ".join(moves).encode())


class Sources:
    def __init__(self, cache, offline, refresh):
        self.cache, self.offline, self.refresh = cache, offline, refresh
        cache.mkdir(parents=True, exist_ok=True)
        self.last_request = 0.0
        self.items = []

    def fetch(self, url, filename, kind, limit=5_000_000):
        path = self.cache / filename
        if self.refresh or not path.exists():
            if self.offline:
                raise FileNotFoundError(f"Offline source missing: {url}")
            for attempt in range(3):
                time.sleep(max(0, 1 - (time.monotonic() - self.last_request)))
                try:
                    request = Request(url, headers={"User-Agent": "XiangqiStudy factual game-record archive"})
                    self.last_request = time.monotonic()
                    with urlopen(request, timeout=45) as response:
                        raw = response.read(limit + 1)
                    if len(raw) > limit:
                        raise ValueError(f"Source exceeds {limit} byte limit: {url}")
                    path.write_bytes(raw)
                    break
                except OSError:
                    if attempt == 2:
                        raise
                    time.sleep(attempt + 2)
        raw = path.read_bytes()
        if kind in ("regulations", "program") and not raw.startswith(b"%PDF"):
            raise ValueError(f"Expected official PDF: {url}")
        evidence = {"kind": kind, "url": url, "sha256": sha(raw), "bytes": len(raw)}
        previous = ROOT / ".cache/vietnamese" / filename
        if previous.exists():
            evidence["previousCacheSha256"] = sha(previous.read_bytes())
        self.items.append(evidence)
        return raw, evidence


def adult_rounds(raw, policy):
    links = Links()
    links.feed(raw.decode("utf-8-sig"))
    found = {}
    for href, title in links.items:
        match = re.search(r"2023 18th World Xiangqi Championships (Men|Women) Round (\d+) Game Records", title)
        if not match:
            continue
        division, number = match[1], int(match[2])
        url = urljoin(policy["indexUrl"], href)
        if urlparse(url).hostname not in ("wxf-xiangqi.org", "www.wxf-xiangqi.org"):
            raise ValueError("Unexpected external round URL")
        article = re.search(r"[?&]id=(\d+)", url)
        if not article:
            raise ValueError(f"Round article ID missing: {url}")
        key = (division, number)
        if key in found and found[key][0] != url:
            raise ValueError(f"Conflicting round links: {key}")
        found[key] = (url, int(article[1]), clean(title))
    expected = {(division, n) for division, count in policy["expectedRounds"].items() for n in range(1, count + 1)}
    if set(found) != expected:
        raise ValueError(f"Adult round coverage differs: missing={expected-set(found)}, extra={set(found)-expected}")
    return [(division, number, *found[(division, number)]) for division, number in sorted(found)]


def ordinal(text):
    if text.isdecimal():
        return int(text)
    digits = {c: n for n, c in enumerate("零一二三四五六七八九")}
    if "十" in text:
        first, last = text.split("十")
        return (digits[first] if first else 1) * 10 + (digits[last] if last else 0)
    return digits[text]


def board_number(title, division):
    if division == "Men":
        # Two original titles mistakenly say 第一台/第三台 in the round slot.
        # The last 台 ordinal still explicitly identifies the actual board.
        boards = re.findall(r"第([一二三四五六七八九十零\d]+)台", title)
        if boards:
            return ordinal(boards[-1])
    else:
        match = re.match(r"(\d+)", title)
        if match:
            return int(match[1])
    raise ValueError(f"Missing explicit board number: {title}")


def iso_date(value):
    match = re.fullmatch(r"(\d{4})[./-](\d{1,2})[./-](\d{1,2})", value)
    if not match:
        raise ValueError(f"Unresolved source date: {value!r}")
    return date(*map(int, match.groups())).isoformat()


def verify_women_results(games, policy):
    table = policy["womenFinalCrossTable"]
    players = {row["name"]: row for row in table["players"]}
    checked = 0
    for game in games:
        if not game["round"].startswith("Women"):
            continue
        number = int(re.search(r"Round (\d+)", game["round"])[1]) - 1
        red, black = players[game["red"]], players[game["black"]]
        if red["opponents"][number] != black["number"] or black["opponents"][number] != red["number"]:
            raise ValueError(f"Women's final cross-table pairing disagrees: {game['sourceRecord']}")
        scores = [row["cumulativePoints"][number] - (row["cumulativePoints"][number-1] if number else 0) for row in (red, black)]
        expected = {(2, 0): "1-0", (0, 2): "0-1", (1, 1): "1/2-1/2"}.get(tuple(scores))
        if expected != game["result"]:
            raise ValueError(f"Women's final cross-table result disagrees: {game['sourceRecord']}")
        checked += 1
    if checked != 49:
        raise ValueError(f"Expected all49 Women's records for cross-table check, got{checked}")
    return {"games": checked, "sourceUrl": table["sourceUrl"], "pairingsAndResultsAgree": True, "method": table["method"]}


def build(args):
    policy_raw = POLICY.read_bytes()
    policy = json.loads(policy_raw)
    curation_raw = (ROOT / "data/curation-policy.json").read_bytes()
    excluded_names = set(json.loads(curation_raw)["excludedNames"])
    sources = Sources(args.cache, args.offline, args.refresh)
    index, _ = sources.fetch(policy["indexUrl"], "index-2023.html", "index")
    sources.fetch(policy["regulationsUrl"], "regulations-2023.pdf", "regulations")
    sources.fetch(policy["programUrl"], "program-2023.pdf", "program", 10_000_000)
    for evidence_url in policy.get("metadataEvidenceImages", []):
        sources.fetch(evidence_url, urlparse(evidence_url).path.rsplit("/", 1)[-1], "metadata result image")
    seen, games, exclusions, raw_count = {}, [], [], 0
    rounds, corrections, legacy_used = [], [], set()
    for division, number, url, article, label in adult_rounds(index, policy):
        raw, evidence = sources.fetch(url, f"source-{article}.html", "round")
        blocks = re.findall(r"\[DhtmlXQiFrame\](.*?)\[/DhtmlXQiFrame\]", raw.decode("utf-8-sig"), re.S)
        if not blocks:
            raise ValueError(f"Missing embedded records: {label}")
        evidence.update(division=division, round=number, records=len(blocks))
        count = 0
        for source_number, block in enumerate(blocks, 1):
            raw_count += 1
            original = {key: clean(value) for key, value in re.findall(r"\[DhtmlXQ_(\w+)\](.*?)\[/DhtmlXQ_\1\]", block, re.S)}
            tags = dict(original)
            key = f"{article}-g{source_number}"
            metadata_override = policy["metadataOverrides"].get(key)
            if metadata_override:
                tags.update(metadata_override["fields"])
            title = tags.get("title", "")
            red, black = (canonical_name(tags.get(side, ""), policy) for side in ("red", "black"))
            matched = sorted({red, black} & excluded_names)
            reasons = []
            if not tags.get("movelist"):
                reasons.append("No recorded moves (bye/forfeit/unrecorded)")
            if tags.get("binit") != STANDARD_BINIT or tags.get("firstnum", "0") != "0":
                reasons.append("Nonstandard start or partial record")
            if re.search(r"快棋|超快|rapid|blitz", title, re.I) or tags.get("gametype") in ("快棋", "超快棋"):
                reasons.append("Explicit rapid/blitz marker")
            if matched:
                reasons.append("Participant matches current curation exclusion policy")
            result = {"红胜": "1-0", "黑胜": "0-1", "和局": "1/2-1/2"}.get(tags.get("result"))
            if result is None:
                reasons.append("Unresolved game result")
            if not red or not black:
                reasons.append("Unresolved player metadata")
            if tags.get("timerule", "") and tags["timerule"] not in policy["allowedSourceClocks"]:
                reasons.append("Conflicting record time control")
            if key in policy["excludedRecords"]:
                reasons.append(policy["excludedRecords"][key])
            if reasons:
                exclusions.append({"sourceRecord": key, "red": red, "black": black, "sourceTitle": title,
                                   "sourceUrl": url, "sourceDate": original.get("date", ""), "reasons": reasons,
                                   **({"matchedNames": matched} if matched else {})})
                continue
            if red == black:
                raise ValueError(f"Unrepaired identical player fields: {key}")
            moves = iccs_moves(tags["movelist"])
            digest = fingerprint(moves)
            if digest in seen:
                exclusions.append({"sourceRecord": key, "sourceUrl": url, "reasons": ["Duplicate main line"], "duplicateOf": seen[digest]})
                continue
            legacy = policy["legacyRecords"].get(key)
            if legacy and digest != legacy["movesSha256"]:
                raise ValueError(f"Previously bundled main line changed: {key}")
            ident = legacy["id"] if legacy else f"wxf-2023-{division.lower()}-r{number}-g{source_number}"
            if legacy:
                legacy_used.add(key)
            override = policy["dateOverrides"].get(key)
            played = override["date"] if override else iso_date(original.get("date", ""))
            expected_date = policy["roundDates"][division][str(number)]
            if played != expected_date:
                raise ValueError(f"Source date disagrees with official schedule: {key}, {played}, {expected_date}")
            board = board_number(title, division)
            game = {"id": ident, "collection": policy["name"], "event": "World Championship 2023",
                    "round": f"{division} · Round {number} · Board {board}", "date": played,
                    "red": red, "black": black, "result": result, "timeControl": policy["timeControl"],
                    "sourceUrl": url, "sourceTitle": title, "sourceRecord": key, "sourceRoundPage": label,
                    "sourceRound": original.get("round", ""), "sourceDate": original.get("date", ""),
                    "sourceRed": original.get("red", ""), "sourceBlack": original.get("black", ""),
                    "sourceResult": original.get("result", ""), "sourceTimeControl": original.get("timerule", ""),
                    "sourceGameType": original.get("gametype", ""), "datePrecision": "day",
                    "classicalEvidence": policy["classicalEvidence"], "classicalRegulationsUrl": policy["regulationsUrl"], "moves": moves}
            if not original.get("timerule", ""):
                game["timeControlNote"] = "Individual record omits its clock. The explicit official adult main-championship page is governed by the event regulations' 60-minute plus 30-second clock."
            if metadata_override:
                game["metadataNote"] = metadata_override["note"]
                game["metadataEvidenceUrls"] = metadata_override.get("evidenceUrls", [])
                corrections.append({"sourceRecord": key, "kind": metadata_override.get("kind", "players"), "note": game["metadataNote"]})
            if override:
                game["dateNote"] = override["note"]
                game["dateEvidenceUrls"] = override.get("evidenceUrls", [])
                corrections.append({"sourceRecord": key, "kind": "date", "note": game["dateNote"]})
            if key in policy.get("resultCrossChecks", {}):
                game["resultNote"] = policy["resultCrossChecks"][key]["note"]
                game["resultEvidenceUrl"] = policy["resultCrossChecks"][key]["evidenceUrl"]
            if division == "Men" and len(re.findall(r"第([一二三四五六七八九十零\d]+)台", title)) > 1:
                game["roundNote"] = "Source title uses 台 instead of 轮 in its round position. Round comes from the official adult round page; board is the final explicit 台 ordinal. Original title retained."
            games.append(game)
            seen[digest] = ident
            count += 1
        rounds.append({"division": division, "round": number, "article": article, "sourceRecords": len(blocks), "admittedGames": count})
        print(f"{division} round {number}: {count}/{len(blocks)} admitted", flush=True)
    if legacy_used != set(policy["legacyRecords"]):
        raise ValueError(f"Previously bundled games missing: {set(policy['legacyRecords'])-legacy_used}")
    women_validation = verify_women_results(games, policy)
    games.sort(key=lambda game: (game["date"], game["round"], game["id"]))
    payload = (json.dumps({"collection": policy["name"], "source": "Official World Xiangqi Federation adult championship archive", "games": games}, ensure_ascii=False, indent=2) + "\n").encode()
    manifest = {"collection": policy["name"], "scope": policy["scope"], "screeningDate": policy["screeningDate"],
                "games": len(games), "halfMoves": sum(len(game["moves"]) for game in games), "sourceRecordSlots": raw_count,
                "divisionCounts": dict(Counter(game["round"].split(" · ")[0] for game in games)),
                "legacyGamesPreserved": len(legacy_used), "newGames": len(games)-len(legacy_used), "rounds": rounds,
                "metadataCorrections": corrections, "excludedRecords": exclusions, "sources": sources.items,
                "resultCrossChecks": policy.get("resultCrossChecks", {}),
                "womenFinalCrossTableValidation": women_validation,
                "policySha256": sha(policy_raw), "curationPolicySha256": sha(curation_raw), "assetSha256": sha(payload),
                "notation": "ICCS; normal starting board, a0 at Red's lower-left",
                "rights": "Attributed factual main-line moves and event metadata only. No annotations, board code, photographs or source PDFs bundled. WXF retains rights in its pages; no blanket redistribution license asserted."}
    OUTPUT.write_bytes(payload)
    MANIFEST.write_bytes((json.dumps(manifest, ensure_ascii=False, indent=2)+"\n").encode("utf-8"))
    tsv = args.cache / "validated-games.tsv"
    tsv.write_text("\n".join(game["id"]+"\t"+" ".join(game["moves"])+"\t" for game in games)+"\n", encoding="utf-8")
    print(json.dumps({key: manifest[key] for key in ("games", "halfMoves", "divisionCounts", "legacyGamesPreserved", "newGames", "assetSha256")}, ensure_ascii=True))
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--cache", type=Path, default=ROOT / ".cache/world-2023")
    arguments = parser.parse_args()
    if arguments.offline and arguments.refresh:
        parser.error("--offline and --refresh are mutually exclusive")
    build(arguments)
