# MtG Life Tracker

A shared life tracker for Magic: The Gathering. Everyone at the table joins the
same session on their own phone; each player changes their own life total and
counters, and sees everyone else's update in real time.

## Features

- Life totals with optimistic updates, plus undo and a per-game history covering every
  counter, turning counters on or off, and Day/Night
- Per-player counters: commander damage (tracked per opposing commander), poison,
  energy, experience, storm, commander tax, the Ring, monarch, initiative,
  city's blessing, and custom counters
- Session-wide Day/Night toggle and a game timer (stopwatch or countdown)
- Host controls: shared game rules (starting life, damage limits, player cap),
  new game in the same session, removing players
- Invites by code, QR code or a shareable https link
- Friends: send requests in a session, see when friends are in a game and join them
- Resumes the last session after the app is restarted

## UI Mockup

Interactive HTML mock of the app screens:
**[https://claude.ai/artifact/FDwKP6PLyLT7dUc1NKhWiP](https://claude.ai/artifact/FDwKP6PLyLT7dUc1NKhWiP)**

## Architecture

```
MtGLifeTracker/
├── core/         Android library, no Firebase: SessionViewModel, data model,
│                 game rules (domain/), and the SessionApi / SessionConnection /
│                 CodeRegistry / UserPrefs interfaces a backend implements
├── firebase/     Firebase adapter: SessionSchema (pure mapping to the database
│                 layout) plus thin FirebaseSessionApi / FirebaseSessionConnection
├── app/          Jetpack Compose UI, SharedPreferences-backed UserPrefs
├── rules-tests/  Emulator tests for database.rules.json
└── docs/join/    Invite landing page served by GitHub Pages
```

The app talks directly to **Firebase Realtime Database**; there is no server of
our own. Every client signs in anonymously with **Firebase Authentication**
before touching the database.

Business rules don't depend on Firebase: code allocation (`SessionCodes`), seat
handling on join, new games and leaving (`SeatRules`), and stat routing live in
core and are unit-tested there. Switching backends means implementing
`SessionApi`, `SessionConnection` and `CodeRegistry` and a schema like
`SessionSchema`; the view model and the rules stay as they are.

### Data model

```
presence/<playerId>            session the player is currently in
sessionCodes/<CODE>            session ID for an 8-character invite code
sessions/<sessionId>/
    code, createdAt, hostUserId, game
    settings/                  startLife, commanderDeathThreshold, infectDeathThreshold, maxPlayers
    users/<playerId>/          displayName, life, conceded, online, game,
                               customStats/<stat>, commanderDamage/<opponentId>
    customStatNames/<stat>     NUMERIC | TOGGLE | RING_STAGE
    globalStats/<stat>         session-wide values (Day/Night)
    friendRequests/<to>/<from>, friendAccepted/<to>/<from>
```

A player ID is the device's anonymous **Firebase Auth uid**, so the security
rules check ownership directly (`$userId == auth.uid`): only you can write your
seat, presence and friend requests. The host's actions (rules, new game,
removing players) are checked against `hostUserId`. The uid survives app
restarts and is lost when the app's data is cleared; backups don't copy it.

### Session flow

1. A player taps **Create new session**. The app claims a free 8-character code
   and becomes host; the session uses the host's game rules from Settings.
2. They tap the QR icon to show a QR code or share an https invite link
   (`https://kregosh.github.io/MtGLifeTracker/join/?code=…`). The link opens a
   small page that hands the code to the app via `mtgtracker://join/<code>`.
3. Other players scan the code, tap the link, or type the code on the home screen.
4. Life changes are batched for 400 ms and written with Firebase transactions.
   A player who loses connection is shown as offline (via `onDisconnect`) and
   keeps their seat, so they can resume. The last player to leave deletes the
   session.

## Setup

### Firebase

1. Create a Firebase project with a Realtime Database.
2. **Authentication → Sign-in method**: enable **Anonymous**.
3. Add an Android app with package `com.kregosh.mtglifetracker` and download
   `google-services.json` into `app/`. The file is gitignored; CI writes it from
   the `GOOGLE_SERVICES_JSON` secret (raw JSON).
4. Deploy the rules whenever `database.rules.json` changes, together with the
   matching app version:

   ```bash
   npx firebase-tools deploy --only database --project <your-project-id>
   ```

   or paste the file into **Realtime Database → Rules** in the console.

### Invite page

Enable **GitHub Pages** for the repository (**Settings → Pages → Deploy from a
branch → `main` / `docs`**) so the invite links resolve.

## Building and testing

| Tool        | Version |
|-------------|---------|
| JDK         | 17+     |
| Android SDK | API 35  |
| Node.js     | 22 (rules tests only) |

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew testDebugUnitTest         # unit tests for every module
./gradlew lintDebug                 # Android lint for every module

cd rules-tests && npm ci && npm test   # security rules against the database emulator
```

CI (`.github/workflows/ci.yml`) runs all of the above plus a minified release
build on every pull request. Every push to `main` publishes a debug APK as a
GitHub release (`.github/workflows/release.yml`).
