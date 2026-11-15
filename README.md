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

`check` runs ktlint, detekt, Android lint, the module dependency rule (`checkModuleRules`), all
host tests and the screenshot comparison. `./gradlew ktlintFormat` fixes formatting.

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

## Screenshots

Every design-system component is rendered by Roborazzi on Robolectric in light/dark × English/
French × 100 %/130 % font scale. The golden images are committed in
`core/designsystem/src/test/screenshots/`; `check` fails when a rendering differs, and CI uploads
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
