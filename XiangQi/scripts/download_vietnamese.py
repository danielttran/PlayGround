#!/usr/bin/env python3
"""Curate additional Vietnamese adult classical games from verified 2022–2023 sources.

Only factual main-line moves and event metadata enter the app. Cached public
HTML stays out of Git. Earlier inspected archives without clock evidence are
held out. The broader 2023 World Championship pack owns its records, retaining
their original IDs; this supplement deduplicates against it. --fetch-only downloads sources without changing assets;
--offline reproduces the selected pack from the downloaded source cache.
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
from urllib.parse import urljoin, quote
from urllib.request import Request, urlopen
from download_wxf import Links, STANDARD_BINIT, iccs_moves

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".cache/vietnamese"
POLICY = ROOT / "data/vietnamese-policy.json"
OUTPUT = ROOT / "app/src/main/assets/vietnamese-games.json"
MANIFEST = ROOT / "data/vietnamese-manifest.json"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def fetch(url, filename, offline):
    path = CACHE / filename
    if path.exists():
        return path.read_bytes()
    if offline:
        raise FileNotFoundError(f"Offline source missing: {url}")
    for attempt in range(3):
        try:
            req = Request(quote(url, safe=":/?=&%"), headers={"User-Agent": "XiangqiStudy factual game-record archive"})
            with urlopen(req, timeout=45) as response:
                raw = response.read(5_000_001)
            if len(raw) > 5_000_000:
                raise ValueError("Source page exceeds 5 MB")
            path.write_bytes(raw)
            time.sleep(1)
            return raw
        except OSError:
            if attempt == 2:
                raise
            time.sleep(attempt + 2)
    raise AssertionError("Unreachable")


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


def source_date(raw, year):
    # Never invent a day/month that the source does not record.
    match = re.fullmatch(r"\s*(\d{4})[./-](\d{1,2})[./-](\d{1,2})\s*", raw)
    if match and int(match[1]) == year:
        y, m, d = map(int, match.groups())
        if m and d:
            try:
                return date(y, m, d).isoformat()
            except ValueError:
                pass
        if 1 <= m <= 12:
            return f"{y:04}-{m:02}"
    match = re.fullmatch(r"\s*(\d{1,2})/(\d{1,2})/(\d{4})\s*", raw)
    if match and int(match[3]) == year:
        d, m, y = map(int, match.groups())
        try:
            return date(y, m, d).isoformat()
        except ValueError:
            pass
    return str(year)


def source_pages(source, offline):
    raw = fetch(source["indexUrl"], f"index-{source['year']}.html", offline)
    parser = Links()
    parser.feed(raw.decode("utf-8-sig"))
    found = {}
    for href, label in parser.items:
        if re.search(source["adultRoundPattern"], label):
            url = urljoin(source["indexUrl"], href)
            ident = re.search(r"[?&]id=(\d+)", url)
            if ident:
                found[int(ident[1])] = (url, label.strip())
    if len(found) < source["minimumPages"]:
        raise ValueError(f"Missing adult round links for {source['year']}: {len(found)}")
    return raw, sorted(found.items())


def build(offline=False, fetch_only=False):
    CACHE.mkdir(parents=True, exist_ok=True)
    policy_raw = POLICY.read_bytes()
    policy = json.loads(policy_raw)
    screening_raw = (ROOT / "data/curation-policy.json").read_bytes()
    excluded_names = json.loads(screening_raw)["excludedNames"]
    existing = []
    for name in ("games.json", "historical-games.json", "world-2023-games.json"):
        existing += json.loads((ROOT / "app/src/main/assets" / name).read_text(encoding="utf-8"))["games"]
    seen = {sha(" ".join(g["moves"]).encode()): g["id"] for g in existing}
    games, sources, exclusions = [], [], []
    for source in policy["sources"]:
        year = source["year"]
        index_raw, pages = source_pages(source, offline)
        sources.append({"kind": "index", "year": year, "url": source["indexUrl"], "sha256": sha(index_raw), "bytes": len(index_raw)})
        for article, (url, label) in pages:
            raw = fetch(url, f"source-{article}.html", offline)
            blocks = re.findall(r"\[DhtmlXQiFrame\](.*?)\[/DhtmlXQiFrame\]", raw.decode("utf-8-sig"), re.S)
            sources.append({"kind": "round", "article": article, "year": year, "url": url, "label": label,
                            "sha256": sha(raw), "bytes": len(raw), "recordCount": len(blocks)})
            print(f"{year} article {article}: {len(blocks)} records", flush=True)
            for number, block in enumerate(blocks, 1):
                tags = {k: clean(v) for k, v in re.findall(r"\[DhtmlXQ_(\w+)\](.*?)\[/DhtmlXQ_\1\]", block, re.S)}
                ident = f"vietnam-wxf-{year}-{article}-g{number}"
                original_names = {side: tags.get(side, "") for side in ("red", "black")}
                original_result = tags.get("result", "")
                metadata_override = policy.get("metadataOverrides", {}).get(ident, {})
                tags.update(metadata_override.get("fields", {}))
                red, black = (canonical_name(tags.get(s, ""), policy) for s in ("red", "black"))
                participants = tags.get("red", "") + " " + tags.get("black", "")
                if not ("越南" in participants or red in source["vietnamesePlayers"] or black in source["vietnamesePlayers"]):
                    continue
                reject = {"id": ident, "red": red, "black": black, "sourceUrl": url}
                reason = ""
                matched = sorted(set((red, black)) & set(excluded_names))
                if matched:
                    reason = "Participant matches current curation exclusion policy"
                    reject["matchedNames"] = matched
                elif not tags.get("movelist"):
                    reason = "No recorded moves (bye/forfeit/unrecorded)"
                elif tags.get("binit") != STANDARD_BINIT or tags.get("firstnum", "0") != "0":
                    reason = "Nonstandard start or partial record"
                elif tags.get("gametype") in ("快棋", "超快棋") or re.search(r"快棋|超快|rapid|blitz", tags.get("title", ""), re.I):
                    reason = "Explicit rapid/blitz marker"
                elif source.get("requireSlowMarker") and tags.get("gametype") != "慢棋":
                    reason = "No explicit slow/classical marker"
                elif ident in policy["excludedRecords"]:
                    reason = policy["excludedRecords"][ident]
                if reason:
                    exclusions.append(dict(reject, reason=reason))
                    continue
                moves = iccs_moves(tags["movelist"])
                fingerprint = sha(" ".join(moves).encode())
                if fingerprint in seen:
                    exclusions.append(dict(reject, reason="Duplicate main line", duplicateOf=seen[fingerprint]))
                    continue
                # The round links are restricted to the adult main championship,
                # whose clock is documented by each source's official rules.
                timer = tags.get("timerule", "")
                if timer and timer not in source["allowedSourceClocks"]:
                    exclusions.append(dict(reject, reason="Conflicting/unverified record time control", sourceTimeControl=timer))
                    continue
                result = {"红胜": "1-0", "黑胜": "0-1", "和局": "1/2-1/2"}.get(tags.get("result"))
                if result is None:
                    exclusions.append(dict(reject, reason="Unresolved game result"))
                    continue
                played = source_date(tags.get("date", ""), year)
                override = policy["dateOverrides"].get(ident)
                if override:
                    played = override["date"]
                division = "Women" if "Women" in label or "女子组" in label else "Men"
                round_match = re.search(r"R(\d+)T(\d+)", tags.get("title", ""))
                page_round = re.search(r"Round\s*(\d+)", label)
                readable_round = f"{division} · Round {int(round_match[1])} · Board {int(round_match[2])}" if round_match else f"{division} · Round {int(page_round[1])}" if page_round else label
                game = {
                    "id": ident, "collection": policy["name"], "event": f"World Championship {year}",
                    "round": readable_round, "sourceRoundPage": label, "sourceRound": tags.get("round", ""), "date": played,
                    "datePrecision": {4: "year", 7: "month", 10: "day"}[len(played)], "sourceDate": tags.get("date", ""),
                    "red": red, "black": black, "sourceRed": original_names["red"], "sourceBlack": original_names["black"],
                    "result": result, "sourceResult": original_result, "timeControl": "3600+30" if timer in source["allowedSourceClocks"] else source["timeControl"], "sourceTimeControl": timer,
                    "sourceGameType": tags.get("gametype", ""), "classicalEvidence": source["classicalEvidence"],
                    "classicalRegulationsUrl": source["regulationsUrl"], "sourceUrl": url,
                    "sourceTitle": tags.get("title", ""), "moves": moves,
                }
                if override:
                    game["dateNote"] = override["note"]
                if metadata_override:
                    game["metadataNote"] = metadata_override["note"]
                games.append(game)
                seen[fingerprint] = ident
    supplement = ROOT / "data/futa-trenh-2023.json"
    supplement_proof = ROOT / "data/futa-trenh-manifest.json"
    if supplement.exists():
        supplement_raw = supplement.read_bytes()
        proof = json.loads(supplement_proof.read_text(encoding="utf-8"))
        if sha(supplement_raw) != proof["candidateSha256"]:
            raise ValueError("Trenh factual snapshot differs from its audited manifest")
        for item in json.loads(supplement_raw)["games"]:
            if set((item["red"], item["black"])) & set(excluded_names):
                raise ValueError("Excluded source-name match in Trenh supplement")
            fingerprint = sha(" ".join(item["moves"]).encode())
            if fingerprint in seen:
                raise ValueError("Duplicate Trenh supplement main line")
            item["collection"] = policy["name"]
            games.append(item)
            seen[fingerprint] = item["id"]
        sources.append({"kind": "audited factual supplement", "path": str(supplement.relative_to(ROOT)),
                        "sha256": sha(supplement_raw), "sourceManifest": str(supplement_proof.relative_to(ROOT))})
    games.sort(key=lambda g: (g["date"], g["id"]), reverse=True)
    pack = {"collection": policy["name"], "source": "Official World Xiangqi Federation adult championship archives", "games": games}
    payload = (json.dumps(pack, ensure_ascii=False, indent=2) + "\n").encode()
    manifest = {
        "collection": policy["name"], "screeningDate": policy["screeningDate"],
        "scope": "Additional 2022 World Championship and 2023 Phuong Trang international adult classical games with a Vietnamese player. The broader 2023 World Championship pack owns its records; duplicate main lines, rapid/blitz/youth and unverified older clocks are excluded.",
        "games": len(games), "halfMoves": sum(len(g["moves"]) for g in games),
        "yearCounts": dict(Counter(g["date"][:4] for g in games)),
        "vietnamesePlayerCounts": {p: sum(p in (g["red"], g["black"]) for g in games) for p in sorted(set(p for s in policy["sources"] for p in s["vietnamesePlayers"]) | set(policy.get("additionalVietnamesePlayers", [])))},
        "assetSha256": sha(payload), "policySha256": sha(policy_raw), "curationPolicySha256": sha(screening_raw),
        "deduplicatedAgainst": ["games.json", "historical-games.json", "world-2023-games.json"], "excludedRecords": exclusions, "sources": sources,
        "rights": "Attributed factual moves and event metadata only. WXF retains rights in its pages; no annotations, page design or board code copied and no general redistribution license asserted.",
    }
    if not fetch_only:
        OUTPUT.write_bytes(payload)
        MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        (CACHE / "candidate-games.json").write_bytes(payload)
        (CACHE / "candidate-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ("games", "halfMoves", "yearCounts", "vietnamesePlayerCounts")}, ensure_ascii=True))
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--fetch-only", action="store_true")
    args = parser.parse_args()
    build(args.offline, args.fetch_only)
