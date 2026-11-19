# Architecture

MVVM in the presentation layer, Clean Architecture across layers, one Gradle module per layer and
per feature. Decisions that are hard to undo are recorded as ADRs in `docs/adr/`.

## 1. Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin, coroutines and Flow | Standard; structured concurrency fits sockets and polling |
| UI | Jetpack Compose, Material 3 with a custom theme | Declarative UI, previews, screenshot tests |
| DI | Hilt | Compile-time graph, ViewModel and WorkManager integration |
| HTTP | Retrofit + OkHttp + kotlinx.serialization | Mature; OkHttp also gives the WebSocket client |
| TLS trust | The platform's trust (system and user CAs, `network_security_config.xml`) plus one owner-pinned leaf certificate, in the shared client's trust manager | A home server with a self-signed certificate, without ever turning checks off (SPEC.md §8) |
| Live updates | OkHttp WebSocket | Same client, same auth interceptor |
| Local cache | Room | Last known devices, pets and timeline pages for offline display |
| Preferences | DataStore | Server address, notification state (the language and the theme are the phone's) |
| Secret storage | Android Keystore (AES-GCM key) encrypting the session token, and the pinned certificate, in DataStore | No deprecated crypto library |
| Images | Coil 3 with the shared OkHttp client | Media and thumbnails need the `Authorization` header |
| Video | Media3 ExoPlayer: HLS natively; fMP4-over-WebSocket through a custom `DataSource` | One player, two transports |
| Charts | Compose `Canvas` | Few, simple charts; no charting dependency |
| Notifications | A foreground service holding the server's notification WebSocket (OkHttp) | No other app, server or Google service required |
| Navigation | Navigation Compose with type-safe routes | Standard, testable |
| Open-source licences | AboutLibraries Gradle plugin and core (no UI library): the list is generated into the APK, restricted to the release build's direct dependencies (build-logic `directExternalDependencies`); our own screens group it by project | Apache 2.0 wants the licence to travel with the APK; no Google Play services (so not Google's oss-licenses plugin) |
| Static analysis | ktlint, detekt, Android lint | CI gate |
| Tests | JUnit 5, MockK, Turbine, kotlinx-coroutines-test, Robolectric, Compose UI tests, screenshot tests | One layer each, below |

Exact library versions are pinned in a Gradle version catalog (`gradle/libs.versions.toml`) when
the project is created, and upgraded through Dependabot, as on the server.

## 2. Modules

```
:app                      Application, MainActivity, NavHost, Hilt entry points, top-level wiring
:core:domain              Pure Kotlin. Models, repository interfaces, use cases, domain errors
:core:data                Repository implementations, mappers DTO→domain, sync with cache
:core:network             Retrofit service, DTOs, auth interceptor, WebSocket clients, OpenAPI contract tests
:core:database            Room database, entities, DAOs
:core:datastore           Preferences and encrypted session storage
:core:designsystem        Theme (colors, type, shapes), reusable components, illustrations
:core:ui                  Shared screen pieces: loading/empty/error states, formatting (weights, durations, dates)
:core:testing             Fakes of every repository, test dispatchers, fixtures from the OpenAPI examples
:core:e2e                 End-to-end test support: the mock server's address and control API, API clients
:feature:auth             Sign-in, session expired, change password
:feature:home             Home: device grid, pets row, alert banners
:feature:device           Litter box, feeder, fountain, generic device, device settings
:feature:history          Day strip, filters, timeline, event detail, media viewer
:feature:pets             Pet list and detail, weight chart
:feature:live             Live video player
:feature:settings         Server, notifications, phone name, sign out, About
:push                     Notification connection service, channels and deep links
```

**Dependency rule:**
- `feature:*` modules depend on `core:domain`, `core:designsystem` and `core:ui`, never on
  `core:data`, `core:network` or `core:database`.
- `core:data` depends on `core:domain`, `core:network`, `core:database` and `core:datastore`.
- `core:domain` depends on nothing Android.
- `:app` is the only module that sees everything; it binds implementations to interfaces with Hilt.

A Gradle convention plugin (`build-logic/`) holds the shared configuration, and a dependency check
in CI fails the build if a feature imports a data-layer package.

## 3. Layers

### Domain (`core:domain`)
- **Models**: `Device`, `DeviceFamily`, `Entity`, `DeviceAction`, `TimelineItem`, `Pet`,
  `FeedingPlan`, `DeferredFeed`, `Session`, `ServerInfo`. Kotlin data classes, no
  serialization annotations.
- **Repository interfaces**:
  - `AuthRepository`
  - `DeviceRepository` (stream of devices, refresh, run action, set entity)
  - `TimelineRepository` (paged)
  - `PetRepository`
  - `FeedingRepository`
  - `LiveUpdatesRepository`
  - `VideoRepository`
  - `TalkRepository`
  - `SettingsRepository`
- **Use cases**: one public `operator fun invoke`, one job each:
  - `SignInUseCase`, `ObserveDevicesUseCase`, `RunDeviceActionUseCase`, `SetEntityValueUseCase`,
    `ObserveTimelineUseCase`, `SaveFeedingPlanUseCase`, `ObservePetUseCase`…
  - Business rules live here, for example "a destructive action needs confirmation", or
    "visits today = summaries of the local day".
- **Errors**: a sealed `DomainError` (`Unauthorized`, `SessionEnded(reason)`, `Network`, `Server(code, message)`,
  `Validation(field, message)`, `RateLimited(retryAfter)`); use cases return `Result`-like
  `Outcome<T>`, never throw across layers.

### Data (`core:data`, `core:network`, `core:database`, `core:datastore`)
- **Network**:
  - **DTOs** mirror the server's OpenAPI schemas one to one. Contract tests deserialize the
    example payloads in `openapi.yaml`, copied into `core:testing` and refreshed by a script, so
    a server change breaks a test, not the app.
  - **Auth**: an `AuthInterceptor` adds `Authorization: Bearer`. A 401 `invalid_token` or WebSocket close code `4401` emits
    `SessionEnded`, which clears the token and routes to sign-in.
  - **TLS** (SPEC.md §8): every client is built on the one OkHttp client (Retrofit, Coil, Media3's
    data sources, the WebSockets), whose `ServerTrust` holds a `PinningTrustManager`: the
    platform's trust first, then the one certificate the owner pinned, for its host and port only,
    and only if it names the host. A refusal is a `CertificateRefusedException` in the handshake's
    causes, mapped to `DomainError.Certificate` (untrusted, changed, wrong host, out of date).
    The pin lives in the session store (`CertificateTrust`, core:data); changing it resets the
    trust (a new TLS context, the connection pool emptied), so nothing made under the old one is
    reused. A changed certificate refused while signed in is also kept for the app shell, which
    asks the owner about it over whatever screen is showing.
- **Repositories**: single source of truth in Room for devices, pets and timeline pages.
  - REST refreshes write to Room.
  - WebSocket messages patch Room (`hello` replaces, `state` patches entity values, `timeline` upserts by id with
    `replaces`).
  - ViewModels only observe Room flows, so UI is the same online and offline.
- **Writes**: always server-first; the cache updates when the WebSocket (or a refetch) confirms.
  No optimistic UI for device commands: a litter box that did not start must not look like it did.

### Presentation (`feature:*`) — MVVM
- One **ViewModel** per screen, exposing `StateFlow<XxxUiState>`, and one function per user intent
  (`onFeedNow(portions)`, `onActionClicked(key)`), or a single `onEvent(XxxEvent)` when there are
  many.
- **UiState** is an immutable data class (`loading`, `content`, `error` fields or a sealed type),
  built from use-case flows with `combine` and `stateIn(WhileSubscribed(5s))`.
- **One-shot effects** (navigate, show a snackbar, ask for confirmation) go through a `Channel`
  exposed as a `Flow`, collected once in the screen.
- **Composables** are stateless. A `XxxRoute` collects state and wires callbacks; a `XxxScreen` takes the
  state and lambdas. Previews and screenshot tests use the stateless screen.
- ViewModels hold no Android types except `SavedStateHandle`. Formatting of numbers, dates and
  plurals happens in `core:ui` with resources.

## 4. Live updates and video

- `LiveUpdatesRepository` owns one socket per process.
  - It is started when the first collector subscribes and stopped `WhileSubscribed(5s)` after the last one leaves, so it lives only while the app is visible.
  - It reconnects with exponential backoff and jitter, capped at 30 s, except after `4401`.
  - It parses each message into a domain event that `core:data` applies to Room.
- **Video**: `VideoRepository` opens `/devices/{id}/live` with the bearer header.
  - It sends the single `mse` request with the codecs the device's decoder supports, and feeds the binary fMP4 fragments to ExoPlayer through a pipe-backed `DataSource`.
  - On close code `1013`, or after two failed starts, it switches to the HLS URL with the same header (`DefaultHttpDataSource` with request properties).
  - Close code `4409` (replaced) is silent.

- **Talk**: `TalkRepository` opens `/devices/{id}/talk` (`TalkSocketClient`, on the shared
  OkHttp client and its bearer header) and records the microphone with `AudioRecord` only once
  the server says `ready`, sending raw PCM (s16le, 16 kHz, mono) as it is recorded, with no
  encoder.
  - A stop ends the recording, sends `{"type":"stop"}` and waits for the server to close (at most
    10 s); cancelling the collection closes at once. The microphone is released either way.
  - Every `error` code and close code becomes a `TalkFailure` the screen says in words; `4401`
    ends the session, as on the live socket.
  - The live screen holds the talk only while Talk is held and the screen is visible, and mutes
    the camera's own sound meanwhile (half-duplex).

## 5. Notifications

All in one (spec, decision 4; ADR 0007): the app keeps its own connection to petkit-local, as the
ntfy app would to an ntfy server. The server side is petkit-local issue 89.

- **Connection** (`:push`):
  - A foreground service holds the server's notification WebSocket while the user is signed in and
    notifications are on, with the bearer header like every other request.
  - Service type `specialUse` (the app is not on the Play Store, so no store review applies),
    which Android 14+ does not time-limit as it does `dataSync`. It starts after sign-in, at
    boot (`BOOT_COMPLETED`) and after an app update (`MY_PACKAGE_REPLACED`).
  - The socket carries notifications only, never device state: `/ws` stays a screen-time
    connection, closed in the background.
  - Reconnection with backoff, and immediately when the network comes back
    (`ConnectivityManager` callback). The server pings inside Cloudflare's idle timeout.
  - The last message id is kept in DataStore and passed back on reconnection, so the server
    replays what was missed.
  - Close code `4401` stops the service and ends the session (sign-in, with the reason).
- **Battery**: the app asks once, with an explanation, to be exempted from battery optimisation
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) when notifications are turned on; without it, Doze
  delays alerts. Settings shows the state and the shortcut to fix it, next to the notifications
  switch, as it does for Android 13's notification permission, which Home also asks for once in a
  banner until the user allows it or answers "Not now". Both screens ask through the same
  `core:ui` helper.
- **Kinds**: the kinds chosen in Settings are sent to the server, which filters per phone; the
  app does not drop what the server sent.
- **Delivery**:
  - Messages are small JSON documents: kind, device id, pet id, event code, timestamp, id.
  - The service maps them through the same keyed translations as the UI and posts a notification
    on the channel for its kind, in the phone's language. Channel importance follows
    petkit-local's own priorities: low for visits and meals, high for faults, failed meals and
    offline devices. A notification's Android id is the server's id, so one delivered twice
    replaces itself; a batch replayed on reconnection makes one sound.
  - Tapping it opens the device (a condition: food low, a consumable, offline) or the device's
    History on the day it happened (an event), through a link in an explicit intent to the app's
    own activity, which has no intent filter for it.
  - The connection's own ongoing notification sits on a separate, minimum-importance channel.

## 6. Testing

Six kinds of test, from fast and many to slow and few. Each has one job; a bug should be caught by
the cheapest kind able to see it.

| Kind | Runs on | What it proves | Tools |
|---|---|---|---|
| **Unit** | JVM | Domain use cases and rules, ViewModel state sequences and effects, mappers, formatting, WebSocket message handling | JUnit 5, MockK, Turbine, kotlinx-coroutines-test, fakes from `core:testing` |
| **Integration (data)** | JVM | Repositories against a scripted server (MockWebServer, incl. 401/429/5xx and a WebSocket), Room DAOs and migrations, Keystore-backed session store | MockWebServer, Robolectric, Room testing |
| **Contract** | JVM | Every DTO parses the examples and schemas of the server's `openapi.yaml`; every route the app calls exists in it | kotlinx.serialization, a small OpenAPI reader; the YAML is fetched from a pinned petkit-local release by a script and committed |
| **Compose UI** | JVM (Robolectric), and emulator for the few that need a real device | Each screen's stateless composable: content for each UiState, clicks reach the right callbacks, confirmation dialogs, TalkBack labels, EN and FR | Compose UI test, Robolectric; Gradle Managed Devices (ATD image) on the emulator |
| **Screenshot** | JVM | Every component and screen in light/dark × EN/FR × font scale 100 %/130 % renders as recorded; a visual change shows up in review as an image diff | Roborazzi on Robolectric |
| **End to end** | Emulator + mock server, and nightly the petkit-local container | Real flows against a server: sign in, home lists the devices, run an action and see the state change arrive by WebSocket, history paging, pet detail, session revoked from the panel sends the app back to sign-in. On every PR against the mock (mock-server/), whose control API makes the household change; every night against `ghcr.io/gamso/petkit-local:dev`, where the tests needing that control API (`@MockOnly`) are skipped | Gradle Managed Device; the real server started with `--no-ha`, seeded with a fake T5 (HTTP registration and MQTT session) by the server's own smoke test (`scripts/real-server.sh`) |
| **Smoke** | Emulator | The **release** build — minified, shrunk, signed with the CI key — installs, starts, signs in to the container and draws Home without a crash or an ANR. Catches what only R8 and release config break | Same emulator and container; one short instrumented test run against the release APK |

Rules:
- No test reaches the internet; the only server is MockWebServer or the local container.
- Fakes, not mocks, for repositories (`core:testing`), so ViewModel tests read like the app.
- Coverage targets: domain 90 %, data 80 %, ViewModels 80 %, measured with Kover and reported on
  each pull request (not a gate at first).

## 7. CI (GitHub Actions)

| Workflow | Trigger | Jobs (in parallel unless noted) | Target time |
|---|---|---|---|
| `ci.yml` | every pull request and push to `main` | **static**: ktlint, detekt, Android lint · **unit**: unit + integration + contract tests, Kover report · **compose**: Robolectric Compose UI tests + Roborazzi verify (diffs uploaded as artifacts) · **build**: `assembleDebug` and `assembleRelease` with R8 | < 10 min |
| `e2e.yml` | pull requests touching `app/`, `core/`, `feature/`, `push/` or the workflow; push to `main`; nightly | **e2e**: emulator (Gradle Managed Device, KVM) + mock server container, end-to-end suite · then **smoke**: release APK on the same emulator | < 25 min |
| `e2e-real-server.yml` | nightly and on demand | **e2e (real server)**: the same suite against the petkit-local container (`dev` image), mock-only tests skipped | < 30 min |
| `release.yml` | tag `v*` | static + unit + compose + e2e + smoke must pass, **then** sign and attach the APK (and AAB if the Play Store is chosen) to a GitHub release | — |
| `contract-refresh.yml` | weekly and on demand | fetch `openapi.yaml` from the latest petkit-local release; if it changed, open a pull request so the contract tests run against it | — |

- **The nightly takes the server as it is now**: the floating `dev` tag of petkit-local's latest
  published main, not a pin, so a drift between the app, the mock and the server shows up the
  next morning rather than at the next release. Each run prints the image's digest and the commit
  it was built from (also in the job summary) and seeds it with that commit's smoke test, so a red
  run says which server build it met. The contract itself moves through `contract-refresh.yml`.
- **Supply chain**: actions pinned by SHA; Dependabot for Gradle (version catalog), GitHub
  Actions and the mock image's base images (`.github/dependabot.yml`); dependency verification
  (`gradle/verification-metadata.xml`) so Gradle refuses an artifact whose checksum changed.
- **Required checks** on `main`: static, unit, compose, build, e2e, smoke.

## 8. ADRs to write at project start

1. Modules and dependency rule (this document, section 2).
2. Room as the single source of truth, with no optimistic UI for device commands.
3. Hand-written DTOs plus OpenAPI contract tests, rather than generated clients.
4. Session token storage (Keystore-encrypted DataStore).
5. Video transport: fMP4 over WebSocket first, HLS fallback, via Media3.
6. Push through UnifiedPush and ntfy, no Firebase (superseded by 7).
7. Notifications over the app's own connection to petkit-local.
