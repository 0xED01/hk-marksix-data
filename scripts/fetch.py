#!/usr/bin/env python3
"""Fetches Mark Six results from HKJC into data/*.json for the app to read through jsDelivr.

  fetch.py          refresh once
  fetch.py --watch  on a draw night, keep polling every 15 s until the result is out
"""
import gzip
import json
import os
import sys
import time
import urllib.request
from datetime import datetime, timedelta, timezone

UPSTREAM = "https://info.cld.hkjc.com/graphql/base/"
HKT = timezone(timedelta(hours=8))
POLL_SECONDS = 15
WATCH_UNTIL_HOUR = 23  # HKT; give up after 23:30
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")

# Must match the official site's whitelisted queries byte for byte.
FRAGMENT = "fragment lotteryDrawsFragment on LotteryDraw {\n  id\n  year\n  no\n  openDate\n  closeDate\n  drawDate\n  status\n  snowballCode\n  snowballName_en\n  snowballName_ch\n  lotteryPool {\n    sell\n    status\n    totalInvestment\n    jackpot\n    unitBet\n    estimatedPrize\n    derivedFirstPrizeDiv\n    lotteryPrizes {\n      type\n      winningUnit\n      dividend\n    }\n  }\n  drawResult {\n    drawnNo\n    xDrawnNo\n  }\n}"
QUERY_DRAW = FRAGMENT + "\n\nquery marksixDraw {\n  timeOffset {\n    m6\n    ts\n  }\n  lotteryDraws {\n    ...lotteryDrawsFragment\n  }\n}"
QUERY_RESULT = FRAGMENT + "\n\nquery marksixResult($lastNDraw: Int, $startDate: String, $endDate: String, $drawType: LotteryDrawType) {\n  lotteryDraws(\n    lastNDraw: $lastNDraw\n    startDate: $startDate\n    endDate: $endDate\n    drawType: $drawType\n  ) {\n    ...lotteryDrawsFragment\n  }\n}"


def query(op, variables, text):
    body = json.dumps({"operationName": op, "variables": variables, "query": text}).encode()
    req = urllib.request.Request(UPSTREAM, data=body, headers={
        "Content-Type": "application/json",
        "Accept-Encoding": "gzip",
        "User-Agent": "Mozilla/5.0",
    })
    with urllib.request.urlopen(req, timeout=20) as r:
        data = r.read()
        if r.headers.get("Content-Encoding", "").lower() == "gzip":
            data = gzip.decompress(data)
    draws = json.loads(data).get("data", {}).get("lotteryDraws")
    if not isinstance(draws, list):
        raise RuntimeError("unexpected response: %s" % data[:300])
    return draws


def current():
    return query("marksixDraw", {}, QUERY_DRAW)


def history():
    return query("marksixResult", {"lastNDraw": 30, "drawType": "All"}, QUERY_RESULT)


def has_result(d):
    return len((d.get("drawResult") or {}).get("drawnNo") or []) == 6


def save(name, draws):
    """Writes data/<name>.json; returns True if the draws changed."""
    path = os.path.join(DATA_DIR, name + ".json")
    try:
        with open(path, encoding="utf-8") as f:
            if json.load(f).get("lotteryDraws") == draws:
                return False
    except (OSError, ValueError):
        pass
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"updated": datetime.now(HKT).isoformat(timespec="seconds"), "lotteryDraws": draws},
                  f, ensure_ascii=False, separators=(",", ":"))
    return True


def log(msg):
    print(datetime.now(HKT).strftime("%H:%M:%S"), msg, flush=True)


def main():
    watch = "--watch" in sys.argv
    cur = current()
    save("current", cur)
    save("history", history())
    today = datetime.now(HKT).strftime("%Y-%m-%d")
    pending = [d for d in cur if not has_result(d) and d.get("drawDate", "")[:10] == today]
    if not watch or not pending:
        log("no draw pending tonight" if watch else "refreshed")
        return
    target = pending[0]["id"]
    deadline = datetime.now(HKT).replace(hour=WATCH_UNTIL_HOUR, minute=30, second=0)
    log("watching draw %s until %s" % (target, deadline.strftime("%H:%M")))
    while datetime.now(HKT) < deadline:
        time.sleep(POLL_SECONDS)
        try:
            cur = current()
        except Exception as e:
            log("fetch failed: %s" % e)
            continue
        if any(d["id"] == target and has_result(d) for d in cur):
            save("current", cur)
            for attempt in range(5):
                try:
                    save("history", history())
                    break
                except Exception as e:
                    log("history fetch failed: %s" % e)
                    time.sleep(5)
            log("result is out")
            return
    log("gave up waiting")


if __name__ == "__main__":
    main()
