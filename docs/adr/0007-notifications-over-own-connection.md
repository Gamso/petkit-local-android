# ADR 0007: Notifications over the app's own connection to petkit-local

Status: accepted (2026-11-15; implemented in M5). Supersedes ADR 0006.

## Context

ADR 0006 chose UnifiedPush with the ntfy app as distributor. In practice that needs three things
besides petkit-local and this app: a self-hosted ntfy server exposed through the user's tunnel,
the ntfy app installed and pointed at it, and, when ntfy is closed by default, an access rule for
UnifiedPush topics. The owner wants an all-in-one setup: petkit-local and Petkit Local, nothing
else.

The ntfy app itself is a foreground service holding a long-lived connection to its server.
petkit-local already authenticates each phone and already speaks WebSocket, so the app can hold
that connection itself, at the same battery cost, with nothing in between.

## Decision

- The server gets a notification-only WebSocket on the app API (petkit-local issue 89):
  structured messages (kind, device, pet, code, time) with an id, a replay of what a phone missed
  since the id it passes back, per-phone kinds, pings inside Cloudflare's idle timeout, and the
  app API's close codes (`4401` when the session ends).
- The `:push` module runs a foreground service holding that socket while the user is signed in and
  notifications are on. Service type `specialUse`: the app is distributed on GitHub, so no store
  review applies, and Android 14+ does not time-limit it as it does `dataSync`. It starts after
  sign-in, at boot and after an app update, reconnects with backoff and as soon as the network
  returns, and stops on `4401`.
- The app asks to be exempted from battery optimisation, with an explanation, as ntfy does.
- The connection's ongoing notification, which Android requires, sits on its own
  minimum-importance channel; alerts use one channel per kind, with text written by the app in
  the phone's language.
- UnifiedPush is not used by the app in v1. The server keeps it (endpoint registration per
  session, and the ntfy topic setting) for people who already run ntfy; adding it to the app later
  as an alternative stays possible, since both channels carry the same message.

## Consequences

- No ntfy server, no second app, no Google services: notifications work as soon as the user is
  signed in.
- An ongoing "connected" notification is always present; the user can hide its channel.
- Battery use is one idle WebSocket kept alive by pings, about what the ntfy app costs.
- Delivery depends on the battery-optimisation exemption; Settings shows its state and how to fix
  it. Some vendor ROMs kill background services regardless; that is a documented limitation.
- Alerts missed while the phone was offline arrive on reconnection, within the server's replay
  bounds.
- Needs the server change before M5, and the mock server must implement the channel for the e2e
  tests.
