# Releasing Aangan on Google Play

Everything needed for the Play Console, in order. The listing text is in [`listing.md`](listing.md), and the
graphics are in [`graphics/`](graphics/).

## 1. Account

- Create a developer account at https://play.google.com/console ($25 once, plus identity verification).
- **Organisation or personal?** A *personal* account created after November 2023 must run a closed test
  with at least 12 testers for 14 days before it can publish to production. An *organisation* account
  ("Vaari") skips that, but needs a D-U-N-S number (free, takes about one to two weeks).
- Play requires a public support email on the listing. Use a role address rather than a personal one.

## 2. Create the app

- Name `Aangan: Private TV Launcher`, default language English (India) – en-IN, App, Free.
- Package name: `dev.vaari.aangan`, fixed by the first upload.

## 3. Signing: keep the existing key

Phones and TVs that already have Aangan from GitHub must accept Play's updates, so Play has to sign
with **our existing key**, not a new one Google generates.

1. In *Test and release → Setup → App signing*, choose **Use a different key → Export and upload a key
   from Java keystore**.
2. Download Google's `pepk.jar` and encryption key from that page, then run:

       java -jar pepk.jar --keystore=$HOME/.android-keys/aangan-release.jks --alias=aangan \
           --output=aangan-signing-key.zip --include-cert --rsa-aes-encryption \
           --encryption-key-path=encryption_public_key.pem

3. Upload `aangan-signing-key.zip`, then delete it.
4. **Upload key:** the same key can sign uploads. That's simplest, but a separate upload key is safer,
   because Google can reset a lost upload key while the app-signing key stays put.

Then check that the SHA-256 Play shows for the app-signing key is
`7F:90:5B:6D:3C:FB:CC:92:CD:2D:A7:20:3C:5B:6C:F3:65:B6:5D:67:CB:3D:16:2A:4C:54:D4:04:3C:AA:14:84`.

## 4. Build the bundle

    AANGAN_KEYSTORE=$HOME/.android-keys/aangan-release.jks \
    AANGAN_KEY_PASS="$(security find-generic-password -s aangan-release-key -w)" ./build.sh bundle

This writes `build/aangan.aab`. Upload it to *Internal testing* first, install it on a TV from Play, and
check it there before promoting it.

## 5. App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | https://vaari-dev.github.io/aangan/privacy.html |
| App access | All functionality is available without special access |
| Ads | No, the app doesn't contain ads |
| Content rating | Run the IARC questionnaire with the category *All other app types*. Answer **No** to everything (violence, sexuality, language, controlled substances, gambling, user interaction and content sharing, sharing location with other users, digital purchases, unrestricted web access). Expected result: **Everyone / 3+ / PEGI 3**. |
| Target audience | **13 and over**. The app suits everyone, but choosing under-13 brings in the Families policy's extra requirements and review, which don't add anything here. |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | None. Air quality is general information, not a health app. |
| Data safety | See section 6 |
| Accessibility API | See section 7 |

## 6. Data safety

Aangan's developers receive no data. The form also covers data the app itself sends to third parties,
though, and the forecast request sends the chosen city's coordinates to Open-Meteo.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all data encrypted in transit? | **Yes** (HTTPS only; `usesCleartextTraffic="false"`) |
| Do you provide a way for users to request that their data is deleted? | Data isn't kept off the device. Answer **No**, and explain in the policy that everything is on the TV and is deleted by clearing data or uninstalling. |
| Data type | **Location → Approximate location** |
| Collected? | **Yes**: the city's coordinates, rounded to about 1 km, are sent to Open-Meteo for the forecast. |
| Shared? | **No**: it's sent only to provide the forecast the user asked for, which Play treats as user-initiated rather than sharing. |
| Processed ephemerally? | **No**. We can't promise how Open-Meteo handles requests. |
| Required or optional? | **Required**: the weather needs a city (Gurgaon by default). |
| Purpose | **App functionality** |

Nothing else is collected. Family names and dates, the app list and settings never leave the TV.
City search text is also sent to Open-Meteo when the user searches; it doesn't fit a Play data type,
but if you prefer to be conservative, add **App activity → Other user-generated content**, collected,
not shared, for app functionality.

## 7. Accessibility API declaration

Aangan isn't an accessibility tool (`isAccessibilityTool` isn't set), so Play needs a declaration.

- **Core functionality:** *Aangan is a home screen (launcher) for Android TV and Google TV. Most TVs
  provide no way to change the default home screen, so Aangan offers an optional accessibility service
  that opens Aangan when the user presses the Home button, or when the TV's built-in home screen
  appears at start-up.*
- **How it uses the API:** *A fixed rule, not autonomous action. The service filters key events and
  consumes only KEYCODE_HOME, passing every other key through. It receives window-state events only
  from the stock launcher packages listed in its configuration. It has canRetrieveWindowContent=false
  and reads nothing on screen. It starts exactly one activity, Aangan's home screen, and collects,
  stores and transmits no data.*
- **Prominent disclosure:** shown before Aangan opens Android's accessibility settings, from the welcome
  and from Settings, with **Agree and continue** and **Not now**
  (`HomeButton.askToTurnOn`, screenshot `graphics/disclosure.png`).
- **Video:** upload `disclosure.mp4` (67 seconds, 1280×720, recorded on a Sony Android 9 TV) as an
  unlisted YouTube video and paste the link. It shows, on a TV with no other copy of Aangan: the home
  screen, Aangan's Settings with **Open with the Home button** off, the disclosure, **Agree and
  continue**, Android's accessibility list with Aangan in it, Android's own "Use Aangan?" warning, and
  Aangan switched on.

## 8. Release

1. Internal testing: upload `build/aangan.aab`, add yourself as a tester, install on a TV from Play.
2. Personal account: closed testing with 12 or more testers for 14 days.
3. Production: Play reviews TV apps against the
   [TV app quality guidelines](https://developer.android.com/docs/quality-guidelines/tv-app-quality).
   Aangan has the banner, the leanback launcher entry, no touchscreen requirement, D-pad navigation
   throughout, and `android.software.leanback` required, so Play offers it only to TVs.
4. Each later release: bump `--version-code` in `build.sh` (it must always increase), build the bundle,
   upload, and add release notes from `CHANGELOG.md`.

## Notes for review

- Screenshots show third-party apps' tiles (Netflix, YouTube and so on) because they're installed on
  the TV. Launchers on Play commonly show them, but if a reviewer objects, retake the screenshots
  with those apps hidden.
- The app sends no data to its developers and has no server. The privacy policy covers the Open-Meteo
  requests, the phone setup page and the accessibility service in detail.

## 9. Automatic uploads from CI

Every release CI publishes can also go to Play's internal track. The step stays dormant until it's
set up:

1. Upload the first bundle by hand (step 8). Play only accepts API uploads for an app that already exists.
2. In Google Cloud, create a service account in any project and download a JSON key for it. Enable the
   *Google Play Android Developer API* for that project.
3. In Play Console → *Users and permissions*, invite the service account's email and give it
   *Release to testing tracks* for Aangan.
4. Store the key as a secret of the approval-gated `release` environment, then delete the file:

       gh secret set PLAY_SERVICE_ACCOUNT_JSON --env release --repo vaari-dev/aangan < key.json

5. Until the first production release, Play takes drafts only, so CI uploads drafts that you roll out in
   the Console. Afterwards, set `gh variable set PLAY_RELEASE_STATUS --body completed --repo vaari-dev/aangan`
   to release to testers straight away.

Promoting from internal testing to production stays a deliberate step in the Console.
