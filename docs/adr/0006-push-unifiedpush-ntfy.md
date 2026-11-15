# ADR 0006: Push through UnifiedPush and ntfy, without Firebase

Status: accepted (M0; implemented in M5)

## Context

The app is distributed on GitHub only and talks only to the user's own server. Firebase Cloud
Messaging would need a Google project, a server key on petkit-local and Google Play services on
the phone, and would route every notification through Google.

## Decision

- The `:push` module uses the UnifiedPush connector. The recommended distributor is the ntfy
  app; any UnifiedPush distributor works.
- After sign-in, and whenever the distributor hands it a new endpoint, the app registers the
  endpoint with the server (`PUT /app/v1/push`, tied to the session: a server change, SPEC.md
  decision 4). Logout, revocation or expiry of the session removes it.
- The server posts a small JSON message (kind, device id, pet id, event code, time). The app
  writes the notification text itself, in the phone's language, on one channel per kind.

## Consequences

- Works on phones without Google services, and nothing goes through Google.
- The user installs a distributor (ntfy) once; Settings says so when none is found.
- Needs the server-side endpoint before M5 (ROADMAP.md, server follow-ups).
