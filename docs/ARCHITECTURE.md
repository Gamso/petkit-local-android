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
| Live updates | OkHttp WebSocket | Same client, same auth interceptor |
| Local cache | Room | Last known devices, pets and timeline pages for offline display |
| Preferences | DataStore | Server address, language, theme, notification kinds |
| Secret storage | Android Keystore (AES-GCM key) encrypting the session token in DataStore | No deprecated crypto library |
| Images | Coil 3 with the shared OkHttp client | Media and thumbnails need the `Authorization` header |
| Video | Media3 ExoPlayer: HLS natively; fMP4-over-WebSocket through a custom `DataSource` | One player, two transports |
| Charts | Compose `Canvas` | Few, simple charts; no charting dependency |
| Push | UnifiedPush connector (ntfy distributor) | No Google services required |
| Navigation | Navigation Compose with type-safe routes | Standard, testable |
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
:feature:auth             Sign-in, session expired, change password
:feature:home             Home: device grid, pets row, alert banners
:feature:device           Litter box, feeder, generic device, device settings
:feature:history          Day strip, filters, timeline, event detail, media viewer
:feature:pets             Pet list and detail, weight chart
:feature:live             Live video player
:feature:settings         Server, notifications, language, theme, sign out
:push                     UnifiedPush receiver and notification channels
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

## 5. Push notifications

- **Registration**: the `:push` module registers with UnifiedPush and stores its endpoint. Today the user copies the
  topic into the panel; a server route can automate it later (spec, open question 3).
- **Delivery**: the receiver turns each message into a notification on the channel for its kind (title, body,
  tags from ntfy). Tapping it opens the device or History deep link.

## 6. Testing

| Layer | What | Tools |
|---|---|---|
| Domain | Every use case and rule | JUnit 5, Turbine, fakes from `core:testing` |
| Data | Mappers, repositories against a fake server (MockWebServer), Room DAOs, WebSocket message handling, DTO contract tests | JUnit 5, MockWebServer, Robolectric for Room |
| Presentation | ViewModels with fake repositories (state sequences, effects) | Turbine, coroutines-test |
| UI | Screens in both languages and both themes | Compose UI tests, screenshot tests |
| End to end | Sign in, home, run an action, history against a petkit-local container | Instrumented test on CI emulator, petkit-local image started with `--no-ha` |

Coverage targets: domain 90 %, data 80 %, ViewModels 80 %.

## 7. CI (GitHub Actions)

- **Every pull request**: ktlint, detekt, Android lint, unit tests, screenshot tests,
  `assembleDebug`.
- **Main**: the same, plus the end-to-end test against the current `ghcr.io/gamso/petkit-local` image.
- **Tag `v*`**: signed release APK (and AAB if the Play Store is chosen) attached to a GitHub
  release.
- **Supply chain**: actions pinned by SHA and Dependabot for Gradle and actions, as on the server.

## 8. ADRs to write at project start

1. Modules and dependency rule (this document, section 2).
2. Room as the single source of truth, with no optimistic UI for device commands.
3. Hand-written DTOs plus OpenAPI contract tests, rather than generated clients.
4. Session token storage (Keystore-encrypted DataStore).
5. Video transport: fMP4 over WebSocket first, HLS fallback, via Media3.
6. Push through UnifiedPush and ntfy, no Firebase.
