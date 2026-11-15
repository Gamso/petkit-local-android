# Roadmap

Milestones in delivery order. Each line becomes a GitHub issue; each milestone ends with a usable,
tested build.

## M0 — Foundations
- Create the Gradle project:
  - modules as in ARCHITECTURE.md;
  - convention plugins;
  - version catalog;
  - minimum and target SDK.
- CI (ARCHITECTURE.md, section 7), built before the first feature so every feature lands tested:
  - `ci.yml`: static analysis, unit + integration + contract tests, Robolectric Compose UI and
    Roborazzi screenshot tests, debug and release (R8) builds;
  - `e2e.yml`: Gradle Managed Device emulator + petkit-local container (pinned image in
    `e2e/docker-compose.yml`, seeded with fake devices), end-to-end suite, then the smoke test on
    the release APK;
  - `release.yml` gated on all of them; `contract-refresh.yml` for the server's `openapi.yaml`;
  - required checks on `main`; Dependabot (already configured) for Gradle, actions and the
    server image; actions pinned by SHA; Gradle dependency verification.
- One placeholder test of each kind (unit, integration, contract, Compose UI, screenshot, e2e,
  smoke), so each job proves it runs before real tests arrive.
- Design system:
  - tokens (light and dark);
  - bundled fonts;
  - base components;
  - screenshot-test setup.
- `core:network`:
  - DTOs for every `/app/v1` schema;
  - contract tests on the OpenAPI examples;
  - auth interceptor;
  - error mapping.
- ADRs 1–6.

## M1 — Sign in and see the devices
- Sign-in, with:
  - address normalisation;
  - `/info` probe;
  - every login error;
  - rate-limit countdown.
- Encrypted session storage; sign-out; session-ended routing (401, `4401`).
- Home: device grid from `GET /devices`, with pull to refresh and the offline banner.
- Room cache for devices.
- English and French resources; in-app language switch.

## M2 — Device pages and actions
- Litter box screen:
  - hero with hotspots;
  - mode chips;
  - today tiles;
  - 24 h chart;
  - action sheet with confirmation.
- Feeder screen:
  - feed now;
  - feeding plan read, edit and pause;
  - one-off feeds.
- Generic device screen; device settings from settable entities.
- Live updates over `/ws`, patching the cache.

## M3 — History and pets
- History:
  - day strip and filters;
  - cursor paging into Room;
  - detail sheet;
  - photo viewer;
  - clip player (Range requests).
- Pets: list, detail, weight chart, recent visits; pets row on Home.

## M4 — Video
- Live player:
  - fMP4 over WebSocket through Media3;
  - HLS fallback;
  - delay badge, fullscreen, snapshot.
- Recent clips.

## M5 — Notifications and release
- Notifications all in one (ADR 0007): the foreground service holding the server's notification
  socket, catch-up on reconnection, battery-optimisation exemption, start at boot, notification
  channels per kind, deep links.
- Settings screen complete:
  - server info;
  - notifications: on/off, connection state, kinds;
  - theme;
  - change password.
- End-to-end test against a petkit-local container in CI.
- Signed APK attached to a GitHub release on each `v*` tag (spec, decision 2); About screen with
  the non-affiliation notice.

## Server-side follow-ups (petkit-local)
- **Needed before M5**: notifications over the app API's own WebSocket, with per-phone kinds and
  catch-up after a disconnection (petkit-local issue 89; spec, decision 4).
- Done: UnifiedPush endpoint registration per session (`PUT/DELETE /app/v1/push`). The app does
  not use it in v1; it stays for people who already run ntfy.
- The mock server gains the notification channel once the server's contract has it.
- Stable keys or translations for event labels, if keying them in the app proves brittle.
