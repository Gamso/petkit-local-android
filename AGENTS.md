# petkit-local-android

The Android app for petkit-local: it talks only to the user's own server through `/app/v1`
(contract vendored in `api/openapi.yaml`), never to PetKit's cloud or Home Assistant. Read
`docs/SPEC.md` (what), `docs/ARCHITECTURE.md` (how: MVVM + Clean Architecture, modules, tests, CI),
`docs/DESIGN.md` (tokens, components, icons) and `docs/adr/` before changing anything. The README
says how to build, test and run the mock server.

These rules are binding for every contributor, human or agent.

## Architecture and build

- **Dependency rule**: `feature:*` depends on `core:domain`, `core:designsystem`, `core:ui` only;
  never on `core:data`, `core:network` or `core:database`. `checkModuleRules` (part of `check`)
  enforces it.
- **Room is the single source of truth, no optimistic UI** (ADR 0002): a command updates the cache
  only with what the server answered; the device's next report does the rest.
- **No database migrations before the first release — but the version still goes up.** Room knows
  a schema only by its version: a changed schema under the same number makes an installed app crash
  on start. So until the first release, every schema change raises `PetkitDatabase.VERSION` (and
  `OLDEST_SUPPORTED_VERSION` with it), keeps only the current exported schema, and writes no
  migration; `fallbackToDestructiveMigration` rebuilds an older dev database (it is only a cache,
  refetched from the server). Never add `fallbackToDestructiveMigrationOnDowngrade` next to it: it
  turns required migrations back on. From the first release on, every schema change raises the
  version with a tested migration, and the fallback goes.
- **Build JDK 25, app bytecode 17, minSdk 29, target/compile 37.** Robolectric runs on SDK 37;
  screenshot tests on SDK 36 (SDK 37 renders blank images).
- **Strict Gradle dependency verification**: a new dependency needs its entries in
  `gradle/verification-metadata.xml`, generated from an empty Gradle cache.
- **Every string in English and French**; server labels are translated by stable key
  (`ServerLabels`); `checkTranslations` fails on a missing French string. The app follows the
  phone's language: no in-app language switch.
- **Entity and action keys come from the server**; capabilities are read from the API
  (`DeviceDetail.actions`, entity `settable`), never hard-coded per model — except tables of
  physical parts per model (for example `LitterParts`), which cite their source in petkit-local.

## UI

- **Material Symbols first** (Rounded, weight 400, unfilled), added to `PkIcons` as path data from
  `google/material-design-icons` at the commit recorded there. Draw an icon only when no symbol fits.
- **The way back is `PkBackButton`**; icon-only controls are `PkIconButton` (48 dp, round) with a
  TalkBack label; the way to a device's settings is the header gear only.
- **Never grey out a button for missing input**: keep it enabled and say what is missing where it
  is missing. Disable only while a request is in flight or when the server says to wait.
- **No awkward empty bands** in layouts; content that may not fit scrolls from the top.
- **Show only what the device has**: no hotspot, pill or action for a part the model lacks; "missing"
  only when the device reports the part absent, never because a value is null.
- **No PetKit image, logo, colour or product photo**, ever: illustrations are our own drawings, in
  their own composable so a user photo can replace them.
- **The app follows the phone's theme**: light or dark as the system is, no in-app theme switch.
- **Destructive actions are confirmed** (`PkConfirmDialog`) and every command reports how it was
  delivered (`mqtt`, `queued`, `local`) or the server's error.

## Tests: what to run, and when

The full verification is slow (around 700 tests, a hundred screenshots, an emulator). Run it once.

| When | Run |
|---|---|
| While developing | Only the unit tests of the modules you touch, without screenshot tests and without the emulator, e.g. `./gradlew :feature:device:testDebugUnitTest --tests '*ViewModelTest'` |
| To look at a screen | Install the debug APK on an emulator and look; do not rerun the suite |
| Once, when the work is finished | `./gradlew assembleDebug assembleRelease check :app:assembleDebugAndroidTest`, goldens recorded then verified, the managed-device tests (`:app:pixel6Api37DebugAndroidTest`) against the mock, the smoke test; `cd mock-server && ./gradlew check` if the mock changed |
| After review feedback | The unit tests of the touched module, re-record only the goldens of the screens that changed, then the full verification once more at the end |
| Rewriting a branch's history | Verify the last commit only (its tree must equal the one already verified, `git diff --quiet`); intermediate commits are not rebuilt one by one |

GitHub CI runs everything on every pull request: it is the safety net, not a reason to skip the
final local run.

## Review of visual changes

A pull request that changes the UI is shown to the owner **once**, when it is otherwise finished:
the debug APK on an emulator against the mock, plus the relevant goldens. The owner's remarks for
that pull request are collected in **one pass** and applied together, followed by one final
verification. Do not iterate screen by screen with a full verification after each remark.

## Commits and pull requests

- Small logical commits from the start, English, a sentence-like subject and a body saying why.
  No "WIP", no fix-ups of the branch's own earlier commits in the final history: amend locally
  before pushing, or rewrite the branch before review (then push with `--force-with-lease` only
  with the owner's go-ahead).
- Bring `main` into a branch with a normal merge or, before review, a rebase; never rewrite `main`.
- Merge pull requests with "Create a merge commit" so the commits stay in `main`.
- One Android branch at a time when branches touch the same modules (device pages, the shared
  action sheet): parallel branches there cost a full integration cycle afterwards.

## Running things locally

- The mock server (`mock-server/`, password `demo`) runs on port 8097 by default; the emulator
  reaches the host as `10.0.2.2`. `TestServerRule` resets the server it points at, so run your own
  mock on another port (`./gradlew run --args="--port=<port>"`) and pass
  `-Pandroid.testInstrumentationRunnerArguments.petkitServerUrl=http://10.0.2.2:<port>` when someone
  else is using 8097.
- The real server for the nightly run: `scripts/real-server.sh up` (petkit-local's `dev` image on
  8297, seeded, password set; it prints the instrumentation arguments), `down` when done. A test
  that needs the mock's control API or its seeded household is `@MockOnly` (core:e2e) and is
  skipped there; any other test must pass against both, reading what the server has.
- On Android 17, reaching a private address needs `ACCESS_LOCAL_NETWORK`: tests grant it
  (`TestServerRule`), the app asks for it at sign-in.
- Stop what you start (emulators, mock servers, the real server). Never `./gradlew --stop` on a shared machine: it
  stops every build using the shared Gradle home.
