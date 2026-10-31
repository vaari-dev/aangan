#!/usr/bin/env python3
"""The store listing in fastlane/metadata (read by F-Droid, and by Play tooling): python3 tools/store.py

Checks the text against Google Play's limits, and writes the current version's release notes,
changelogs/<versionCode>.txt, from CHANGELOG.md: plain text, cut to Play's 500 characters.
"""
import os
import re

import release

LISTING = os.path.join(release.REPO_ROOT, "fastlane", "metadata", "android", "en-US")
LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
NOTES_LIMIT = 500
MORE = "More: github.com/vaari-dev/aangan/releases"


def version_code():
    with open(os.path.join(release.REPO_ROOT, "app", "build.gradle.kts"), encoding="utf-8") as f:
        return int(re.search(r"^\s*versionCode = (\d+)", f.read(), re.M).group(1))


def plain(markdown):
    s = re.sub(r"\*\*(.+?)\*\*", r"\1", markdown)
    s = re.sub(r"`(.+?)`", r"\1", s)
    return re.sub(r"\[(.+?)\]\((.+?)\)", r"\1", s)


def play_notes(name=None):
    """Whole bullets, in order, while they fit; then a pointer to the full notes."""
    bullets = ["• " + plain(b[2:]) for b in release.notes(name).splitlines()]
    if len("\n".join(bullets)) <= NOTES_LIMIT:
        return "\n".join(bullets)
    kept = []
    for b in bullets:
        if len("\n".join(kept + [b, MORE])) > NOTES_LIMIT:
            break
        kept.append(b)
    return "\n".join(kept + [MORE])


def main():
    for name, limit in LIMITS.items():
        with open(os.path.join(LISTING, name), encoding="utf-8") as f:
            length = len(f.read().rstrip("\n"))
        if length > limit:
            raise SystemExit(f"{name} is {length} characters; Play allows {limit}.")
    path = os.path.join(LISTING, "changelogs", f"{version_code()}.txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write(play_notes() + "\n")


if __name__ == "__main__":
    main()
