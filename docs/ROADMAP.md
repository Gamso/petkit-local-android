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
- UnifiedPush registration, notification channels per kind, deep links.
- Settings screen complete:
  - server info;
  - notification kinds;
  - theme;
  - change password.
- End-to-end test against a petkit-local container in CI.
- Signed release on tag; distribution channel decided (spec, open question 2).

## Server-side follow-ups (petkit-local)
- **Needed before M5**: `PUT/DELETE /app/v1/push` to register the app's UnifiedPush endpoint per
  session, and the notifier posting structured JSON to every registered endpoint (spec, decision 4).
- Stable keys or translations for event labels, if keying them in the app proves brittle.
