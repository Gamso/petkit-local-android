# Roadmap

Milestones in delivery order. Each line becomes a GitHub issue; each milestone ends with a usable,
tested build.

## M0 — Foundations
- Create the Gradle project:
  - modules as in ARCHITECTURE.md;
  - convention plugins;
  - version catalog;
  - minimum and target SDK.
- CI: ktlint, detekt, lint, unit tests, `assembleDebug`; Dependabot; actions pinned by SHA.
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
- A route for the app to register its UnifiedPush endpoint, so the topic need not be pasted into
  the panel.
- Stable keys or translations for event labels, if keying them in the app proves brittle.
