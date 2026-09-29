# MtG Life Tracker

A shared life-total tracker for Magic: The Gathering. Multiple players join the
same session; each player sees everyone's counter in real time and can use
**+** / **−** buttons to change their own value.

## Architecture

```
MtGLifeTracker/
├── shared/     Kotlin JVM library — WebSocket protocol + REST DTOs (shared by both)
├── backend/    Ktor server — session management + WebSocket relay
└── app/        Android app — Jetpack Compose UI, Ktor client
```

### Session flow

1. One player taps **Create new session** → the backend mints a UUID session ID
   and a 6-character invite code (e.g. `ABC123`).
2. That player taps **Share** and sends the code (or a `mtgtracker://join/ABC123`
   deep link) to their friends.
3. Friends enter the code on the home screen or tap the link; the app opens the
   live session automatically.
4. Every player's counter is an unsigned 32-bit integer. Each player controls
   their own row; changes propagate via WebSocket to all connected clients
   within milliseconds.

---

## Building

### Prerequisites

| Tool        | Version |
|-------------|---------|
| JDK         | 17+     |
| Android SDK | API 35  |
| Gradle      | 8.14.3  |

### Backend

```bash
# Run locally (default port 8080)
./gradlew :backend:run

# Build fat jar
./gradlew :backend:jar
java -jar backend/build/libs/backend.jar
```

Override the port with `PORT=9000 ./gradlew :backend:run`.

### Android app

Open the project in **Android Studio Ladybug (2024.2.1)** or later and click
**Run** on the `:app` configuration.

The app is configured to talk to `10.0.2.2:8080` by default (Android emulator
localhost alias). For a physical device or a remote server, change
`SERVER_BASE_URL` and `SERVER_WS_URL` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "SERVER_BASE_URL", "\"https://your-server.example.com\"")
buildConfigField("String", "SERVER_WS_URL",   "\"wss://your-server.example.com\"")
```

---

## Protocol

WebSocket endpoint: `ws://host/ws/sessions/{sessionId}`

### Client → Server

| Message            | JSON                                              |
|--------------------|---------------------------------------------------|
| Join session       | `{"type":"join","userId":"<uuid>","displayName":"Alice"}` |
| Increment counter  | `{"type":"increment"}`                            |
| Decrement counter  | `{"type":"decrement"}`                            |

### Server → Client

| Message          | JSON                                              |
|------------------|---------------------------------------------------|
| Joined           | `{"type":"joined","userId":"...","sessionCode":"ABC123"}` |
| State snapshot   | `{"type":"state","users":[{"id":"...","displayName":"Alice","value":42}]}` |
| Error            | `{"type":"error","message":"..."}` |

State snapshots are broadcast to **all** connected clients after every join,
increment, decrement, and disconnect.
