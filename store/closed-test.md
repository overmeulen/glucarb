# Closed test - release notes

## What to put in the "release notes" field

Play asks for release notes per language. For a first closed test, testers mainly need to
know what to try and what is known to be shaky.

```
First closed test build.

What to try:
- Add a few foods you actually eat, with a photo and their carbs per 100 g or 100 ml.
- For things you count rather than weigh (bread, biscuits), tick "available in portions".
- Log a few meals and check the running total.
- Try the camera button on the home screen to send a plate photo to your AI assistant.
- Export your history from the History screen.

Known rough edges:
- The AI assistant receives the photo but usually ignores an attached question, so the
  prompt is copied to your clipboard for you to paste.
- Settings are not included in the backup file yet.

Please report anything that looks wrong, especially around photos.
```

---

## Release setup

| Field | Value |
|---|---|
| Track | Closed testing |
| Bundle | `dist/Glucarb-1.0-beta.aab` |
| Version code | 1 |
| Version name | 1.0 |
| Signing | Play App Signing, upload key from `D:/Glucarb-keys/glucarb-release.jks` |

The version code is deliberately left at 1. Nothing has been uploaded yet, so 1 is free,
and keeping it means this exact bundle can later be promoted from closed testing to
production without a rebuild - the artifact that was tested is the artifact that ships.

Every later upload must increase the version code, including a re-upload after a
rejection.

---

## Testers

Closed testing needs a tester list before the release can go live. Either:

- an email list created under Testing > Closed testing > Testers, or
- a Google Group.

Testers must **opt in through the web link** Play gives you before the app appears for
them in the Play Store. Installing straight from the store without opting in does not
work, and this is the most common reason a tester reports "I cannot find the app".

Each tester's Play Store account email has to match the address on the list.

---

## Before promoting to production

- [ ] Photo capture and gallery pick both work on a real device
- [ ] The AI hand-off reaches the assistant and the number comes back
- [ ] Export produces a file that opens cleanly in a spreadsheet
- [ ] Backup export and restore survive an uninstall
- [ ] Screenshots captured from the real app, with a realistic catalog
- [ ] Merchant account set up and the app set to paid **before** the first production
      release - free to paid is not reversible
