# CarbTrack

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
./gradlew testDebugUnitTest   # 25 unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug
```

`local.properties` must point at your SDK, e.g. `sdk.dir=D\:\\android-sdk`.

## Layout

```
data/    Room entities, DAOs, repositories, photo storage, settings
domain/  carb math, item ranking, icon suggestion, clipboard parsing — all unit tested
ui/      Compose screens: home, item editor, history, settings
di/      Hilt wiring
```
