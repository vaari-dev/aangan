#!/usr/bin/env python3
"""The version (from app/build.gradle.kts) and the changelog (CHANGELOG.md), for the website and for releases.

    python3 tools/release.py version      # 1.4
    python3 tools/release.py code         # 14
    python3 tools/release.py tag          # v1.4.0
    python3 tools/release.py notes [1.4]  # that version's changelog, as Markdown
"""
import html
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _gradle(key):
    with open(os.path.join(REPO_ROOT, "app", "build.gradle.kts"), encoding="utf-8") as f:
        return re.search(rf'^\s*{key} = "?([^"\n]+)"?$', f.read(), re.M).group(1)


def version():
    return _gradle("versionName")


def code():
    return int(_gradle("versionCode"))


def tag(name=None):
    """"1.3" is v1.3.0; "1.3.1" is v1.3.1."""
    name = name or version()
    return "v" + name + (".0" if name.count(".") == 1 else "")


def changelog():
    """[(version, date or "", [bullet, ...])], newest first, from headings like "## 1.1 · 25 October 2026"."""
    entries = []
    with open(os.path.join(REPO_ROOT, "CHANGELOG.md"), encoding="utf-8") as f:
        for line in f:
            heading = re.match(r"## (\S+)(?: · (.+))?$", line.strip())
            if heading:
                entries.append((heading.group(1), heading.group(2) or "", []))
            elif line.startswith("- ") and entries:
                entries[-1][2].append(line[2:].strip())
    return entries


def bullets(name=None):
    name = name or version()
    for v, _, items in changelog():
        if v == name and items:
            return items
    raise SystemExit(f"CHANGELOG.md has no notes for {name}")


def notes(name=None):
    return "\n".join("- " + b for b in bullets(name))


def inline_html(markdown):
    """Only **bold**, `code` and [links](url)."""
    s = html.escape(markdown, quote=False)
    s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"`(.+?)`", r"<code>\1</code>", s)
    return re.sub(r"\[(.+?)\]\((.+?)\)", r'<a href="\2">\1</a>', s)


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "version":
        print(version())
    elif command == "code":
        print(code())
    elif command == "tag":
        print(tag())
    elif command == "notes":
        print(notes(sys.argv[2] if len(sys.argv) > 2 else None))
    else:
        raise SystemExit(__doc__)
