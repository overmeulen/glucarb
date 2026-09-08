# Play Store listing - Glucarb

Everything below is ready to paste into Play Console.

The listing names diabetes only to say who built the app and who it is for. It carefully
avoids "insulin", "dose", "bolus", "blood sugar" and any suggestion that the app tells
you what to do with the number it produces. That line matters: stating an audience is
not a medical claim, but a listing that implies dosing advice would put the app under
Play's Health apps policy and, in the EU, into medical-device territory. Keep additions
on the safe side of it.

---

## Short description (80 characters max)

```
Count the carbs in your meals in seconds. Private, offline, no account.
```

71 characters.

Alternatives if you prefer a different emphasis:

```
Your own food catalog. Log a meal's carbs in two taps. Works fully offline.
```
(74 characters)

```
A fast carbohydrate log. Build your catalog, tap, done. No account, no ads.
```
(74 characters)

---

## Full description (4000 characters max)

```
Glucarb is a carbohydrate log built around a single idea: recording a meal should take
seconds, not minutes.

It was designed by a diabetic, for diabetics. Counting carbohydrates is something you do
several times a day, every day, for years. An app that takes a minute of fiddling per
meal is an app you stop using by the end of the month. So the whole thing is built
around keeping that count down to a few taps.

You build a small catalog of the things you actually eat, and from then on logging is a
couple of taps.


YOUR OWN CATALOG

Add the foods you eat, with a photo so you can recognise them at a glance. Choose grams
or millilitres, enter the carbohydrate content per 100 g or 100 ml, and you are done.

For things that are naturally counted rather than weighed - slices of bread, biscuits,
squares of chocolate - mark the item as available in portions and give the weight of one
portion. You can then log "3 slices" instead of reaching for the scales.


BUILT FOR SPEED

The home screen shows your foods as large tiles. Tap one, tap an amount, and it is in
the meal. Glucarb remembers the amounts you use most for each food and offers them as
one-tap shortcuts, so your regular breakfast takes about four taps in total.

The running total for the meal is the biggest thing on the screen. There is no "finish"
button to remember: a meal closes itself after a period of inactivity that you choose.


PHOTO ESTIMATES, WITH THE ASSISTANT YOU ALREADY USE

Eating something that is not in your catalog, and not easy to weigh? Take a photo and
hand it to an AI assistant already installed on your phone. Glucarb passes the picture
across, you paste the question, and the number it gives you comes straight back into the
meal.

Glucarb has no AI of its own and no server. You choose which app receives the photo, and
nothing is sent anywhere unless you tap that button.


HISTORY AND EXPORT

Every meal is kept with its date and time. When you want to look at the bigger picture,
export your history from any starting date as a plain CSV file with two columns: when
the meal was, and how many grams of carbohydrate it contained.

That format is deliberately minimal. It opens in any spreadsheet, and it is simple
enough to hand to an AI assistant and ask questions about it in plain language.


PRIVATE BY CONSTRUCTION, NOT BY PROMISE

Glucarb requests no Android permissions at all. It is built without internet access, so
it is not merely unwilling to send your information somewhere - it is incapable of it.

No account. No sign-in. No ads. No analytics. No tracking.

Your catalog and your history live on your phone. You can export a full backup archive
at any time and restore it on another device, so your data is never trapped in the app.


ALSO

- Works entirely offline, including on a phone in airplane mode
- Dark theme
- No subscription and no in-app purchases
- Uses your existing camera and gallery apps, so it never needs access to either


Glucarb is a record-keeping and arithmetic tool: you supply the carbohydrate values and
it adds them up. It does not provide medical advice, does not recommend insulin doses,
and is not a substitute for the guidance of your own healthcare team. Always check the
figures against your own judgement.
```

---

## Graphics

| Asset | File | Status |
|---|---|---|
| App icon, 512x512, 32-bit PNG | `store/icon-512.png` | Ready |
| Feature graphic, 1024x500 | `store/feature-graphic-1024x500.png` | Ready |
| Phone screenshots, 2-8 | - | You need to capture these |

Both graphics are generated from `store/icon.html` and `store/feature-graphic.html` by
running `store/render.ps1`, which drives headless Edge. Regenerate rather than editing
the PNGs, so the store icon always matches the launcher icon vector.

---

## Screenshots - what to capture

Play needs at least 2 phone screenshots; 4 to 6 is the practical minimum for a listing
that does not look abandoned. They must be genuine captures of the app, so these have to
come off your phone - a mockup would breach Play's rule that screenshots depict the real
in-app experience.

Requirements: PNG or JPEG, shortest side at least 320 px, longest at most 3840 px, and
no more than 2:1 either way. A stock phone screenshot at 1080x2400 satisfies all of
this, so no editing is needed.

Suggested order, because the first two are all most people ever see:

1. Home screen with a meal in progress - a few items logged and a decent total showing.
   This is the shot that explains the app.
2. The tile grid with a good handful of foods that have photos on them.
3. Adding an amount, with the one-tap shortcut values visible.
4. The item editor for an item that uses portions.
5. History.
6. The export dialog.

Capture with power + volume-down, then transfer them off the phone. Fill the catalog
with real-looking food first: an empty or half-configured app makes a poor first
impression, and screenshots are the single biggest factor in whether someone installs.

---

## Other listing fields

| Field | Value |
|---|---|
| App name | Glucarb |
| Category | Health & Fitness |
| Tags | Food, Nutrition, Tracker |
| Contact email | overmeulen85@gmail.com |
| Website | https://overmeulen.github.io/glucarb/ |
| Privacy policy | https://overmeulen.github.io/glucarb/privacy.html |
