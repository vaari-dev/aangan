# Google Play store listing

The listing itself is in [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/), the layout
F-Droid and Play tools read. Copy each field into Play Console → Grow users → Store presence → Main store
listing. `tools/store.py`, run by CI, checks each field against Play's limits.

## Rules this listing follows

Checked against Google's Play Console Help on 25 October 2026:

- **Limits.** App name 30 characters, short description 80, full description 4,000.
- **App name, icon and developer name** may not suggest store performance or ranking ("#1", "Top",
  "Best of Play", "Popular"), price or promotions ("Free", "No ads", "10% off"), or Play programmes
  ("New"); no emoji, emoticons or repeated special characters, and no ALL CAPS unless it's the brand.
  So "ad-free" stays out of the name and appears only in the descriptions.
- **Descriptions** must describe the app accurately, in plain language, not as a list of keywords: no
  repetitive or unrelated keywords, no unattributed testimonials, no comparisons with other apps, and
  the full description shouldn't repeat the short one.
- **"Android TV"** may not be part of an app's name without Google's approval (Android brand
  guidelines), so the name says "TV launcher". The descriptions use "for Android TV and Google TV" to
  say what it runs on, with the trademark attribution at the end.
- **Search.** The name carries the most weight, the short description appears in search results, and
  the full description is indexed too. "Launcher" and "TV launcher" are what people search for, so the
  name holds both, and each other term (home screen, weather, air quality, AQI) appears naturally in
  context rather than repeated.

## Fields

| Field | File | Limit |
|---|---|---|
| App name | `title.txt` | 30 |
| Short description | `short_description.txt` | 80 |
| Full description | `full_description.txt` | 4,000 |
| Release notes | `changelogs/<versionCode>.txt`, written from `CHANGELOG.md` by `tools/store.py` and sent with each Play upload | 500 |

If a weather search term matters more than "private", `Aangan: TV Launcher & Weather` (29) also fits the name.

## Category and contact

| Field | Value |
|---|---|
| App or game | App |
| Category | Personalization (the category other TV launchers use) |
| Tags | Up to five, chosen from Play's own list in *Grow users → Store presence → Store settings → Manage tags*. Pick **Launcher** first; then only tags a stranger would see apply, such as weather or personalisation ones if the list offers them. Change them only when the app changes a lot. |
| Website | https://vaari-dev.github.io/aangan/ |
| Email | A role address that someone reads, such as `aangan@` on a domain you control. It's shown publicly and Play requires it. Avoid a personal address. |
| Phone | Optional; leave it empty. |
| Privacy policy | https://vaari-dev.github.io/aangan/privacy.html |

## Graphics (in `fastlane/metadata/android/en-US/images/`)

| Asset | File |
|---|---|
| App icon, 512 × 512 | `icon.png` |
| Feature graphic, 1024 × 500 | `featureGraphic.png` |
| Android TV banner, 1280 × 720 | `tvBanner.png` |
| Android TV screenshots, 1920 × 1080 | `tvScreenshots/1-home.png` … `8-welcome.png`: home, Today card, Diwali, a birthday, Karwa Chauth, city search, Settings, welcome. Taken on Google TV 16 (Android 16), with only Google's own apps installed. |

All screenshots use sample names (Asha, Kabir, Meera, Dadi), not real people.
