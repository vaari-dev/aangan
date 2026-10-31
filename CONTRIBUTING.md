# Contributing

Aangan should stay small and private. For anything large, open an issue first.

## Build

JDK 17+ and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in an uncommitted `local.properties`).

    ./gradlew assembleDebug    # app/build/outputs/apk/debug/aangan-debug.apk
    ./gradlew installDebug     # over adb; set ANDROID_SERIAL if several devices are connected
    ./gradlew check            # unit tests (Robolectric), Android Lint, javac -Xlint:all; warnings are errors

Run `check` before opening a pull request. `assembleRelease bundleRelease` signs with the release key when
`AANGAN_KEYSTORE` and `AANGAN_KEY_PASS` are set, otherwise with the debug key, which can't update a real install.

Gradle verifies every download against [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml).
After changing `gradle/libs.versions.toml`, regenerate it and review the diff:

    ./gradlew --write-verification-metadata sha256 help check assembleRelease bundleRelease

## Layout

- `app/src/main/java/dev/vaari/aangan/`: the code ([source map](https://vaari-dev.github.io/aangan/developers.html#map)).
- `app/src/main/assets/`: `festivals.csv`, festival-name translations, and the phone page's stylesheet, font and logo.
- `app/src/main/res/values/strings.xml`: the app's text.
- `app/src/test/java/dev/vaari/aangan/`: unit tests.
- `site/`: the website; `python3 tools/site.py` builds it into `build/site/`.

## Rules

- Match the surrounding code. Plain Java on the Android framework.
- Comments explain why, not what.
- No libraries. The app has no runtime dependencies; propose one in an issue first.
- No work while people watch: nothing animates, polls or touches the network while another app is in front.
- No analytics, crash reporting, ads or tracking. A new network request or permission needs an issue first, and a
  [privacy policy](site/pages/privacy.html) change before the code.
- Add a unit test when you change behaviour that can be tested off a TV.

## Festivals

[`app/src/main/assets/festivals.csv`](app/src/main/assets/festivals.csv), one line per festival day:

    # date,name,greeting,scene[,expected]
    2026-11-08,Diwali,Happy Diwali,DIWALI
    2027-03-10,Eid al-Fitr,Eid Mubarak,EID,expected

- **date**: `YYYY-MM-DD`, from [Drik Panchang](https://www.drikpanchang.com/calendars/indian/indiancalendar.html)
  for New Delhi; national holidays and Eid from the Government of India's list.
- **scene**: a `Festive.Theme` name, or `PLAIN` for the greeting alone. Days between two entries with the same
  scene, up to 9 days apart, share it.
- **expected**: the date depends on a moon sighting and may move by a day.
- Families' moves and hides are keyed by the original date and name, so changing either drops them. Correct a line
  only when it's wrong.

Without editing the file, use the **Festival date** issue form. Preview a scene with **See how it looks** in Settings, or:

    adb shell am start -n dev.vaari.aangan/.HomeActivity --es festival Diwali

## Translating

1. Copy `app/src/main/res/values/strings.xml` to `values-XX/strings.xml` and translate it, keeping placeholders
   such as `%1$s`.
2. Translate festival names and greetings in `app/src/main/assets/festival-names/XX.csv`
   (`name,localised name,localised greeting`; `name` must match `festivals.csv` exactly).
3. Check it on a TV in that language: long words can overflow tiles and buttons.

The Hindi draft (`values-hi`) needs a native speaker's review.

## Testing on a TV

- Emulator: an Android TV or Google TV image in Android Studio's Device Manager. Arrow keys and Enter are the D-pad.
- Real TV: enable developer options and USB or network debugging, then `adb connect <address>`.
- Make Aangan home: `adb shell cmd package set-home-activity dev.vaari.aangan/.HomeActivity`.
- Everything must work with a D-pad alone.

## Releasing

1. Raise `versionName` and `versionCode` in [`app/build.gradle.kts`](app/build.gradle.kts) and add the version to [`CHANGELOG.md`](CHANGELOG.md).
2. Run `python3 tools/store.py` and commit the Play release notes it writes.
3. Push to `main`. After an owner approves the `release` environment, CI signs the build, checks the certificate,
   publishes the GitHub release and uploads to Play's internal track ([`store/README.md`](store/README.md)).

## Pull requests

- One change each, saying what and why.
- A [`CHANGELOG.md`](CHANGELOG.md) line for anything a family would notice.
- Screenshots for visual changes, and which TV or emulator you tested on.
- CI must pass: `check`, a reproducible-build check, emulator smoke tests, zizmor and CodeQL.
- Contributions are licensed under the [MIT licence](LICENSE).

Security problems: [`SECURITY.md`](SECURITY.md). Conduct: [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).
