# Releasing on Google Play

Everything needed to publish MtG Life Tracker on Google Play, starting with an internal test.

## 1. One-time setup

### Upload key

Play signs the app for the store itself (Play App Signing). You sign each upload with an
**upload key**, which you create once and keep. If it is ever lost, Play support can reset it.

```sh
keytool -genkeypair -v -keystore upload.jks -alias upload \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=MtG Life Tracker"
base64 -w0 upload.jks > upload.jks.base64     # macOS: base64 -i upload.jks -o upload.jks.base64
```

Keep `upload.jks` and its passwords somewhere safe, outside the repository. Then add four
repository secrets (GitHub → Settings → Secrets and variables → Actions):

| Secret                     | Value                                           |
|----------------------------|-------------------------------------------------|
| `UPLOAD_KEYSTORE_BASE64`   | the contents of `upload.jks.base64`             |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password you chose                 |
| `UPLOAD_KEY_ALIAS`         | `upload`                                        |
| `UPLOAD_KEY_PASSWORD`      | the key password (the same one if you only gave one) |

### Play Console

1. Register at <https://play.google.com/console> (one-off fee of US$25).
2. **Create app**: name *MtG Life Tracker*, default language English, *App*, *Free*.
3. Accept **Play App Signing** when asked during the first upload.

## 2. Build a bundle

GitHub → Actions → **Play release** → *Run workflow* → enter the version name (e.g. `0.1.0`).

The workflow builds the release bundle with the upload key and checks its signature. It then
attaches `mtg-life-tracker-<version>-<code>.aab` to a **draft release** on the Releases page.
The version code counts up by itself with every run, as Play requires.

## 3. Internal test

Play Console → the app → **Testing → Internal testing**:

1. **Testers**: create an email list with the Google accounts of up to 100 testers.
2. **Create new release** → upload the `.aab` → release notes → *Save* → *Review release* → *Start rollout*.
3. Share the **opt-in link** from the Testers tab. Testers open it on their phone, accept, and
   install from the Play Store. Updates arrive through the store like any other app.

Testers who have the GitHub debug APK installed must uninstall it first: the store version is
signed with a different key.

## 4. App content (Play Console → Policy → App content)

Play asks for these before the first release; the answers for this app:

- **Privacy policy**: <https://kregosh.github.io/MtGLifeTracker/privacy.html>
- **Ads**: no ads.
- **App access**: all functionality is available without special access.
- **Content rating**: questionnaire, category *Utility / Productivity*. Users can interact
  (they see each other's chosen display names); no violence, no gambling, no purchases.
- **Target audience**: 13 and over.
- **Data safety**:
  - Data is collected and encrypted in transit; users can request deletion (see the privacy policy).
  - *Personal info → Name*: the display name. Collected, not shared, required for the app to
    work, purpose *App functionality*.
  - *App activity → Other user-generated content*: life totals and counters. Same answers.
  - *Device or other IDs*: the anonymous Firebase ID. Same answers.
  - Not collected: location, contacts, photos (a chosen background picture stays on the
    device), financial info, health, messages, audio, files, calendar, web history.
- **Foreground service permissions**: the app declares `FOREGROUND_SERVICE_SPECIAL_USE`.
  Explain: *"Keeps a live multiplayer game session connected while the player briefly
  switches to another app, so other players keep seeing their life total. A notification shows
  while it runs; it stops when the player leaves the game."* Play may ask for a short video
  showing the notification while a game is running.
- **News app / Government / Financial / Health**: no.

## 5. Store listing (Grow → Store presence → Main store listing)

Graphics are in [`graphics/`](graphics):

| Field                 | File                                         |
|-----------------------|----------------------------------------------|
| App icon (512 × 512)  | `graphics/icon-512.png`                      |
| Feature graphic       | `graphics/feature-graphic-1024x500.png`      |
| Phone screenshots     | `graphics/screenshot-1-session.png`, `-2-details.png`, `-3-invite.png` |

**App name** (30 characters max)

> MtG Life Tracker

**Short description** (80 characters max)

> Life, commander damage and poison for your Magic games, shared live at the table

**Full description**

> Keep track of your Magic: The Gathering games together. Each player follows the game on their
> own phone, and every change shows up instantly for everyone at the table.
>
> • Your life total fills the screen: tap the right half to gain, the left half to lose, hold for ±5
> • Commander damage and poison/infect at a glance, with how close you are to losing (5/21, 2/10)
> • See everyone else's life, commander damage and poison on top; tap a player for the rest
> • Extra counters when you need them: energy, experience, storm, the Ring, the monarch, day/night and your own
> • Start a game in seconds and invite friends by QR code or link. No account needed
> • Watch a game without playing
> • Undo any change, see the game's history, and run a game timer
> • Works in the browser too, for friends without the app
>
> Free, no ads, no tracking.
>
> MtG Life Tracker is unofficial fan content, not approved or endorsed by Wizards of the Coast.

**Category**: Board (Games) or Tools. **Contact email**: required, shown publicly.

## Note on the name

"Magic: The Gathering" and "MTG" are trademarks of Wizards of the Coast. An internal test is not
reviewed for this. For an open or production release, Play may object to the trademark in the app
name. A neutral name with "for Magic: The Gathering" in the description is the safer route.
