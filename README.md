# Glucarb

An Android app for counting grams of carbohydrate per meal, built around one goal:
**three taps to log** — tap the item, tap a quantity chip, done.

## What it does

- **Your own catalog.** Every item is created by you: name, picture, unit (g or ml) and
  carbs per 100 g/ml. Items you eat in units — bread, biscuits — get a portion size
  ("1 slice = 32 g") so you enter `2` instead of `64`.
- **Icons instead of photos.** Typing a name suggests icons from a built-in food set,
  in English and French ("pêche", "pommes", "pain au chocolat"). A confident match is
  applied on its own, so most items need no picture at all; the rest of the row offers
  the same food family, and a photo is still one tap away.
- **Implicit meals.** A meal is always open. It closes itself after a configurable
  idle gap (90 min by default) or when you tap *Done*. Opening the app never creates
  an empty meal — the first entry does.
- **Ranking that learns.** The home grid orders items by how often you eat them, how
  recently, and what time of day it is, so breakfast items surface at breakfast.
- **AI for unknown plates.** Photograph the plate and send it to any assistant app on
  the phone with an editable prompt. The answer is read back off the clipboard and
  pre-fills a confirmation sheet; typing the number yourself always works too.
- **History.** Day-by-day list of past meals, still editable.

## Design notes

- Entries **snapshot** their quantity, resolved carbs and icon. Editing an item's carb
  ratio or icon later never rewrites what you already logged.
- Items are **archived**, not deleted, so history keeps its labels.
- The icon set stops at Emoji 5.0, because `minSdk 26` is Android 8.0 and anything newer
  would render as an empty box there. Glyphs are declared as code points so the source
  file stays pure ASCII.
- AI plate estimates are stored as standalone entries and are *not* added to the
  catalog — that would pollute the time-of-day ranking with one-offs.
- Catalog photos are downscaled to ~400 px so a large catalog stays inside Android
  Auto Backup's 25 MB quota. One-off plate photos are excluded from cloud backup.
  Settings also offers a manual export/restore zip.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26).

```
./gradlew testDebugUnitTest   # 42 unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew lintDebug
```

`local.properties` must point at your SDK, e.g. `sdk.dir=D\:\\android-sdk`.

## Installing on a phone

The release build is signed with a private key that is deliberately **not** in this
repo. Create `keystore.properties` at the repo root:

```properties
storeFile=D:/somewhere-outside-the-repo/Glucarb-release.jks
storePassword=...
keyAlias=Glucarb
keyPassword=...
```

Use forward slashes — a `.properties` file treats `\` as an escape character and will
silently mangle a Windows path. Generate the keystore with:

```
keytool -genkeypair -v -keystore Glucarb-release.jks -alias Glucarb \
        -keyalg RSA -keysize 4096 -validity 10950
```

Without `keystore.properties` the release build still assembles, just unsigned, so a
fresh clone is never broken by the missing secret.

**Back the keystore up.** Android identifies an app by its signature, so losing the key
means you can never again install an update over an existing Glucarb — you would have
to uninstall first, destroying the local database.

Then either `adb install -r dist/Glucarb-1.0.apk` over USB, or copy the APK to the
phone and open it from a file manager. Sideloading needs "Install unknown apps" granted
to whichever app opens the file.

Bump `versionCode` in `app/build.gradle.kts` for every build you intend to install over
the previous one.

## Publishing to Google Play

Play distributes an **app bundle**, not an APK:

```
./gradlew bundleRelease    # app/build/outputs/bundle/release/app-release.aab
```

Requirements met by this build:

| Requirement | Status |
|---|---|
| Target API 36, mandatory for new submissions from 31 Aug 2026 | ✅ `targetSdk = 36` |
| Signed release bundle | ✅ see above |
| Privacy policy URL | `PRIVACY.md` — must be published at a public URL |

Still to do outside the repo: a Play Console account (US$25 one-off); for new personal
accounts, a closed test with 12 testers opted in for 14 continuous days before
production access; a 512×512 icon, a 1024×500 feature graphic and screenshots.

### Data safety declaration

The app declares **no `INTERNET` permission**, so it cannot transmit anything, and it
requests no camera permission (photos arrive via an intent to the system camera or
gallery). Two disclosures are nonetheless required:

- **Android Auto Backup is enabled**, so the catalog, history, photos and settings are
  copied to the user's own Google account. Declare this.
- The **share-to-AI feature** hands a photo to a user-chosen third-party app. Declare it
  as a user-initiated transfer; whatever that app does is outside this app's control.

### Name

⚠️ `CarbTrack` is **already published on Play** as `com.kouidev.carbtrack`, a diabetes
carb/glucose tracker, and a second unrelated `CarbTrack` ships on the iOS App Store.
Shipping under this name risks a misleading-app-name takedown. See branch
`rename/glucarb` for a complete, tested rename. `applicationId` is immutable after the
first published release, so this must be settled before the first upload.

### Regulatory

Counting carbohydrates from user-entered data is not a medical device. Adding an
**insulin dose or bolus recommendation** would make it one under EU MDR — most likely
Class IIb, requiring a notified body and CE marking. Do not cross that line casually.

## Layout

```
data/    Room entities, DAOs, repositories, photo storage, settings
domain/  carb math, item ranking, icon suggestion, clipboard parsing — all unit tested
ui/      Compose screens: home, item editor, history, settings
di/      Hilt wiring
```
