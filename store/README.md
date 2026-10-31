# Google Play runbook

Listing: [`listing.md`](listing.md), with its text and graphics in [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/).

## 1. Account

- https://play.google.com/console ($25 once, plus identity verification).
- A *personal* account created after November 2023 needs a closed test with 12+ testers for 14 days before
  production. An *organisation* account ("Vaari") skips that but needs a D-U-N-S number (free, one to two weeks).
- Support email: a role address, not a personal one.

## 2. Create the app

- Name `Aangan: Private TV Launcher`; default language English (India) – en-IN; App; Free.
- Package `dev.vaari.aangan` (fixed by the first upload).

## 3. Signing: keep the existing key

GitHub installs must accept Play's updates, so Play signs with our key.

1. *Test and release → Setup → App signing* → **Use a different key → Export and upload a key from Java keystore**.
2. Download `pepk.jar` and the encryption key from that page, then:

       java -jar pepk.jar --keystore=$HOME/.android-keys/aangan-release.jks --alias=aangan \
           --output=aangan-signing-key.zip --include-cert --rsa-aes-encryption \
           --encryption-key-path=encryption_public_key.pem

3. Upload `aangan-signing-key.zip`, then delete it.
4. **Upload key:** a separate one is safer (Google can reset a lost upload key); reusing the signing key is simpler.

Play's app-signing SHA-256 must be
`7F:90:5B:6D:3C:FB:CC:92:CD:2D:A7:20:3C:5B:6C:F3:65:B6:5D:67:CB:3D:16:2A:4C:54:D4:04:3C:AA:14:84`.

## 4. Build the bundle

    AANGAN_KEYSTORE=$HOME/.android-keys/aangan-release.jks \
    AANGAN_KEY_PASS="$(security find-generic-password -s aangan-release-key -w)" ./gradlew bundleRelease

Output: `app/build/outputs/bundle/release/aangan-release.aab`. Without both variables it's debug-signed and Play
refuses it.

## 5. App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | https://vaari-dev.github.io/aangan/privacy.html |
| App access | All functionality is available without special access |
| Ads | No |
| Content rating | IARC, category *All other app types*. **No** to everything (violence, sexuality, language, controlled substances, gambling, user interaction and content sharing, sharing location with other users, digital purchases, unrestricted web access). Expect **Everyone / 3+ / PEGI 3**. |
| Target audience | **13 and over** (under-13 brings in the Families policy for no benefit). |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | None (air quality is general information). |
| Data safety | Section 6 |
| Accessibility API | Section 7 |

## 6. Data safety

Developers receive nothing, but the form covers data the app sends to third parties: the city's coordinates go
to Open-Meteo.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all data encrypted in transit? | **Yes** (HTTPS only; `usesCleartextTraffic="false"`) |
| Do you provide a way for users to request that their data is deleted? | **No**. Nothing is kept off the device; the policy says data is deleted by clearing data or uninstalling. |
| Data type | **Location → Approximate location** |
| Collected? | **Yes**: the city's coordinates, rounded to about 1 km, sent to Open-Meteo. |
| Shared? | **No**: sent only to provide the user's forecast, which Play treats as user-initiated. |
| Processed ephemerally? | **No** (we can't vouch for Open-Meteo). |
| Required or optional? | **Required**: the weather needs a city (Gurgaon by default). |
| Purpose | **App functionality** |

Also add **App activity → Other user-generated content** (city search text): collected, not shared, optional, app
functionality. The privacy policy's "Google Play's data safety" section matches both.

Nothing else: family days, the app list and settings never leave the TV.

## 7. Accessibility API declaration

Not an accessibility tool (`isAccessibilityTool` unset), so a declaration is needed.

- **Core functionality:** *Aangan is a home screen (launcher) for Android TV and Google TV. Most TVs
  provide no way to change the default home screen, so Aangan offers an optional accessibility service
  that opens Aangan when the user presses the Home button, or when the TV's built-in home screen
  appears at start-up.*
- **How it uses the API:** *A fixed rule, not autonomous action. The service filters key events and
  consumes only KEYCODE_HOME, passing every other key through. It receives window-state events only
  from the stock launcher packages listed in its configuration. It has canRetrieveWindowContent=false
  and reads nothing on screen. It starts exactly one activity, Aangan's home screen, and collects,
  stores and transmits no data.*
- **Prominent disclosure:** shown before opening Android's accessibility settings, from the welcome and from
  Settings, with **Agree and continue** and **Not now** (`HomeButton.askToTurnOn`; screenshot `graphics/disclosure.png`).
- **Video:** `disclosure.mp4` (67 s, 1280×720, Sony Android 9 TV) as an unlisted YouTube link. It shows the home
  screen, Settings with **Open with the Home button** off, the disclosure, **Agree and continue**, Android's
  accessibility list, Android's "Use Aangan?" warning, and Aangan switched on.

## 8. Release

1. Internal testing: upload the `.aab`, add yourself as tester, install on a TV from Play and check it.
2. Personal account only: closed testing, 12+ testers, 14 days.
3. Production, reviewed against the [TV app quality guidelines](https://developer.android.com/docs/quality-guidelines/tv-app-quality):
   banner, leanback launcher entry, no touchscreen requirement, D-pad throughout, `android.software.leanback`
   required (TVs only).
4. Later releases: raise `versionCode` and `versionName`, build, upload, notes from `CHANGELOG.md`.

## Notes for review

- Play screenshots are from the Google TV 16 emulator, so the only app tiles are Google's (YouTube, Live TV, Play
  Store, Play Games): no other companies' logos.
- No developer server; the privacy policy covers Open-Meteo, the phone page and the accessibility service.

## 9. Automatic uploads from CI

CI uploads each release to the internal track once set up:

1. Upload the first bundle by hand (step 8); the API only accepts existing apps.
2. Google Cloud: create a service account, download a JSON key, enable the *Google Play Android Developer API*.
3. Play Console → *Users and permissions*: invite the service account with *Release to testing tracks* for Aangan.
4. Store the key in the `release` environment, then delete the file:

       gh secret set PLAY_SERVICE_ACCOUNT_JSON --env release --repo vaari-dev/aangan < key.json

5. Each upload carries release notes from `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, in
   the en-IN listing.
6. Until the first production release Play takes drafts only, rolled out in the Console. Afterwards:
   `gh variable set PLAY_RELEASE_STATUS --body completed --repo vaari-dev/aangan`.

Promotion to production stays manual.
