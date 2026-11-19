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
- [docs/RELEASING.md](docs/RELEASING.md) — versions, the release key, cutting a release.

Languages: English and French.

## Installing and updating

The app is not on Google Play. Each [release](https://github.com/Gamso/petkit-local-android/releases)
carries a signed APK, `petkit-local-<version>.apk`. It needs Android 10 or later.

1. On the phone, open the latest release and download `petkit-local-<version>.apk`.
2. Open the download. The first time, Android asks to allow the app you opened it from (the
   browser or the file manager) to install unknown apps: allow it (Settings, Apps, Special app
   access, Install unknown apps), go back, and install.
3. Open the app and sign in to your petkit-local server.

**Updating**: download the newer APK and install it the same way, over the installed app. It keeps
its data (server, sign-in, settings) because every release is signed with the same key. Android
refuses an APK signed with another key, such as a build of your own or a CI build: the installed app
would have to be uninstalled first, which deletes its data. To hear of new releases, watch the
repository (Watch, Custom, Releases).

**Checking a download**: next to each APK is its `.sha256`, and the release notes give the APK's
SHA-256 and the signing certificate's. The certificate is the same for every release; an APK
signed with another one is not from this project.

```sh
sha256sum -c petkit-local-0.1.0.apk.sha256               # Windows: certutil -hashfile petkit-local-0.1.0.apk SHA256
apksigner verify --print-certs petkit-local-0.1.0.apk    # Android SDK build-tools: "certificate SHA-256 digest"
```

## Building

Needs JDK 25 and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`). Gradle
runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`) and finds it through `JAVA_HOME` or the
usual install locations; Android Studio's bundled JBR 25 works. The app itself is compiled to
Java 17 bytecode. Gradle installs the SDK platform it needs if it is missing. All versions are pinned in
`gradle/libs.versions.toml`, and every downloaded artifact is checked against
`gradle/verification-metadata.xml`.

```sh
./gradlew assembleDebug                  # app/build/outputs/apk/debug/
./gradlew assembleRelease                # R8; signed with the debug key, version 0.1.0-dev
./gradlew assembleDebug assembleRelease check   # everything CI checks
```

`check` runs ktlint, detekt, Android lint, the module dependency rule (`checkModuleRules`), the
French translation check (`checkTranslations`), all host tests and the screenshot comparison.
`./gradlew ktlintFormat` fixes formatting.

Every build but a release is version `0.1.0-dev`, code 1. A release takes its version from its tag
(`-PappVersion=0.1.0` for `v0.1.0`, code 100) and is built, signed and published by
`.github/workflows/release.yml`: see [docs/RELEASING.md](docs/RELEASING.md).

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
`10.0.2.2`. CI runs both in `.github/workflows/e2e.yml`, and the end-to-end tests again every
night against the real petkit-local (`.github/workflows/e2e-real-server.yml`, below).

Plain HTTP is accepted for a private LAN address only, a rule the app applies itself before any
request (`ServerAddresses`, core:domain); off the LAN it is HTTPS. On Android 17 and later,
reaching a private address also needs the `ACCESS_LOCAL_NETWORK` permission: the tests grant it
(`TestServerRule`), and the app asks for it at sign-in when the server is on the LAN.

**End to end** (`app/src/androidTest`): the instrumented tests, run by Gradle on a managed
emulator that it downloads, creates and boots by itself (hardware acceleration needed: KVM on
Linux, WHPX or the Android Emulator hypervisor driver on Windows).

```sh
docker compose -f e2e/docker-compose.yml up --build   # the mock on 8097 (and HTTPS on 8443); leave it running
./gradlew :app:pixel6Api37DebugAndroidTest            # API 37, Automated Test Device image, as CI
./gradlew :app:pixel6Api29DebugAndroidTest            # API 29, the minimum SDK (nightly in CI)
./gradlew :app:connectedDebugAndroidTest              # or on an emulator or phone you started
```

Reports: `app/build/reports/androidTests/`. The tests look for the mock at
`http://10.0.2.2:8097`; point them elsewhere with
`-Pandroid.testInstrumentationRunnerArguments.petkitServerUrl=http://10.0.2.2:<port>` (the older
`petkitMockUrl` still works). The certificate-trust tests use the mock's HTTPS port,
`https://10.0.2.2:8443` (`petkitServerTlsUrl` to change it), whose self-signed certificate the
mock makes at start. Shared helpers for these tests (the server's address and password,
the mock's control API, a signed-in API client, a rule that resets the server before each test)
are in `core/e2e`; a module uses them with `androidTestImplementation(projects.core.e2e)`.

**Against the real server**: `scripts/real-server.sh` starts petkit-local itself, the floating
`ghcr.io/gamso/petkit-local:dev` image of its latest main, without Home Assistant (service
`petkit-local` of `e2e/docker-compose.yml`). It says which build it got (digest and commit),
seeds it with the fake T5 litter box of the server's own smoke test taken at that commit, sets
the app password and prints the arguments for the tests. Its panel is published on the host's
loopback only, for the tests to do what the panel's user does (reset the password, revoke
sessions).

```sh
scripts/real-server.sh up                             # app API on 8297, panel on 127.0.0.1:8299
./gradlew :app:pixel6Api37DebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.petkitServerKind=real   -Pandroid.testInstrumentationRunnerArguments.petkitServerUrl=http://10.0.2.2:8297   -Pandroid.testInstrumentationRunnerArguments.petkitServerPanelUrl=http://10.0.2.2:8299   -Pandroid.testInstrumentationRunnerArguments.petkitServerPassword=petkit-e2e-password
scripts/real-server.sh down
```

A test that drives the mock's control API (`/mock`) or relies on its seeded household is
annotated `@MockOnly`, and is reported as skipped against the real server; every other test runs
against both, reading what the server has from the server. A test that fails there and not
against the mock shows a drift between the app, the mock and the server.

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

This project is not affiliated with, endorsed by or connected to PetKit. "PetKit" is a trademark
of its owner and is used here only to say which devices the app works with.

## Licence

MIT, see [LICENSE](LICENSE). The product photos of the supported devices
(`core/designsystem/src/main/res/drawable-nodpi/device_photo_*.webp`) are PetKit's images,
© PetKit, and are **not** covered by the MIT licence: see [NOTICE](NOTICE).
