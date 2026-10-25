#!/usr/bin/env python3
"""Builds Aangan's website into docs/, which GitHub Pages serves. Run from the repository root:

    python3 docs/site.py

Each page's content lives in docs/site/<name>.html, starting with a JSON comment that gives its
title and description. This adds the shared head, navigation and footer, and fills in the festival
calendar from assets/festivals.csv. The site has no scripts, cookies or analytics, and loads
nothing from anywhere else.
"""
import csv
import datetime
import html
import json
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))
SITE = "https://vaari-dev.github.io/aangan/"
REPO = "https://github.com/vaari-dev/aangan"

TODAY = datetime.date.today().isoformat()
NAV = [("index", "Home"), ("guide", "Guide"), ("privacy", "Privacy"), ("festivals", "Festivals"),
       ("faq", "FAQ"), ("developers", "Developers")]

# What each scene draws, and its colours (the glow and accent in Festive.Theme).
SCENES = {
    "DIWALI": ("Diwali", "Clay diyas lighting one by one, rangoli and fireworks", "#7A5A2E", "#FFC56B"),
    "TORAN": ("Toran", "Marigold garlands with mango leaves", "#7A4A1E", "#FFB347"),
    "HOLI": ("Holi", "Clouds of gulal thrown from the edges", "#6A2A5E", "#FF7AC0"),
    "BONFIRE": ("Bonfire", "A fire with sparks rising", "#7A3A1A", "#FFA04D"),
    "KITES": ("Kites", "Patangs on long strings", "#2E5A7A", "#8AD8FF"),
    "BASANT": ("Basant", "Kites over a mustard-yellow glow", "#6E6420", "#FFE066"),
    "KARWA": ("Karwa Chauth", "The moon through a decorated chalni", "#4A3466", "#F2C46B"),
    "MOON": ("Full moon", "A full moon rising among stars", "#3A3F66", "#F2E6C2"),
    "CRESCENT": ("Crescent", "A thin crescent among stars", "#26305E", "#BFCBFF"),
    "PEACOCK": ("Peacock", "A peacock feather and a bansuri", "#1D4A5E", "#63D8C8"),
    "RAKHI": ("Rakhi", "A red-and-gold thread with a flower", "#6A2A3A", "#FF94A8"),
    "SUNSET": ("Sunset", "The sun setting over rippling water", "#7A3A22", "#FFA86B"),
    "DEEPMALA": ("Deepmala", "Strings of lights and rows of diyas", "#6A5A36", "#FFE2A6"),
    "CHRISTMAS": ("Christmas", "Coloured lights and falling snow", "#2A4A36", "#FF7B72"),
    "FIREWORKS": ("Fireworks", "Bursts of light across the sky", "#3E2A66", "#DDBDFF"),
    "EID": ("Eid", "Swinging fanoos lanterns, a crescent and a star", "#1E4A40", "#86E3BF"),
    "TRICOLOUR": ("Tricolour", "Saffron, white and green ribbons and the chakra", "#5A4A36", "#FFB066"),
    "BIRTHDAY": ("Birthday", "Balloons and confetti", "#5A2E66", "#FFA3CF"),
    "ANNIVERSARY": ("Anniversary", "Two gold rings, hearts and rose petals", "#6A2A40", "#FFB3C1"),
}


def festivals():
    rows = []
    with open(os.path.join(ROOT, "..", "assets", "festivals.csv"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            fields = [x.strip() for x in next(csv.reader([line]))]
            if len(fields) >= 4:
                rows.append((datetime.date.fromisoformat(fields[0]), fields[1], fields[2], fields[3],
                             len(fields) > 4 and fields[4] == "expected"))
    return rows


def calendar():
    out, year, month = [], None, None
    for date, name, greeting, scene, expected in festivals():
        if date.year != year:
            if month is not None:
                out.append("</ul></div>")
            out.append(f"<h3>{date.year}</h3>")
            year, month = date.year, None
        if date.month != month:
            if month is not None:
                out.append("</ul></div>")
            out.append(f"<div class=month><b>{date.strftime('%B')}</b><ul>")
            month = date.month
        what = SCENES.get(scene, ("", "No scene, just the greeting", "", ""))[1] if scene != "PLAIN" else "The greeting, no scene"
        note = " · date depends on the moon" if expected else ""
        out.append(f"<li><span class=d>{date.day}<small>{date.strftime('%a')}</small></span>"
                   f"<span class=n>{html.escape(name)}<span class=s>“{html.escape(greeting)}” · {html.escape(what)}{note}</span></span></li>")
    out.append("</ul></div>")
    return "<div class=cal>" + "".join(out) + "</div>"


def scenes():
    cards = []
    for key, (name, what, glow, accent) in SCENES.items():
        cards.append(f"<div class=scene style='background:radial-gradient(120% 90% at 20% 0%, {accent}55, transparent 60%),"
                     f"linear-gradient(160deg, {glow}, #120d0a)'><b style='color:{accent}'>{name}</b><span>{what}</span></div>")
    return "<div class=scenes>" + "".join(cards) + "</div>"


def faq_items(body):
    """Questions and answers from the FAQ page's details blocks, as plain text."""
    items = []
    for q, a in re.findall(r"<summary>(.*?)</summary><div>(.*?)</div></details>", body, re.S):
        text = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", a))).strip()
        items.append({"@type": "Question", "name": html.unescape(q),
                      "acceptedAnswer": {"@type": "Answer", "text": text}})
    return items


def structured(name, meta, body, url):
    """Schema.org data for search engines: the app on the home page, questions on the FAQ, a trail elsewhere."""
    org = {"@type": "Organization", "@id": SITE + "#org", "name": "Vaari", "url": "https://github.com/vaari-dev",
           "logo": SITE + "icon.png"}
    graph = [org, {"@type": "WebSite", "@id": SITE + "#site", "name": "Aangan", "url": SITE, "inLanguage": "en-IN",
                   "publisher": {"@id": SITE + "#org"}}]
    if name == "index":
        graph.append({
            "@type": "SoftwareApplication", "@id": SITE + "#app", "name": "Aangan",
            "alternateName": "Aangan TV launcher",
            "description": meta["description"],
            "applicationCategory": "UtilitiesApplication", "applicationSubCategory": "Launcher",
            "operatingSystem": "Android TV 9+, Google TV",
            "softwareVersion": "1.1", "datePublished": "2026-10-25", "license": "https://opensource.org/licenses/MIT",
            "isAccessibleForFree": True, "offers": {"@type": "Offer", "price": "0", "priceCurrency": "INR"},
            "downloadUrl": REPO + "/releases/latest/download/aangan.apk", "installUrl": SITE + "install.html",
            "codeRepository": REPO, "fileSize": "152KB", "inLanguage": "en",
            "screenshot": [SITE + f for f in ("home.jpg", "today.jpg", "diwali.jpg", "settings.jpg")],
            "image": SITE + "social.png", "author": {"@id": SITE + "#org"}, "publisher": {"@id": SITE + "#org"},
            "featureList": ["No ads, tracking or account", "Weather and air quality (CPCB AQI, US AQI)",
                            "Indian festival countdowns and animated scenes", "Birthdays and anniversaries",
                            "Arrange, pin and hide apps", "Set up from your phone by QR code",
                            "Opens with the Home button without adb", "Screensaver"],
        })
    else:
        graph.append({"@type": "BreadcrumbList", "itemListElement": [
            {"@type": "ListItem", "position": 1, "name": "Aangan", "item": SITE},
            {"@type": "ListItem", "position": 2, "name": meta.get("crumb", meta["title"]), "item": url}]})
    if name == "faq":
        graph.append({"@type": "FAQPage", "mainEntity": faq_items(body)})
    return json.dumps({"@context": "https://schema.org", "@graph": graph}, ensure_ascii=False, separators=(",", ":"))


def page(name, meta, body):
    title = meta["title"] if name == "index" else meta["title"] + " · Aangan"
    desc = html.escape(meta["description"], quote=True)
    nav = "".join(
        f"<a href='{'./' if key == 'index' else key + '.html'}'{' aria-current=page' if key == name else ''}>{label}</a>"
        for key, label in NAV) + "<a class=cta href='install.html'>Install</a>"
    url = SITE + ("" if name == "index" else name + ".html")
    root = "/aangan/" if name == "404" else ""
    robots = "noindex" if meta.get("noindex") else "index, follow, max-image-preview:large, max-snippet:-1"
    ld = "" if meta.get("noindex") else f'<script type="application/ld+json">{structured(name, meta, body, url)}</script>\n'
    head = f"""<!doctype html>
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
    if root:
        head = re.sub(r'(href|src)="(?!https?:|#|/)([^"]*)"', lambda m: f'{m.group(1)}="{root}{m.group(2)}"', head)
        head = re.sub(r"(href|src)='(?!https?:|#|/)([^']*)'", lambda m: f"{m.group(1)}='{root}{m.group(2)}'", head)
    return head


def main():
    src = os.path.join(ROOT, "site")
    pages = []
    for file in sorted(os.listdir(src)):
        if not file.endswith(".html"):
            continue
        name = file[:-5]
        text = open(os.path.join(src, file), encoding="utf-8").read()
        m = re.match(r"\s*<!--\s*(\{.*?\})\s*-->\s*", text, re.S)
        meta, body = json.loads(m.group(1)), text[m.end():]
        body = body.replace("{{CALENDAR}}", calendar()).replace("{{SCENES}}", scenes())
        body = body.replace("{{REPO}}", REPO).replace("{{ROOT}}", "/aangan/")
        with open(os.path.join(ROOT, file), "w", encoding="utf-8") as out:
            out.write(page(name, meta, body))
        print("wrote docs/" + file)
        if not meta.get("noindex"):
            pages.append((name, meta.get("priority", "0.8" if name != "index" else "1.0")))
    # The sitemap and robots.txt, for search engines.
    urls = "".join(f"<url><loc>{SITE}{'' if n == 'index' else n + '.html'}</loc><lastmod>{TODAY}</lastmod>"
                   f"<priority>{p}</priority></url>\n" for n, p in pages)
    with open(os.path.join(ROOT, "sitemap.xml"), "w", encoding="utf-8") as out:
        out.write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
                  + urls + "</urlset>\n")
    with open(os.path.join(ROOT, "robots.txt"), "w", encoding="utf-8") as out:
        out.write(f"User-agent: *\nAllow: /\n\nSitemap: {SITE}sitemap.xml\n")
    open(os.path.join(ROOT, ".nojekyll"), "w").close()
    print("wrote docs/sitemap.xml, docs/robots.txt")


if __name__ == "__main__":
    main()
