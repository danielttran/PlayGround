#!/usr/bin/env python3
"""Reproduce the curated historical pack from public official WXF main lines.

Only factual moves/metadata are retained. Annotations and board code stay out of
the app. The manifest records raw-source hashes, game selection and exclusions.
Use --offline to reproduce from the cached downloaded source pages.
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
import sys
import time
from urllib.request import Request, urlopen

from download_wxf import STANDARD_BINIT, iccs_moves

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".cache/historical"
POLICY_PATH = ROOT / "data/historical-policy.json"
OUTPUT = ROOT / "app/src/main/assets/historical-games.json"
MANIFEST = ROOT / "data/historical-manifest.json"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def clean(value: str) -> str:
    return html.unescape(value).strip()


def name_key(value: str) -> str:
    return re.sub(r"[\s-]", "", value).casefold()


def normalized_date(value: str, year: int) -> str:
    """Preserve partial precision; zero-filled unknown dates never become Jan 1."""
    match = re.match(r"(\d{4})(?:[-./](\d{1,2}))?(?:[-./](\d{1,2}))?", value)
    if not match:
        return str(year)
    y, m, d = (int(part or 0) for part in match.groups())
    if y != year:
        return str(year)
    if not 1 <= m <= 12:
        return str(year)
    if d == 0:
        return f"{y:04}-{m:02}"
    try:
        return date(y, m, d).isoformat()
    except ValueError:
        return f"{y:04}-{m:02}"


def fetch(article: int, offline: bool) -> tuple[bytes, str]:
    url = f"https://www.wxf-xiangqi.org/index.php?id={article}&option=com_content&view=article&lang=en"
    path = CACHE / f"source-{article}.html"
    if path.exists():
        return path.read_bytes(), url
    if offline:
        raise FileNotFoundError(f"Offline cache missing: {path}")
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "XiangqiStudy historical archive curation"})
            with urlopen(request, timeout=45) as response:
                raw = response.read()
            path.write_bytes(raw)
            time.sleep(1)
            return raw, url
        except OSError:
            if attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))
    raise AssertionError("Unreachable")


def build(offline: bool) -> dict:
    policy_raw = POLICY_PATH.read_bytes()
    policy = json.loads(policy_raw)
    screening_raw = (ROOT / "data/curation-policy.json").read_bytes()
    excluded_names = json.loads(screening_raw)["excludedNames"]
    CACHE.mkdir(parents=True, exist_ok=True)
    games, sources, excluded = [], [], []
    seen = {}
    for source in policy["sources"]:
        article, year = source["article"], source["year"]
        raw, url = fetch(article, offline)
        blocks = re.findall(r"\[DhtmlXQiFrame\](.*?)\[/DhtmlXQiFrame\]", raw.decode("utf-8"), re.S)
        if not blocks:
            raise ValueError(f"No public game records in article {article}")
        sources.append({"article": article, "year": year, "url": url, "sha256": sha(raw), "recordCount": len(blocks)})
        for index, block in enumerate(blocks, 1):
            tags = {key: clean(value) for key, value in re.findall(r"\[DhtmlXQ_(\w+)\](.*?)\[/DhtmlXQ_\1\]", block, re.S)}
            ident = f"historical-wxf-{article}-g{index}"
            participants = tags.get("red", "") + " " + tags.get("black", "")
            reason = ""
            if re.search(r"women|女子", block, re.I):
                reason = "Women's record omitted by user collection preference"
            elif not any(name_key(name) in name_key(participants) for name in policy["masters"]):
                reason = "Outside curated master selection"
            elif not any(name_key(name) in name_key(participants) for name in policy["menMasters"]):
                reason = "No verified men's master-roster identity for collection admission"
            elif any(name in participants for name in excluded_names):
                reason = "Participant matches current curation exclusion policy"
            elif tags.get("gametype") != "慢棋":
                reason = "No explicit slow/classical game marker"
            elif tags.get("binit") != STANDARD_BINIT or tags.get("firstnum", "0") != "0":
                reason = "Nonstandard start or partial record"
            elif not tags.get("movelist"):
                reason = "No recorded moves"
            elif ident in policy["excludedRecords"]:
                reason = policy["excludedRecords"][ident]
            if reason:
                excluded.append({"id": ident, "reason": reason, "red": tags.get("red", ""), "black": tags.get("black", ""), "sourceUrl": url})
                continue
            if str(year) not in tags.get("event", ""):
                raise ValueError(f"Event/year conflict in {ident}: {tags.get('event')}")
            moves = iccs_moves(tags["movelist"])
            fingerprint = sha(" ".join(moves).encode())
            if fingerprint in seen:
                excluded.append({"id": ident, "reason": "Duplicate main line", "duplicateOf": seen[fingerprint], "sourceUrl": url})
                continue
            seen[fingerprint] = ident
            original_date = tags.get("date", "")
            override = policy["dateOverrides"].get(ident, {})
            played_date = override.get("date", normalized_date(original_date, year))
            result = {"红胜": "1-0", "黑胜": "0-1", "和局": "1/2-1/2", "未知": "*"}.get(tags.get("result"))
            if result is None:
                raise ValueError(f"Unknown result in {ident}")
            timer = tags.get("timerule", "")
            precise_clock = timer in ("60分＋30秒", "60分+30秒", "60 分钟+30 秒", "60+30")
            game = {
                "id": ident,
                "collection": policy["name"],
                "event": f"World Championship {year}",
                "round": tags.get("round") or "Historical masters",
                "date": played_date,
                "datePrecision": {4: "year", 7: "month", 10: "day"}[len(played_date)],
                "sourceDate": original_date,
                "red": tags["red"],
                "black": tags["black"],
                "result": result,
                "timeControl": "3600+30" if precise_clock else "Classical · exact clock unrecorded",
                "sourceTimeControl": timer,
                "sourceGameType": tags["gametype"],
                "classicalEvidence": "Official WXF record explicitly labels this game 慢棋 (slow/classical).",
                "division": "Men",
                "divisionEvidence": policy["menRosterEvidence"],
                "sourceUrl": url,
                "sourceTitle": tags.get("title", ""),
                "moves": moves,
            }
            if override.get("note"):
                game["dateNote"] = override["note"]
            games.append(game)
    games.sort(key=lambda game: (game["date"], game["id"]))
    pack = {"collection": policy["name"], "source": "World Xiangqi Federation historical championship archive", "games": games}
    output_raw = (json.dumps(pack, ensure_ascii=False, indent=2) + "\n").encode()
    OUTPUT.write_bytes(output_raw)
    manifest = {
        "schemaVersion": 1, "collection": policy["name"], "sourcePolicySha256": sha(policy_raw),
        "curationPolicySha256": sha(screening_raw), "assetSha256": sha(output_raw),
        "games": len(games), "plies": sum(len(game["moves"]) for game in games),
        "byEvent": dict(sorted(Counter(game["event"] for game in games).items())),
        "datePrecision": dict(Counter(game["datePrecision"] for game in games)),
        "sources": sources, "excluded": excluded,
        "scope": "Men's curated master records only, using the explicit WXF Male titled-player roster and men's historical awards list. Women's records are omitted by user preference. Factual main lines and metadata only; classical status comes from each official record and precise clock is asserted only where recorded.",
        "divisionCounts": {"Men": len(games)},
    }
    MANIFEST.write_bytes((json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    return {key: manifest[key] for key in ("games", "plies", "byEvent", "datePrecision", "assetSha256")}


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    print(json.dumps(build(args.offline), ensure_ascii=False, indent=2))
