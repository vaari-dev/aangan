<p align="center"><img src="docs/banner.png" alt="Aangan" width="640"></p>

<h1 align="center">Aangan</h1>

<p align="center"><b>A calm, private home screen for Android TV and Google TV.</b><br>
No ads. No tracking. No account. Just your apps, the sky outside, and the days that matter.</p>

<p align="center"><a href="../../releases/latest/download/aangan.apk"><b>Download the latest release</b></a></p>

---

*Aangan* (आँगन) is the courtyard at the heart of an Indian home: the open space everyone passes through, where you
feel the day's weather and celebrate its festivals. Aangan brings that calm to your TV. It replaces the stock home
screen, with its rows of ads and recommendations, with something quiet, beautiful and yours.

![Home screen](docs/home.jpg)

## What it does

- **Your apps, and nothing else.** A row of favourites and every other app in a tidy grid. Hold OK on any app to move it, pin it or hide it. The focused app casts a soft light in its own colour.
- **The sky outside.** The time, the weather and the air quality for your city, always in the corner.
- **Today, at a glance.** Press Up then OK on the weather: the next hours, high and low, sunrise and sunset, UV, the moon, a 36-hour air-quality trend with your country's safe limit, and the best time to air out the house.
- **Festivals.** A countdown to the next one, and on the day itself a hand-drawn scene behind your apps. If your family keeps a festival on another day, move it, or hide one you don't celebrate.
- **Your family's days.** Birthdays and anniversaries get balloons, confetti, rings and petals, with a greeting by name. Add, edit and remove them on the TV or from your phone.
- **A quiet screensaver.** A dim clock and the weather, drifting slowly so nothing burns in.

![Today card](docs/today.jpg)

## Festivals and family days

On the day, Aangan draws a scene behind your apps and plays a short opening. The scene keeps moving gently, but only
while the home screen is on screen.

| | |
|---|---|
| ![Diwali](docs/diwali.jpg) Diwali: diyas, rangoli and fireworks | ![Holi](docs/holi.jpg) Holi: clouds of gulal |
| ![Karwa Chauth](docs/karwachauth.jpg) Karwa Chauth: the moon through the chalni | ![Christmas](docs/christmas.jpg) Christmas: lights and snow |
| ![A birthday](docs/birthday.jpg) A birthday: balloons and confetti | ![Festival dates in Settings](docs/settings-festivals.jpg) Festival dates you can move or hide |

There are scenes for Lohri, Makar Sankranti, Basant Panchami, Holi, Navratri, Dussehra, Karwa Chauth, the whole of
Diwali week, Chhath, Gurpurab, Raksha Bandhan, Janmashtami, Ganesh Chaturthi, Eid, Christmas, New Year, Republic Day
and Independence Day, and for birthdays and anniversaries.

## Private by design

- No ads, analytics, crash reporting or account.
- No location permission. You choose your city; Gurgaon is the default.
- The only network use: [Open-Meteo](https://open-meteo.com) weather for your city (two requests, at most every 30–60 minutes, and only while the home screen is on screen), city search when you ask for it, and the phone-setup page below.
- Birthdays and anniversaries are stored on the TV and nowhere else.
- Light on the TV: on an ordinary day nothing animates, so Aangan uses no CPU while you watch.

## Install on any TV, without developer mode

1. On the TV, install **Downloader** (by AFTVnews) from the Google Play Store.
2. Open Downloader and enter:

   ```
   https://github.com/vaari-dev/aangan/releases/latest/download/aangan.apk
   ```

   The first time, the TV says it isn't allowed to install apps from this source. Choose **Settings**, switch Downloader on, and press Back. This is an ordinary setting, not developer mode. If you need it later, it's in **Settings → Apps → Security** (Google TV) or **Settings → Device Preferences → Security & restrictions** (older Android TV).
3. Choose **Install**. The same link always fetches the latest version, so use it again to update.
4. Open **Aangan** from your TV's apps. A short welcome takes you through the rest:

![Welcome](docs/welcome.jpg)

- **Open with Home.** Choose *Turn on*, then *Aangan*, and switch it on. From then on the Home button, and switching the TV on, bring up Aangan instead of the stock screen.
  - Android shows its standard warning for accessibility services. Aangan uses the service only to react to the Home button: every other button passes straight through, and it can't see other apps or anything on screen.
  - On Android 13 and later, if Aangan is greyed out there, open **Settings → Apps → Aangan**, choose **Allow restricted settings**, and try again.
- **Your city.** Weather and air quality default to Gurgaon. Change the city if you live elsewhere.
- **Family days.** Add birthdays and anniversaries; the easiest way is from your phone, below.

> Prefer adb? You can make Aangan the true default home instead: `adb shell cmd package set-home-activity dev.vaari.aangan/.HomeActivity`.

## Set up from your phone

Typing with a TV remote is slow, so Aangan's settings show a QR code. Scan it with your phone's camera, on the same
Wi-Fi, to choose your city, add or edit birthdays and anniversaries, and move or hide festival dates, all from the
phone's browser. The TV itself serves the page:

- it runs only while the settings screen is open;
- its address carries a fresh one-time secret;
- it stays on your home network, loads nothing from the internet (even its font comes from the TV), and everything is saved on the TV.

![Settings on the TV](docs/settings.jpg)

<p align="center">
<img src="docs/phone.png" alt="The phone page: your city and the family's days" width="300">&nbsp;&nbsp;
<img src="docs/phone-festivals.png" alt="The phone page: moving a festival date" width="300">
</p>

> **"Not secure"?** Some phone browsers warn about any page without HTTPS. A page served by a TV on your home network
> can't have an HTTPS certificate without a cloud service in the middle, which Aangan avoids on purpose. Choose to
> continue: the page and what you type never leave your Wi-Fi. Most browsers then remember the choice for the TV's address.

## Build from source

You need the Android SDK (build-tools 34, platform 34) and JDK 17. There's no Gradle.

    ./build.sh            # builds build/aangan.apk, signed with a local development key
    ./build.sh install    # and installs it over adb (set ANDROID_SERIAL when several devices are connected)

For a release, sign with your own key: `AANGAN_KEYSTORE=… AANGAN_KEY_PASS=… ./build.sh`.

The icon and banner are drawn by `python3 docs/icon.py`, which writes `res/drawable/icon.xml`, `banner.xml` and the
SVGs in `docs/`.

To preview a festival's scene: `adb shell "am start -n dev.vaari.aangan/.HomeActivity --es festival Diwali"`.
Press Home to clear it.

### The festival calendar

`assets/festivals.csv` holds the public calendar: one line per day, with its greeting and scene.

    # date,name,greeting,scene[,expected]
    2026-11-08,Diwali,Happy Diwali,DIWALI

- **Sources:** dates follow [Drik Panchang](https://www.drikpanchang.com/calendars/indian/indiancalendar.html) for New Delhi. National holidays and Eid follow the Government of India's list.
- **Scenes:** use the names in `Festive.Theme`, or `PLAIN` for none.
- **Festival weeks:** days between two entries with the same scene, up to 9 days apart, share it, so the whole of Diwali week and Navratri glow.
- **Yearly update:** add each year's dates once the calendars are published.
- **Family changes:** a date moved or hidden on the TV or the phone is stored on the TV, on top of the calendar, so an app update with new dates keeps it.

## Credits

- **Weather and air quality:** [Open-Meteo](https://open-meteo.com) (CC BY 4.0), with air quality from Copernicus CAMS.
- **Air-quality scale:** India's CPCB National AQI in India, and the US EPA AQI elsewhere.
- **Typeface:** the wordmark and the phone page use [Fraunces](https://github.com/undercasetype/Fraunces) by Undercase Type, under the SIL Open Font Licence (`assets/phone/OFL-Fraunces.txt`).

Released under the [MIT licence](LICENSE).
