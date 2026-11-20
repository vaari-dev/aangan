#!/usr/bin/env python3
"""Checks the Play listing (fastlane/metadata) against Play's limits, and writes this version's
release notes there from CHANGELOG.md: python3 tools/store.py
"""
import os
import re

import release

LISTING = os.path.join(release.REPO_ROOT, "fastlane", "metadata", "android", "en-IN")
LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
NOTES_LIMIT = 500
MORE = "More: github.com/vaari-dev/aangan/releases"


def plain(markdown):
    s = re.sub(r"\*\*(.+?)\*\*|`(.+?)`", r"\1\2", markdown)
    return re.sub(r"\[(.+?)\]\(.+?\)", r"\1", s)


def play_notes():
    """Whole bullets while they fit, then a link to the rest."""
    lines = ["• " + plain(b) for b in release.bullets()]
    if len("\n".join(lines)) <= NOTES_LIMIT:
        return "\n".join(lines)
    kept = []
    for line in lines:
        if len("\n".join(kept + [line, MORE])) > NOTES_LIMIT:
            break
        kept.append(line)
    return "\n".join(kept + [MORE])


def main():
    for name, limit in LIMITS.items():
        with open(os.path.join(LISTING, name), encoding="utf-8") as f:
            length = len(f.read().rstrip("\n"))
        if length > limit:
            raise SystemExit(f"{name} is {length} characters; Play allows {limit}.")
    # No trailing newline: Play counts it.
    with open(os.path.join(LISTING, "changelogs", f"{release.code()}.txt"), "w", encoding="utf-8") as f:
        f.write(play_notes())


if __name__ == "__main__":
    main()
