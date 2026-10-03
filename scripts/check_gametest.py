#!/usr/bin/env python3
"""Decides whether an in-game test run passed.

The test framework can exit successfully even when a test failed (for example if the game window is
closed), so this checks the evidence instead: no failure in the log, and every screenshot taken.

Usage: check_gametest.py <run dir> <log file>
"""

import json
import re
import sys
from pathlib import Path

EXPECTED_SCREENSHOTS = [
    "fishingfriend-distance-aiming-at-spot",
    "fishingfriend-distance-far-enough",
    "fishingfriend-distance-back-in-spot",
    "fishingfriend-stats",
    "fishingfriend-config-sounds",
    "fishingfriend-config-spots",
]
FAILURE = re.compile(r"gametests failed|StackOverflowError|Mixin apply .* failed|Exception in thread \"Render thread\"")


def main():
    run_dir, log = Path(sys.argv[1]), Path(sys.argv[2])
    problems = []

    text = log.read_text(errors="replace") if log.exists() else ""
    if not text:
        problems.append(f"no log at {log}")
    for line in text.splitlines():
        if FAILURE.search(line):
            problems.append("log: " + line.strip()[:300])

    taken = {re.sub(r"^\d+_", "", p.stem) for p in (run_dir / "screenshots").glob("*.png")}
    for name in EXPECTED_SCREENSHOTS:
        if name not in taken:
            problems.append(f"missing screenshot {name}")

    for line in text.splitlines():
        for marker, label in (("FISHING_FRIEND_TICK_COST", "tick cost"), ("FISHING_FRIEND_PREDICTION", "cast prediction")):
            if marker in line:
                print(label + ": " + line[line.index(marker) + len(marker) + 1:].strip())

    stats_file = run_dir / "config" / "fishingfriend-stats.json"
    if stats_file.exists():
        stats = json.loads(stats_file.read_text())
        print(f"stats: bites={stats['bites']} catches={stats['catches']} empty={stats['emptyCatches']} items={stats['items']}")

    if problems:
        print("FAIL")
        for problem in problems:
            print("  " + problem)
        sys.exit(1)
    print(f"PASS ({len(taken)} screenshots)")


if __name__ == "__main__":
    main()
