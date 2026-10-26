#!/usr/bin/env python3
"""Builds Aangan's website into build/site/, which CI publishes to GitHub Pages. From the repository root:

    python3 tools/site.py

Pages live in site/pages/<name>.html, each starting with a JSON comment giving its title and
description; this adds the shared head, navigation and footer. The festival calendar comes from
app/src/main/assets/festivals.csv, scene colours from Festive.Theme, the changelog from CHANGELOG.md, and the
font and pictures from where the app and README keep them. The site has no scripts, cookies or
analytics, and loads nothing from anywhere else.
"""
import csv
import datetime
import html
import json
import os
import re
import shutil
import subprocess

import release

ROOT = release.REPO_ROOT
OUT = os.path.join(ROOT, "build", "site")
SITE = "https://vaari-dev.github.io/aangan/"
REPO = "https://github.com/vaari-dev/aangan"

NAV = [("index", "Home"), ("guide", "Guide"), ("privacy", "Privacy"), ("festivals", "Festivals"),
       ("faq", "FAQ"), ("developers", "Developers")]

# What each scene draws; its colours come from Festive.Theme.
SCENES = {
    "DIWALI": ("Diwali", "Clay diyas lighting one by one, rangoli and fireworks"),
    "TORAN": ("Toran", "Marigold garlands with mango leaves"),
    "HOLI": ("Holi", "Clouds of gulal thrown from the edges"),
    "BONFIRE": ("Bonfire", "A fire with sparks rising"),
    "KITES": ("Kites", "Patangs on long strings"),
    "BASANT": ("Basant", "Kites over a mustard-yellow glow"),
    "KARWA": ("Karwa Chauth", "The moon through a decorated chalni"),
    "MOON": ("Full moon", "A full moon rising among stars"),
    "CRESCENT": ("Crescent", "A thin crescent among stars"),
    "PEACOCK": ("Peacock", "A peacock feather and a bansuri"),
    "RAKHI": ("Rakhi", "A red-and-gold thread with a flower"),
    "SUNSET": ("Sunset", "The sun setting over rippling water"),
    "DEEPMALA": ("Deepmala", "Strings of lights and rows of diyas"),
    "CHRISTMAS": ("Christmas", "Coloured lights and falling snow"),
    "FIREWORKS": ("Fireworks", "Bursts of light across the sky"),
    "EID": ("Eid", "Swinging fanoos lanterns, a crescent and a star"),
    "TRICOLOUR": ("Tricolour", "Saffron, white and green ribbons and the chakra"),
    "BIRTHDAY": ("Birthday", "Balloons and confetti"),
    "ANNIVERSARY": ("Anniversary", "Two gold rings, hearts and rose petals"),
}


def path(name):
    """A page's path on the site: "" for the home page, "faq.html" for the rest."""
    return "" if name == "index" else name + ".html"


def href(name):
    return path(name) or "./"


def read(*path):
    with open(os.path.join(ROOT, *path), encoding="utf-8") as f:
        return f.read()


def theme_colours():
    """{"DIWALI": ("#7A5A2E", "#FFC56B"), ...}: each Festive.Theme's glow and accent."""
    java = read("app", "src", "main", "java", "dev", "vaari", "aangan", "Festive.java")
    enum = re.search(r"enum Theme \{(.*?);", java, re.S).group(1)
    return {name: ("#" + glow, "#" + accent)
            for name, glow, accent in re.findall(r"(\w+)\(0xFF(\w{6}), 0xFF(\w{6})\)", enum)}


def festivals():
    for row in csv.reader(line for line in read("app", "src", "main", "assets", "festivals.csv").splitlines()
                          if line.strip() and not line.startswith("#")):
        row = [x.strip() for x in row]
        yield datetime.date.fromisoformat(row[0]), row[1], row[2], row[3], row[4:] == ["expected"]


def calendar():
    out, year, month = [], None, None
    for date, name, greeting, scene, expected in festivals():
        if (date.year, date.month) != (year, month):
            if month is not None:
                out.append("</ul></div>")
            if date.year != year:
                out.append(f"<h3>{date.year}</h3>")
            out.append(f"<div class=month><b>{date.strftime('%B')}</b><ul>")
            year, month = date.year, date.month
        what = SCENES[scene][1] if scene in SCENES else "The greeting, no scene"
        note = " · date depends on the moon" if expected else ""
        out.append(f"<li><span class=d>{date.day}<small>{date.strftime('%a')}</small></span>"
                   f"<span class=n>{html.escape(name)}<span class=s>“{html.escape(greeting)}” · {what}{note}</span></span></li>")
    return "<div class=cal>" + "".join(out) + "</ul></div></div>"


def scenes():
    colours = theme_colours()
    return "<div class=scenes>" + "".join(
        f"<div class=scene style='background:radial-gradient(120% 90% at 20% 0%, {colours[key][1]}55, transparent 60%),"
        f"linear-gradient(160deg, {colours[key][0]}, #120d0a)'><b style='color:{colours[key][1]}'>{name}</b><span>{what}</span></div>"
        for key, (name, what) in SCENES.items()) + "</div>"


def changelog():
    entries = release.changelog()
    toc = "".join(f'<a href="#v{v.replace(".", "-")}">{v}</a>' for v, _, _ in entries)
    body = "".join(
        f'<h2 id=v{v.replace(".", "-")}>{v}' + (f' <span class=muted style="font-size:18px">· {date}</span>' if date else "")
        + "</h2><ul>" + "".join(f"<li>{release.inline_html(b)}</li>" for b in bullets) + "</ul>"
        for v, date, bullets in entries)
    return f"""<!-- {{"title": "Changelog", "description": "What's new in each version of Aangan, the free, private launcher for Android TV and Google TV."}} -->
<div class="wrap page-head">
  <p class=eyebrow>Changelog</p>
  <h1>What's new.</h1>
  <p class=lede>Every release is on <a href="{REPO}/releases">GitHub Releases</a>. Update by installing over the old version; your settings stay.</p>
</div>
<div class="wrap doc"><nav class=toc aria-label="Versions"><b>Versions</b>{toc}</nav><div class=prose>{body}</div></div>
"""


def faq_items(body):
    """Questions and answers from the FAQ page's details blocks, as plain text."""
    return [{"@type": "Question", "name": html.unescape(q),
             "acceptedAnswer": {"@type": "Answer",
                                "text": re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", a))).strip()}}
            for q, a in re.findall(r"<summary>(.*?)</summary><div>(.*?)</div></details>", body, re.S)]


def structured(name, meta, body, url):
    """Schema.org data for search engines: the app on the home page, questions on the FAQ, a trail elsewhere."""
    graph = [{"@type": "Organization", "@id": SITE + "#org", "name": "Vaari", "url": "https://github.com/vaari-dev",
              "logo": SITE + "icon.png"},
             {"@type": "WebSite", "@id": SITE + "#site", "name": "Aangan", "url": SITE, "inLanguage": "en-IN",
              "publisher": {"@id": SITE + "#org"}}]
    if name == "index":
        graph.append({
            "@type": "SoftwareApplication", "@id": SITE + "#app", "name": "Aangan",
            "alternateName": ["Aangan TV launcher", "Aangan launcher for Android TV and Google TV"],
            "description": meta["description"],
            "applicationCategory": "UtilitiesApplication", "applicationSubCategory": "Launcher",
            "operatingSystem": "Android TV 9+, Google TV", "softwareVersion": release.version(),
            "license": "https://opensource.org/licenses/MIT", "isAccessibleForFree": True,
            "offers": {"@type": "Offer", "price": "0", "priceCurrency": "INR"},
            "downloadUrl": REPO + "/releases/latest/download/aangan.apk", "installUrl": SITE + "install.html",
            "codeRepository": REPO, "inLanguage": "en",
            "screenshot": [SITE + f for f in ("home.jpg", "today.jpg", "diwali.jpg", "settings.jpg")],
            "image": SITE + "social.png", "author": {"@id": SITE + "#org"}, "publisher": {"@id": SITE + "#org"},
            # No aggregateRating or review: Google's software-app rich result needs one, and there are
            # no genuine ratings to give. The markup still describes the app to search engines.
            "featureList": ["No ads, tracking or account", "A dock of four apps you choose",
                            "Weather and air quality (CPCB AQI in India, US AQI elsewhere)",
                            "City search that knows older names such as Bombay and Gurgaon",
                            "Indian festival countdowns and animated scenes", "Birthdays and anniversaries",
                            "Set up from your phone by QR code", "Opens with the Home button without adb",
                            "Clock screensaver", "Crash notes kept on the TV, never sent"],
        })
    else:
        graph.append({"@type": "BreadcrumbList", "itemListElement": [
            {"@type": "ListItem", "position": 1, "name": "Aangan", "item": SITE},
            {"@type": "ListItem", "position": 2, "name": meta["title"], "item": url}]})
    if name == "faq":
        graph.append({"@type": "FAQPage", "mainEntity": faq_items(body)})
    return json.dumps({"@context": "https://schema.org", "@graph": graph}, ensure_ascii=False, separators=(",", ":"))


def page(name, meta, body):
    title = meta["title"] if name == "index" else meta["title"] + " · Aangan"
    desc = html.escape(meta["description"], quote=True)
    url = SITE + path(name)
    nav = "".join(f"<a href='{href(key)}'{' aria-current=page' if key == name else ''}>{label}</a>"
                  for key, label in NAV) + "<a class=cta href='install.html'>Install</a>"
    robots = "noindex" if meta.get("noindex") else "index, follow, max-image-preview:large, max-snippet:-1"
    ld = "" if meta.get("noindex") else f'<script type="application/ld+json">{structured(name, meta, body, url)}</script>\n'
    out = f"""<!doctype html>
<html lang=en>
<head>
<meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta http-equiv=Content-Security-Policy content="default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'">
<meta name=referrer content=no-referrer>
<title>{html.escape(title)}</title>
<meta name=description content="{desc}">
<meta name=robots content="{robots}">
<link rel=canonical href="{url}">
<link rel=icon href="icon.svg" type="image/svg+xml">
<link rel=apple-touch-icon href="icon.png">
<link rel=sitemap type="application/xml" href="sitemap.xml">
<link rel=stylesheet href="assets/site.css">
<meta name=theme-color content="#f5eee2" media="(prefers-color-scheme: light)">
<meta name=theme-color content="#14100d" media="(prefers-color-scheme: dark)">
<meta property=og:type content=website>
<meta property=og:site_name content=Aangan>
<meta property=og:locale content=en_IN>
<meta property=og:title content="{html.escape(title, quote=True)}">
<meta property=og:description content="{desc}">
<meta property=og:url content="{url}">
<meta property=og:image content="{SITE}social.png">
<meta property=og:image:width content=1280>
<meta property=og:image:height content=640>
<meta property=og:image:alt content="Aangan: a calm, private home screen for Android TV and Google TV">
<meta name=twitter:card content=summary_large_image>
<meta name=application-name content=Aangan>
{ld}</head>
<body>
<a class=skip href="#main">Skip to content</a>
<header class=mast><div class=wrap>
<a class=brand href="./"><img src="icon.svg" alt="" width=32 height=32>Aangan</a>
<nav class=nav aria-label=Site>{nav}</nav>
<details class=menu><summary>Menu</summary><nav class=nav aria-label=Site>{nav}</nav></details>
</div></header>
<main id=main>
{body}
</main>
<footer class=foot><div class=wrap>
<div><a class=brand href="./"><img src="icon.svg" alt="" width=32 height=32>Aangan</a>
<p>A calm, private home screen for Android TV and Google TV. Free and open source, made in Gurgaon.</p>
<p>This website has no scripts, cookies or analytics, and loads nothing from anywhere else.</p></div>
<div><b>Use</b><a href="install.html">Install</a><a href="guide.html">Guide</a><a href="festivals.html">Festival calendar</a><a href="faq.html">FAQ</a></div>
<div><b>Trust</b><a href="privacy.html">Privacy policy</a><a href="terms.html">Terms of use</a><a href="developers.html#security">Security model</a><a href="privacy.html#network">What leaves your TV</a></div>
<div><b>Project</b><a href="{REPO}">Source code</a><a href="{REPO}/releases">Releases</a><a href="changelog.html">Changelog</a><a href="{REPO}/issues">Report a problem</a></div>
<div class=base>Released under the MIT licence. Weather and air quality by Open-Meteo (CC BY 4.0) and Copernicus CAMS. Android TV and Google TV are trademarks of Google LLC; other apps' names and logos in screenshots belong to their owners.</div>
</div></footer>
</body>
</html>
"""
    if name == "404":
        # GitHub serves this page at any missing path, so its links must be absolute.
        out = re.sub(r"""(href|src)=(["'])(?!https?:|#|/)""", r"\1=\2/aangan/", out).replace("/aangan/./", "/aangan/")
    return out


def lastmod(name):
    """When the page's sources last changed, from git, or None in a shallow clone, where every file
    would get the newest commit's date. Google uses lastmod only while it's accurate."""
    sources = {"changelog": ["CHANGELOG.md"], "festivals": ["site/pages/festivals.html", "assets/festivals.csv"]}
    files = sources.get(name, [f"site/pages/{name}.html"])
    try:
        if subprocess.run(["git", "rev-parse", "--is-shallow-repository"], cwd=ROOT, capture_output=True,
                          text=True, check=True).stdout.strip() != "false":
            return None
        date = subprocess.run(["git", "log", "-1", "--format=%cs", "--", *files], cwd=ROOT, capture_output=True,
                              text=True, check=True).stdout.strip()
        return date or None
    except (OSError, subprocess.CalledProcessError):
        return None


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(os.path.join(OUT, "assets"))
    # Pictures shared with the README, the font shared with the phone page.
    for f in os.listdir(os.path.join(ROOT, "docs")):
        shutil.copy(os.path.join(ROOT, "docs", f), OUT)
    shutil.copy(os.path.join(ROOT, "site", "site.css"), os.path.join(OUT, "assets"))
    shutil.copy(os.path.join(ROOT, "app", "src", "main", "assets", "phone", "fraunces.woff2"), os.path.join(OUT, "assets"))

    sources = {f[:-5]: read("site", "pages", f) for f in os.listdir(os.path.join(ROOT, "site", "pages")) if f.endswith(".html")}
    sources["changelog"] = changelog()
    indexed = []
    for name, text in sorted(sources.items()):
        m = re.match(r"\s*<!--\s*(\{.*?\})\s*-->\s*", text, re.S)
        meta = json.loads(m.group(1))
        body = (text[m.end():].replace("{{CALENDAR}}", calendar()).replace("{{SCENES}}", scenes())
                .replace("{{REPO}}", REPO))
        with open(os.path.join(OUT, name + ".html"), "w", encoding="utf-8") as out:
            out.write(page(name, meta, body))
        if not meta.get("noindex"):
            indexed.append((SITE + path(name), lastmod(name)))

    with open(os.path.join(OUT, "sitemap.xml"), "w", encoding="utf-8") as out:
        out.write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
                  + "".join(f"<url><loc>{u}</loc>" + (f"<lastmod>{d}</lastmod>" if d else "") + "</url>\n"
                          for u, d in indexed) + "</urlset>\n")
    with open(os.path.join(OUT, "robots.txt"), "w", encoding="utf-8") as out:
        out.write(f"User-agent: *\nAllow: /\n\nSitemap: {SITE}sitemap.xml\n")
    print(f"built {len(sources)} pages into build/site/")


if __name__ == "__main__":
    main()
