# Product specification — v1

The Android client for [petkit-local](https://github.com/Gamso/petkit-local): it talks only to
the user's own petkit-local server, through the authenticated phone-app API (`/app/v1`), and
never to PetKit's cloud or to Home Assistant.

Mockups: the design canvas "Android app mockups" (eight screens, French copy). Screens below are
numbered as on the canvas.

## 1. Goals

- Give a household the day-to-day experience of PetKit's own app — see each device at a glance,
  act on it, follow what the pets did — against a server they run themselves.
- Work from anywhere: the server is reached through the user's own domain (Cloudflare tunnel,
  reverse proxy). No VPN.
- Two languages from day one: English and French, following the phone's language.

## 2. Non-goals for v1

- Adding a device, Wi-Fi/Bluetooth provisioning, firmware patching or flashing — the panel does
  that, on the LAN.
- Anything Home Assistant does: automations, dashboards, other brands.
- Two-way talk from the camera (needs WebRTC through a TURN relay; see section 9).
- Several servers or several accounts in one app.
- iOS.

## 3. Users and access

- **One account per server.** The server holds one password (issue 71); every phone signs in
  with it and receives its own session token. There are no user roles.
- A session dies after **90 days without use**, on logout, when revoked from the panel, or when the
  password changes. The app then returns to sign-in and says why.
- The server can be anywhere the phone can reach over **HTTPS**. Plain HTTP is accepted only for a
  private LAN address, with a warning.

## 4. Devices in scope

The API exposes every device the server knows. The app renders three families with a dedicated
screen and everything else with a generic one.

| Family | Models | v1 screen |
|---|---|---|
| Litter box | Pura Max (T4, confirmed on hardware), Pura X (T3), Purobot Max Pro 2 (T5), Purobot Ultra (T6), Crystal Duo (T7) | Dedicated (3) |
| Feeder | Fresh Element Solo (D4), Gemini (D4S), YumShare Solo (D4H), YumShare Dual-Hopper (D4SH), older feeders | Dedicated (6) |
| Fountain | EverSweet Ultra AI (W7H, Wi-Fi, confirmed on hardware), EverSweet 3 Pro (W5), EverSweet / W4X (W4), Solo 2 (CTW2), Max Cordless (CTW3): the last four Bluetooth-only, relayed by a litter box or feeder | Dedicated (9) |
| Others | Pura Air (K2/K3) | Generic: status, entities, actions |

Capabilities are read from the API (`DeviceDetail.actions`, entity `settable`, `is_camera`),
never hard-coded per model, except for layout choices (which entity goes where on a dedicated
screen).

## 5. Screens

### 1 · Sign-in
- Fields: server address, password, this phone's name (prefilled with the phone model).
- On submit: normalise the address, call `GET /info`, then `POST /auth/login`.
- Errors in place:
  - not a petkit-local server;
  - no password set on the server, which tells the user to set it in the panel's "Phone app access" card;
  - wrong password;
  - locked out for N seconds (HTTP 429 `Retry-After`);
  - unreachable server;
  - local network access refused. From Android 17, reaching a server on the LAN needs the
    `ACCESS_LOCAL_NETWORK` runtime permission; without it packets are silently dropped. The app
    asks for it, with its reason, before probing a LAN address.

### 2 · Home
- Title (the household; single, not switchable in v1), notification bell, alert banners for
  anything needing attention (consumable, error, offline device).
- While notifications are on but Android 13+ does not let them show, a banner above the alerts
  says so, with "Allow" (Android's prompt, or its notification settings for the app once refused
  for good) and "Not now", which puts it away until the next sign-in; Settings keeps its prompt.
- **Device grid**, two columns, one card per device:
  - name and small device glyph;
  - the family's headline figure: visits today for a litter box, portions served for a feeder, water level for a fountain;
  - a 24-hour strip of the day's events;
  - a status band at the bottom: idle, busy, offline, error.
- **Pets** row: avatar, weight, visits today.
- Pull to refresh. Live updates while open.

### 3 · Litter box
- Header: name, settings shortcut, **mode chips** (auto clean, auto deodorize — from switches).
- **Hero**: a device illustration with **hotspot pills** on its parts:
  - litter level;
  - waste bin;
  - deodorizer liquid or N50;
  - any other consumable the model reports.

  Each pill opens the matching setting or explanation, and its colour follows its state.
- Live status line: idle, cleaning, pet inside, error with its label.
- **Today** card: visits and average duration, each with the change since yesterday as a neutral
  arrow and the amount ("↑ 2 since yesterday"); nothing when yesterday has no visit to compare with.
- **24-hour chart**: one stem per visit, from the axis at its time up to its duration, capped with a
  dot coloured per pet, with a pet filter.
- **Activity**: the day's timeline for this device, linking to History. One row per visit with
  the pet's photo (its initial on its colour without one, a paw for a pet the box could not tell)
  and a sentence ("Mochi spent 1:35 · 4.3 kg"); under it, the steps the server grouped with the
  visit (cleaning done, deodorizing done, a failed one with its reason). A pet seen and a
  cleaning on its own are single rows with their symbol. The feeder and fountain pages list their
  meals and drinks the same way.
- **Bottom action sheet**: the model's actions (clean, level, deodorize…), from
  `DeviceDetail.actions`. Destructive ones ask for confirmation and send `confirm: true`.
- Settings (gear): every settable entity, grouped, with the type-appropriate control.

### 4 · History
- Week strip to pick a local day (arrows to the weeks before, never past today).
- Filter chips: all, then the server's own, single-select (`kind` of `GET /timeline`): pet,
  toileting (visits), drinking, feeding (meals), cleaning, health alert, fault (alerts). A device
  picker and a pet picker after them.
- Opened from a device's page, History shows that device; from Home, every device.
- Timeline of cards, newest first, cursor-paginated (`GET /timeline`) into the cache, each with
  time, pet, device, weight, duration and media thumbnails. Empty, loading, error and offline
  states as everywhere.
- A card opens a detail sheet: decoded fields (named by key; the others on demand), photos
  (full screen, swiped and zoomed), clips (played in-app, seekable).

### 5 · Pet
- Switch between pets at the top; avatar (`GET /pets/{id}/photo`), name, how it is recognised.
- Tiles: weight (median of the last weighed visits), visits today, last visit.
- Weight chart over 30 days / 6 months; recent visits linking to History.

### 6 · Feeder
- Header and hero as for the litter box (hotspots: food level, desiccant, hoppers on dual feeders).
- Next meal card with a **feed now** control (portions; per hopper on dual feeders).
- **Feeding plan**: today's meals with their state (served, skipped, upcoming), an active/paused
  switch, edit sheet for the weekly plan.
- One-off feeds ("later"): list, add, cancel.
- Bottom action sheet: feed, plan, later, history.

### 7 · Live video (camera models only)
- Full-width player, "live" badge and measured delay, fullscreen, mute, snapshot.
- Plays the fMP4-over-WebSocket stream (`/devices/{id}/live`); falls back to HLS automatically on
  close code `1013` or repeated failures.
- Recent clips below.
- "Talk" is shown disabled with an explanation (non-goal).

### 8 · Settings
- Server: address, server version.
- Notifications: on/off for this phone; while on, what Android must still allow (the notification
  permission from Android 13, the battery-optimisation exemption), each with the action that
  fixes it; and the kinds to show. Whether the connection is up is said by its own ongoing
  notification, not here.
- App: this phone's name. No language or theme setting: the app follows the phone's language
  and its light or dark theme.
- Change password (needs the current one; signs out every other phone), sign out.
- About: the app's version, the non-affiliation notice (decision 1), the app's own licence (MIT,
  the repository's LICENSE) and the open-source licences: one line per project the app is built
  on (all of AndroidX and Compose is one), with its licences and, when it has several components,
  their list with versions; the licence text on a tap. Only the direct dependencies of the
  release build are listed (what our modules declare, not what those bring along), plus the
  bundled fonts and icons. The list is generated at build time from Gradle's resolution (never
  kept by hand), shipped in the APK and read offline, with no Google service.

### 9 · Fountain
- Header and hero as for the litter box. Hotspots only for the parts the model has (a table per
  model, citing petkit-local): water level, filter and pump on the W5, W4 and CTW2, plus the
  battery on the CTW3; drinking tray, clean and waste water tanks and pump on the W7H. A value the
  fountain does not report draws no pill; "missing" or "low" only when it says so.
- Mode chips from what the model has and reports: smart mode, do not disturb, child lock.
- **Today**: on the W7H (camera, on-device recognition), drinks and their average duration
  against yesterday, each pet's drinks, and the 24-hour chart of the drinks with a pet filter. A
  Bluetooth fountain reports no drinks: its pump's running time today, as the fountain counts it
  (no yesterday to compare with), and a line saying it reports no drinks.
- A Bluetooth fountain says when it last reported and through which device ("Reports through
  ..."), which opens that device; a write to it needs that device on MQTT.
- Bottom action sheet from `DeviceDetail.actions` (refill, drain and flush, filter reset...);
  destructive ones (drain, deep clean, filter reset) are confirmed. A device page shows no sheet
  when its device has no everyday action and its pills offer every other one (a Bluetooth
  fountain's filter reset is on its filter's pill).

## 6. Cross-cutting behaviour

- **Live updates.** While a screen that shows devices is visible, the app keeps one `GET /ws`
  socket open:
  - the `hello` message replaces local state; `state`, `device`, `device_removed` and `timeline` messages patch it;
  - on close, it reconnects with backoff, except on code `4401`, which ends the session;
  - it is closed in the background, so no battery is spent.
- **Offline and errors.**
  - Last known data stays visible, with a banner when the server is unreachable.
  - Device cards show "offline" from the API's `online` flag.
  - Every action reports success (`delivered`: mqtt, queued or local) or the server's error message.
  - A command queued for a device that polls over HTTP says it will arrive within ~10 s.
- **Notifications.** The app keeps its own connection to the server for them (section 9,
  decision 4): no other app or server is needed.
  - The server sends structured messages only; the app writes the notification text in the
    phone's language.
  - One Android notification channel per kind (visit, meal, consumable/error, offline), so the
    user can mute them in system settings too.
  - A low-importance ongoing notification, on its own channel, shows that the connection is up;
    Android requires it, and the user can hide that channel.
  - After a network loss or a reboot, the alerts missed meanwhile are delivered on reconnection.
- **Accessibility.** Contrast ≥ 4.5:1, touch targets ≥ 48 dp, TalkBack labels on every
  icon-only control, dynamic font size, no information carried by colour alone.
- **Dark theme** from the same tokens, following the phone's setting (no in-app switch).

## 7. Languages

- English and French string resources; plurals and number/date formats from the locale; weights in
  kg, durations in m:ss.
- The language is the phone's. The app has no switch of its own; from Android 13 the system's own
  per-app setting (Settings > Apps > Petkit Local > Language) can still give it the other one,
  because the app declares the two languages it ships (`locales_config.xml`).
- **Server-provided labels are English** (entity names, event labels). The app translates by
  **stable keys** (entity `key`, event kind and code) and falls back to the server's text for
  anything it does not know. A missing translation is a bug report, never a crash.

## 8. Security

- The session token is stored encrypted (Android Keystore); the password is never stored.
- HTTPS required off the LAN; no certificate pinning (the user's proxy or Cloudflare terminate TLS).
- The token travels only in the `Authorization` header, never in a URL (media and video included).
- Screenshots of the video screen are allowed (it is the user's own camera).

## 9. Decisions

1. **Name: "Petkit Local".** The app is distributed on GitHub only, not on a store. The README and
   the app's About screen state that it is not affiliated with PetKit, and no PetKit logo, image
   or colour is used. If it ever goes to a store, rename it then.
2. **Distribution: GitHub releases.**
   - Signed APKs are attached to each `v*` release; the signing key lives in GitHub Actions
     secrets.
   - Updates are installed by hand from the releases page.
3. **Minimum Android: 10 (API 29).** Nothing in the plan needs a higher floor.
   - Compose, Media3, Room, Keystore and foreground services all work there.
   - Notification permission is asked at runtime from Android 13.
   - Raise the floor only if a library drops 29.
4. **Notifications: all in one, over the app's own connection to petkit-local** (ADR 0007; needs
   petkit-local issue 89).
   - While the user is signed in and notifications are on, a foreground service keeps one
     WebSocket open to the server's notification channel. It is the only thing that runs in the
     background, and it carries nothing but notifications.
   - No ntfy server, no ntfy app, no Google services: petkit-local and this app are enough.
   - The server sends a small JSON message: kind, device id, pet, event code, time, and an id the
     app passes back on reconnection to get what it missed.
   - The app builds the notification text itself, in the phone's language.
   - Every signed-in phone can get notifications; logout, revocation, expiry or a password change
     closes the connection.
   - The app asks to be exempted from battery optimisation, as ntfy does, or alerts arrive late
     while the phone sleeps. Battery use is that of one idle WebSocket kept alive by pings.
   - The server also keeps UnifiedPush endpoint registration (`PUT /app/v1/push`) and its ntfy
     topic setting, for people who already run ntfy. The app does not use them in v1.
   - Changed on 2026-11-15: the first version of this decision used UnifiedPush with the ntfy app
     as distributor, which needed a self-hosted ntfy server and a second app on the phone.
5. **Two-way talk from outside** (WebRTC + TURN): a later version.
