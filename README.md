# petkit-local Android

An Android app for [petkit-local](https://github.com/Gamso/petkit-local), the self-hosted
replacement for PetKit's cloud. It talks only to your own petkit-local server, through its
authenticated phone-app API (`/app/v1`): no PetKit cloud and no Home Assistant needed.

**Status: milestone M0 (foundations).** The project builds and every screen is a placeholder.
Start with:

- [docs/SPEC.md](docs/SPEC.md) — what v1 does, screen by screen.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — MVVM + Clean Architecture, modules, stack, tests, CI.
- [docs/DESIGN.md](docs/DESIGN.md) — design tokens and components.
- [docs/ROADMAP.md](docs/ROADMAP.md) — milestones M0–M5.
- [docs/adr/](docs/adr/) — the decisions that are hard to undo.

Languages: English and French.

## Building

Needs JDK 25 and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`). Gradle
runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`) and finds it through `JAVA_HOME` or the
usual install locations; Android Studio's bundled JBR 25 works. The app itself is compiled to
Java 17 bytecode. Gradle installs the SDK platform it needs if it is missing. All versions are pinned in
`gradle/libs.versions.toml`, and every downloaded artifact is checked against
`gradle/verification-metadata.xml`.

```sh
./gradlew assembleDebug                  # app/build/outputs/apk/debug/
./gradlew assembleRelease                # R8; signed with the debug key until release signing exists
./gradlew assembleDebug assembleRelease check   # everything CI checks
```

`check` runs ktlint, detekt, Android lint, the module dependency rule (`checkModuleRules`), the
French translation check (`checkTranslations`), all host tests and the screenshot comparison.
`./gradlew ktlintFormat` fixes formatting.

## Tests

```sh
./gradlew test                                       # every host test (JVM and Robolectric)
./gradlew test -Ppetkit.testEngines=junit-jupiter    # unit, integration and contract tests only
./gradlew testDebugUnitTest -Ppetkit.testEngines=junit-vintage -Proborazzi.test.verify=true
                                                     # Compose UI tests and screenshots only
./gradlew koverHtmlReport                            # coverage: build/reports/kover/html/
```

The contract tests (`core:network`) check the DTOs and the Retrofit routes against petkit-local's
OpenAPI document, vendored in `api/openapi.yaml`. Refresh it with
`scripts/update-openapi.sh <petkit-local tag or commit>`; the `contract-refresh` workflow does it
weekly from the latest release and opens a pull request.

## End-to-end and smoke tests

These run on an Android emulator, against the [mock server](mock-server/README.md) (a fake
petkit-local) started with Docker on your machine. The emulator reaches your machine as
`10.0.2.2`. CI runs both in `.github/workflows/e2e.yml`.

Plain HTTP is accepted for a private LAN address only, a rule the app applies itself before any
request (`ServerAddresses`, core:domain); off the LAN it is HTTPS. On Android 17 and later,
reaching a private address also needs the `ACCESS_LOCAL_NETWORK` permission: the tests grant it
(`MockServerRule`), and the app asks for it at sign-in when the server is on the LAN.

**End to end** (`app/src/androidTest`): the instrumented tests, run by Gradle on a managed
emulator that it downloads, creates and boots by itself (hardware acceleration needed: KVM on
Linux, WHPX or the Android Emulator hypervisor driver on Windows).

```sh
docker compose -f e2e/docker-compose.yml up --build   # the mock on port 8097; leave it running
./gradlew :app:pixel6Api37DebugAndroidTest            # API 37, Automated Test Device image, as CI
./gradlew :app:pixel6Api29DebugAndroidTest            # API 29, the minimum SDK (nightly in CI)
./gradlew :app:connectedDebugAndroidTest              # or on an emulator or phone you started
```

Reports: `app/build/reports/androidTests/`. The tests look for the mock at
`http://10.0.2.2:8097`; point them elsewhere with
`-Pandroid.testInstrumentationRunnerArguments.petkitMockUrl=http://10.0.2.2:<port>`. Shared
helpers for these tests (the mock's address and control API, a signed-in API client, a rule that
resets the mock before each test) are in `core/e2e`; a module uses them with
`androidTestImplementation(projects.core.e2e)`.

**Smoke** (`scripts/smoke-test.sh`): the release build, minified by R8, on a running emulator. It
installs, starts and shows its first screen, with no crash and no ANR in the log. It writes the
log, a screenshot and the UI hierarchy to `build/smoke/`.

```sh
./gradlew assembleRelease                 # signed with the debug key unless signing is injected
scripts/smoke-test.sh app/build/outputs/apk/release/app-release.apk
```

CI signs that APK with its own key, passed to Gradle as Android Studio does
(`-Pandroid.injected.signing.store.file=...`, see the workflow); without the key it makes a
throwaway one.

## Languages

Every string the user sees exists in English (`values/`) and French (`values-fr/`), plurals
included; `checkTranslations` fails the build on a string missing from either side. The labels
the server sends in English (entity and action names, select options, event kinds) are
translated by their stable keys in `core/ui` (`ServerLabels`, `server_labels.xml`); a key the
app does not know shows the server's own text. The app follows the phone's language and has no
switch of its own; from Android 13, Android's own per-app setting can still pick the other one.

## Screenshots

Every design-system component is rendered by Roborazzi on Robolectric in light/dark × English/
French × 100 %/130 % font scale, and every screen in light/dark × English/French. The golden
images are committed in each module's `src/test/screenshots/`; `check` fails when a rendering differs, and CI uploads
the diffs. After an intended visual change, record new ones and commit them:

```sh
./gradlew recordRoborazziDebug           # rewrites src/test/screenshots/
./gradlew compareRoborazziDebug          # or: keep the goldens, write diffs to build/outputs/roborazzi/
```

Robolectric renders the same pixels on Linux and Windows here, so they can be recorded on
either. The screenshots render on SDK 36 rather than the target SDK 37, whose Robolectric
graphics still capture some images blank (see `ComponentScreenshotTest`); every other
Robolectric test runs on SDK 37.

Distributed as signed APKs on [GitHub releases](https://github.com/Gamso/petkit-local-android/releases).
Requires Android 10 or later.

This project is not affiliated with, endorsed by or connected to PetKit. "PetKit" is a trademark
of its owner and is used here only to say which devices the app works with.

## Licence

MIT, see [LICENSE](LICENSE).
