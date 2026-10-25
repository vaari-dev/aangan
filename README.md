# Aangan

A calm, private home screen for Android TV and Google TV. *Aangan* (आँगन) is the courtyard at the heart of an
Indian home: the open space everyone passes through, where the day's weather is felt and festivals are celebrated.

- **The essentials, beautifully.** A clock, the weather and air quality for your city, a row of favourite apps, and
  every other app in a quiet grid. Press Up then OK on the weather for a "Today" card: the next hours, sunrise and
  sunset, UV, moon phase, a 36-hour PM2.5 trend and the best time to air out the house.
- **Festivals and family days.** A countdown to the next festival, and on the day a hand-drawn scene: diyas for
  Diwali, gulal for Holi, kites for Sankranti, a moon through the chalni for Karwa Chauth, balloons for birthdays.
- **Private by design.** No account, no ads, no analytics, no location permission. The only network use is
  [Open-Meteo](https://open-meteo.com) weather for the city you choose (two requests, at most every 30–60 minutes,
  only while the home screen is on show), city search when you ask for it, and the phone-setup page below.
- **Light.** On an ordinary day nothing animates, so Aangan uses no CPU while you watch. Festival scenes animate
  at 20 fps, and only while the home screen is in front.

## Install

1. Download `aangan.apk` from the [releases](../../releases) and install it, either with a sideloading app such as
   *Downloader*, or with `adb install aangan.apk`.
2. Open Aangan, choose **Set up Aangan**, then **Open with the Home button**, and switch on **Aangan** in the
   TV's Accessibility settings. From then on, the Home button and switching the TV on bring up Aangan.
   - The accessibility service acts on the Home button only; every other button passes straight through. It also
     notices when the TV's own home screen opens, so it can show Aangan instead. It can't see other apps or
     anything on screen.
   - With adb you can make Aangan the true default instead:
     `adb shell cmd package set-home-activity dev.vaari.aangan/.HomeActivity`.
3. Optional: choose **Screensaver** in the same settings and pick *Aangan clock*, a dim clock with the weather.

## Set up from your phone

Typing with a remote is slow, so Aangan's settings show a QR code. Scan it with a phone on the same Wi-Fi to choose
your city and add birthdays and anniversaries from the phone's browser. The page is served by the TV itself: it
runs only while the settings screen is open, its address carries a fresh one-time secret, and everything stays on
the TV. You can also do all of this with the remote.

## Build

Needs the Android SDK (build-tools 34, platform 34) and JDK 17. No Gradle.

    ./build.sh            # builds build/aangan.apk, signed with a local development key
    ./build.sh install    # and installs it over adb (set ANDROID_SERIAL when several devices are connected)

For a release, sign with your own key: `AANGAN_KEYSTORE=… AANGAN_KEY_PASS=… ./build.sh`.

To preview a festival's scene: `adb shell "am start -n dev.vaari.aangan/.HomeActivity --es festival Diwali"`;
press Home to clear it.

## The festival calendar

`assets/festivals.csv` lists the public calendar: one line per day, with its greeting and scene.

    # date,name,greeting,scene[,expected]
    2026-11-08,Diwali,Happy Diwali,DIWALI

Dates follow [Drik Panchang](https://www.drikpanchang.com/calendars/indian/indiancalendar.html) for New Delhi;
national holidays and Eid follow the Government of India's list. Scenes are the names in `Festive.Theme`, or
`PLAIN` for none. Days between two entries with the same scene, up to 9 days apart, share it, so the whole of
Diwali week and Navratri glow. Each year's dates need adding once the calendars are published.

Birthdays and anniversaries are not in the code or the repository; each TV keeps its own.

## Credits

Weather and air quality: [Open-Meteo](https://open-meteo.com) (CC BY 4.0), with air quality from Copernicus CAMS.
Air quality uses India's CPCB National AQI in India and the US EPA AQI elsewhere.
