#!/usr/bin/env python3
"""Prints the GitHub release notes for a version: which jar to pick, then that version's changelog.

Usage: release_notes.py <version>    (e.g. 1.1.1)
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def read_properties(path):
    props = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def version_key(text):
    return tuple(int(part) for part in re.findall(r"\d+", text))


def main():
    version = sys.argv[1]
    root_props = read_properties(ROOT / "gradle.properties")
    if root_props["mod_version"] != version:
        sys.exit(f"tag says {version} but gradle.properties has mod_version={root_props['mod_version']}")

    changelog = (ROOT / "CHANGELOG.md").read_text()
    match = re.search(rf"^## {re.escape(version)}\n(.*?)(?=^## |\Z)", changelog, re.S | re.M)
    if not match:
        sys.exit(f"CHANGELOG.md has no section for {version}")

    rows = []
    for props_file in (ROOT / "versions").glob("*/gradle.properties"):
        props = read_properties(props_file)
        label = props["mc_label"]
        first, _, last = label.partition("-")
        jar = f"{root_props['archives_base_name']}-{version}+mc{label}.jar"
        rows.append((version_key(first), f"| {first} – {last} | `{jar}` |" if last else f"| {first} | `{jar}` |"))

    print("A client-side Fabric mod that dings when a fish bites, warns you when your spot is fished out "
          "(handy on servers with overfishing rules), and tells you how far to move.\n")
    print("### Which jar do I need?\n")
    print("| Minecraft | Download |")
    print("| --- | --- |")
    for _, row in sorted(rows):
        print(row)
    print("\nRequires [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api). "
          "[Mod Menu](https://modrinth.com/mod/modmenu) is optional, for changing settings in-game.\n")
    print("### Changes\n")
    print(match.group(1).strip())


if __name__ == "__main__":
    main()
