# Contributing to Aangan

Thank you for helping. Aangan is a small, private home screen for Android TV and Google TV, and it should stay
small and private. This page explains how to build it, test it, and send a change.

For anything large, open an issue first, so we can agree on the shape before you write the code.

## Setting up

You need:

- **JDK 17** or later.
- **The Android SDK.** Point Gradle at it with `ANDROID_HOME`, or with `sdk.dir=/path/to/sdk` in a
  `local.properties` file at the top of the repository (don't commit it).

Then:

    ./gradlew assembleDebug    # app/build/outputs/apk/debug/aangan-debug.apk
    ./gradlew installDebug     # install it over adb (set ANDROID_SERIAL when several devices are connected)
    ./gradlew check            # unit tests, Android Lint and javac warnings

`check` runs the unit tests (Robolectric, in `app/src/test/java/dev/vaari/aangan/`), Android Lint, and
`javac -Xlint:all`. Every warning is an error. Please run it before you open a pull request.

`./gradlew assembleRelease bundleRelease` builds a release APK and bundle, shrunk with R8. Without the release key
they're signed with your local debug key, which is fine for trying them but can't update an install from GitHub or
Google Play.

Gradle checks every file it downloads (the Android Gradle plugin, the test libraries and their own dependencies)
against the checksums in [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml), so a tampered
download fails the build. After changing a version in `gradle/libs.versions.toml`, update the checksums, and look over
the diff before committing:

    ./gradlew --write-verification-metadata sha256 help check assembleRelease bundleRelease

## Where things are

- `app/src/main/java/dev/vaari/aangan/`: all the code. The [source map](https://vaari-dev.github.io/aangan/developers.html#map)
  says what each file does.
- `app/src/main/assets/festivals.csv`: the festival calendar.
- `app/src/main/assets/phone/`: the phone page's stylesheet, font and logo.
- `app/src/main/res/values/strings.xml`: the app's text.
- `app/src/test/java/dev/vaari/aangan/`: the unit tests.
- `site/`: the website. `python3 tools/site.py` builds it into `build/site/`.

## Code style

- **Match the surrounding code**: its names, layout and idiom. Aangan is plain Java on the Android framework.
- **Comments explain why**, not what. Every file starts with a comment saying what it is and why it exists.
- **No libraries.** The app has no runtime dependencies at all, and that's deliberate: it keeps it small, easy to
  read, and easy to trust. If you think one is needed, open an issue to discuss it first.
- **No work while people watch.** Nothing should animate, poll or touch the network while another app is in front.
- Add or update a unit test when you change behaviour that can be tested off a TV.

## Privacy

Aangan's promise is that nothing leaves the TV except the forecast request for the city the family chose. So:

- No analytics, crash reporting, advertising or tracking of any kind.
- No new network requests and no new permissions without discussing them in an issue first. A new kind of request
  means a change to the [privacy policy](site/pages/privacy.html), made before the code.

## Festivals

The calendar is [`app/src/main/assets/festivals.csv`](app/src/main/assets/festivals.csv), one line per festival day:

    # date,name,greeting,scene[,expected]
    2026-11-08,Diwali,Happy Diwali,DIWALI
    2027-03-10,Eid al-Fitr,Eid Mubarak,EID,expected

- **date** is `YYYY-MM-DD`. Dates follow [Drik Panchang](https://www.drikpanchang.com/calendars/indian/indiancalendar.html)
  for New Delhi; national holidays and Eid follow the Government of India's list.
- **scene** is a name from `Festive.Theme`, or `PLAIN` for the greeting alone.
- **expected** marks a date that depends on a moon sighting and may move by a day.
- Changing a line's date or name drops any family's move or hide for that festival, because those are keyed by the
  original date and name. Correct a line only when it's wrong.

The [developer guide](https://vaari-dev.github.io/aangan/developers.html#calendar) has the full format. To suggest a
date without editing the file, use the **Festival date** issue form and include your source.

To preview a festival's scene on a connected TV or emulator:

    adb shell am start -n dev.vaari.aangan/.HomeActivity --es festival Diwali

Press Home to clear it.

## Translating

Aangan's text is in [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml).

1. Copy it to `app/src/main/res/values-XX/strings.xml`, where `XX` is the language code (`hi` for Hindi, `ta` for
   Tamil, and so on), and translate each string. Keep any placeholders, such as `%1$s`, as they are.
2. Festival names and greetings are translated separately, in `app/src/main/assets/festival-names/XX.csv`, with the
   columns `name,localised name,localised greeting`. `name` is the English name exactly as it appears in
   `festivals.csv`, which is how each line is matched.
3. Check it on a TV or emulator with the system language set to yours: long words can overflow tiles and buttons.

There's a first draft of Hindi (`values-hi`) that still needs a native speaker's review. Corrections are very
welcome.

## Testing on a TV or emulator

- **Emulator:** in Android Studio's Device Manager, create an Android TV or Google TV device with a TV system
  image. Use the arrow keys and Enter as the D-pad.
- **A real TV:** turn on developer options and USB or network debugging, then `adb connect <the TV's address>`.
- Install with `./gradlew installDebug`, then make Aangan the home screen:

      adb shell cmd package set-home-activity dev.vaari.aangan/.HomeActivity

- Everything must work with a D-pad alone: check focus moves sensibly, and nothing needs a touchscreen.
- Say which TV or emulator you tested on in the pull request. Real TVs differ, so a real model is especially useful.

## Pull requests

- Keep each pull request to one change, and explain what it does and why.
- Add a line to [`CHANGELOG.md`](CHANGELOG.md) for anything a family would notice, in the same plain style.
- Include screenshots for anything that changes how a screen looks.
- CI must pass. It runs `./gradlew check`, builds the app, checks the build is reproducible, runs a smoke test on
  Android TV 13, Google TV 14 and Google TV 16 emulators, audits the workflows with zizmor, and runs CodeQL.
- By contributing, you agree to license your work under the [MIT licence](LICENSE).

Security problems shouldn't go in a public issue: see [`SECURITY.md`](SECURITY.md). Everyone taking part is
expected to follow the [code of conduct](CODE_OF_CONDUCT.md).
