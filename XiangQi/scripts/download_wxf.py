#!/usr/bin/env python3
"""Download factual main-line records from WXF's public 2025 adult championship pages.

Standard library only. Cached requests, retries and one-second pacing keep requests
bounded. Re-run with --offline to reproduce assets without a network connection.
HTML, annotations, photos and the third-party board implementation are not bundled.
"""
from __future__ import annotations

import argparse
import hashlib
import html
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import time
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
INDEX_URL = "https://www.wxf-xiangqi.org/index.php?Itemid=101&id=1788&lang=en&option=com_content&view=article"
STANDARD_BINIT = "0919293949596979891777062646668600102030405060708012720323436383"


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.items = []
        self.href = None
        self.text = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self.href = dict(attrs).get("href")
            self.text = []

    def handle_data(self, data):
        if self.href is not None:
            self.text.append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self.href is not None:
            self.items.append((self.href, " ".join(self.text)))
            self.href = None


def round_links(source: str):
    parser = Links()
    parser.feed(source)
    found = {}
    for href, title in parser.items:
        match = re.search(r"2025 19th World Xiangqi Championships (Men|Women) Round (\d+) Game Records", title)
        if match:
            key = (match[1], int(match[2]))
            url = urljoin(INDEX_URL, href)
            if urlparse(url).hostname not in ("www.wxf-xiangqi.org", "wxf-xiangqi.org"):
                raise ValueError("Unexpected external round URL")
            found[key] = url
    if len(found) != 16:
        raise ValueError(f"Expected 9 men's and 7 women's rounds, found {len(found)}")
    return sorted((division, number, url) for (division, number), url in found.items())


def iccs_moves(encoded: str):
    encoded = re.sub(r"\s", "", encoded)
    if not encoded or len(encoded) % 4 or not re.fullmatch(r"[0-9]+", encoded):
        raise ValueError("Invalid DhtmlXQ main line")
    moves = []
    for offset in range(0, len(encoded), 4):
        x1, y1, x2, y2 = map(int, encoded[offset:offset + 4])
        if max(x1, x2) > 8:
            raise ValueError("Invalid file")
        if (x1, y1) == (x2, y2):
            raise ValueError("Null move")
        moves.append(f"{chr(97 + x1)}{9 - y1}{chr(97 + x2)}{9 - y2}")
    return moves


def iso_date(value: str):
    match = re.fullmatch(r"\s*(?:(2025)[.\-/年])?(\d{1,2})[.\-/月](\d{1,2})(?:日)?\s*", value)
    if not match:
        return value.strip()
    return f"2025-{int(match[2]):02d}-{int(match[3]):02d}"


def parse_games(source: str, division: str, number: int, url: str, exclusions=None):
    games = []
    blocks = re.findall(r"\[DhtmlXQiFrame\](.*?)\[/DhtmlXQiFrame\]", source, re.S)
    for index, block in enumerate(blocks, 1):
        tags = dict(re.findall(r"\[DhtmlXQ_(\w+)\](.*?)\[/DhtmlXQ_\1\]", block, re.S))
        if not tags.get("movelist"):
            if exclusions is not None:
                exclusions.append({"division": division, "round": number, "game": index,
                                   "title": tags.get("title", ""), "reason": "No recorded moves (e.g. forfeit)", "sourceUrl": url})
            continue
        if tags.get("binit") != STANDARD_BINIT:
            raise ValueError(f"Nonstandard start requires explicit conversion: {division} {number} {index}")
        if tags.get("firstnum", "0") != "0":
            raise ValueError("Unexpected nonzero firstnum")
        results = {"红胜": "1-0", "黑胜": "0-1", "和局": "1/2-1/2", "未知": "*"}
        result = results.get(tags.get("result", "").strip())
        if result is None:
            raise ValueError(f"Unknown result: {tags.get('result')}")
        title = html.unescape(tags.get("title", "").strip())
        final = "冠亚军决赛" in title
        red = re.sub(r"^\d+", "", html.unescape(tags.get("red", "").strip()))
        black = re.sub(r"^\d+", "", html.unescape(tags.get("black", "").strip()))
        if not red or not black:
            raise ValueError(f"Missing players: {title}")
        board_match = re.search(r"\b\d+[-－](\d+)\b", title)
        board = int(board_match[1]) if board_match else index
        game = {
            "id": f"wxf-2025-{division.lower()}-r{number}-g{index}",
            "event": "World Championship 2025",
            "round": f"{division} · " + ("Final" if final else f"Round {number} · Board {board}"),
            "date": iso_date(tags.get("date", "")),
            "red": red,
            "black": black,
            "result": result,
            "timeControl": "3600+30",
            "sourceTimeControl": tags.get("timerule", "").strip(),
            "sourceUrl": url,
            "sourceTitle": title,
            "moves": iccs_moves(tags["movelist"]),
        }
        if game["sourceTimeControl"] not in ("60 分钟+30 秒", "60+30"):
            raise ValueError(f"Unverified classical time control: {game['sourceTimeControl']}")
        games.append(game)
    if not games:
        raise ValueError(f"No embedded games: {division} {number}")
    return games


class Downloader:
    def __init__(self, cache: Path, offline: bool, refresh: bool):
        self.cache, self.offline, self.refresh = cache, offline, refresh
        self.cache.mkdir(parents=True, exist_ok=True)
        self.last_request = 0.0
        self.sources = []

    def fetch(self, url: str):
        path = self.cache / (hashlib.sha256(url.encode()).hexdigest() + ".html")
        if self.refresh or not path.exists():
            if self.offline:
                raise FileNotFoundError(f"Offline cache missing: {url}")
            for attempt in range(3):
                time.sleep(max(0, 1 - (time.monotonic() - self.last_request)))
                try:
                    request = Request(url, headers={"User-Agent": "XiangQiStudy/1.0 (personal game-record archive)"})
                    self.last_request = time.monotonic()
                    with urlopen(request, timeout=45) as response:
                        payload = response.read(5_000_001)
                    if len(payload) > 5_000_000:
                        raise ValueError("Page exceeds 5 MB download limit")
                    path.write_bytes(payload)
                    break
                except Exception:
                    if attempt == 2:
                        raise
                    time.sleep(2 ** attempt)
        payload = path.read_bytes()
        self.sources.append({"url": url, "sha256": hashlib.sha256(payload).hexdigest(), "bytes": len(payload)})
        return payload.decode("utf-8-sig")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--cache", type=Path, default=ROOT / ".cache" / "wxf")
    parser.add_argument("--output", type=Path, default=ROOT / "app/src/main/assets/games.json")
    args = parser.parse_args()
    if args.offline and args.refresh:
        parser.error("--offline and --refresh are mutually exclusive")
    downloader = Downloader(args.cache, args.offline, args.refresh)
    links = round_links(downloader.fetch(INDEX_URL))
    games = []
    counts = {}
    exclusions = []
    policy = json.loads((ROOT / "data/curation-policy.json").read_text(encoding="utf-8"))
    for division, number, url in links:
        records = parse_games(downloader.fetch(url), division, number, url, exclusions)
        counts[f"{division} round {number}"] = len(records)
        games.extend(records)
        print(f"{division} round {number}: {len(records)} games", flush=True)
    downloaded_count = len(games)
    selected = []
    for game in games:
        matched = sorted(set((game["red"], game["black"])) & set(policy["excludedNames"]))
        if matched:
            exclusions.append({"id": game["id"], "title": game["sourceTitle"],
                               "reason": "Conservative disciplinary-notice name match", "matchedNames": matched,
                               "sourceUrl": game["sourceUrl"]})
            continue
        for side in ("red", "black"):
            game[side] = policy["aliases"].get(game[side], game[side])
        selected.append(game)
    games = selected
    # The final leads, then requested players, then the remaining archive.
    def priority(game):
        if "Final" in game["round"]:
            return (0, "", game["id"])
        players = [game["red"], game["black"]]
        focus = next((i + 1 for i, name in enumerate(policy["preferredPlayers"])
                      if any(name in player for player in players)), len(policy["preferredPlayers"]) + 1)
        return (focus, -int(game["date"].replace("-", "")), game["id"])
    games.sort(key=priority)
    final = [game for game in games if "Final" in game["round"]]
    if len(final) != 1 or len(final[0]["moves"]) != 108:
        raise ValueError("The verified 108-ply men's final was not found exactly once")
    payload = (json.dumps({"games": games}, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    manifest = {
        "tournament": "19th World Xiangqi Championship, Shanghai, 2025",
        "scope": "Adult men rounds 1–9 and women rounds 1–7; published main lines only",
        "indexUrl": INDEX_URL,
        "games": len(games), "halfMoves": sum(len(game["moves"]) for game in games),
        "roundCounts": counts,
        "downloadedGameCount": downloaded_count,
        "excludedRecords": exclusions,
        "curationPolicy": "data/curation-policy.json",
        "classicalRegulationsUrl": "https://www.wxf-xiangqi.org/images/2025wxf_shanghai/19th_WXF__World_Xiangqi_Championship_Regulations.pdf",
        "assetSha256": hashlib.sha256(payload).hexdigest(),
        "sources": downloader.sources,
        "notation": "ICCS; a0 is Red's bottom-left, i9 is top-right as viewed by Red",
        "rights": "WXF site states All Rights Reserved. These are attributed factual moves and event metadata; no annotations, page design or board code copied. No general redistribution license asserted.",
    }
    manifest_path = ROOT / "data/wxf-manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(games)} games / {manifest['halfMoves']} half-moves to {args.output}")


if __name__ == "__main__":
    main()
