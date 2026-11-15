# petkit-local mock server

A fake [petkit-local](https://github.com/Gamso/petkit-local) for developing and testing the app
without a server or real devices. It serves the whole phone-app API, `/app/v1`, as described by the
vendored contract `api/openapi.yaml`. Behind it is a fixed household: one device of every family,
two cats and a week of history, with a scenario engine that keeps things happening. A control API
under `/mock` lets tests trigger events, move the clock and force errors.

It is a standalone Gradle build (Kotlin, Ktor, JDK 25), with its own wrapper and version catalog.
It is not part of the app's build.

## Run it

With Gradle (JDK 25):

```sh
cd mock-server
./gradlew run                                   # http://localhost:8097/app/v1, password "demo"
./gradlew run --args="--port=9000 --scenario-interval=5"
```

With Docker, building from the **repo root** (the image needs `api/openapi.yaml`):

```sh
docker build -f mock-server/Dockerfile -t petkit-local-mock .
docker run --rm -p 8097:8097 petkit-local-mock
docker run --rm -p 8097:8097 -e PETKIT_MOCK_PASSWORD=s3cret -e PETKIT_MOCK_SCENARIO_INTERVAL=0 petkit-local-mock
```

Pointing the app at it:

- **Emulator:** `http://10.0.2.2:8097`. This is the emulator's address for the host.
- **Phone on the LAN:** `http://<computer's IP>:8097`. The app accepts plain HTTP only for a private
  LAN address, with a warning.
- Sign in with `demo`.

Try it with curl:

```sh
curl -s localhost:8097/app/v1/info
TOKEN=$(curl -s -X POST -H 'Content-Type: application/json' -d '{"password":"demo"}' \
  localhost:8097/app/v1/auth/login | jq -r .token)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8097/app/v1/devices | jq '.devices[].model'
curl -s -X POST -H "Authorization: Bearer $TOKEN" localhost:8097/app/v1/devices/1002/actions/cleaning_start
```

## Options

Every option can be set as a flag, `--name=value`, or as an environment variable,
`PETKIT_MOCK_NAME`. The flag wins.

| Option | Default | |
|---|---|---|
| `port` / `host` | `8097` / `0.0.0.0` | The real app API's port. |
| `password` | `demo` | Empty means no password is set: every `/app/v1` route answers 401 `password_not_set`. |
| `control` | `true` | Serves `/mock`. Set it to `false` to run the mock as a plain server. |
| `scenario-interval` | `20` | Seconds between two scenario events. `0` turns the engine off, including scheduled meals. |
| `seed` | `42` | Seeds the history and the scenario. The same seed replays the same week, relative to today. |
| `busy` | `6` | Seconds a device stays busy (cleaning, feeding...) after an action. |
| `visit` | `8` | Seconds a cat stays in the box during a live visit. |
| `video-delay` | `5` | Seconds before a visit's recording "arrives". |
| `lockout-base` / `lockout-max` | `1` / `900` | Login lockout: the first wait, doubled at each further wrong password, up to the max. `0` disables it. |
| `heartbeat` | `25` | Protocol ping interval on `/ws`, also sent as `hello.heartbeat_sec`. |
| `ws-queue-limit` | `256` | Messages waiting for one `/ws` client before it is closed with 1013. |
| `timezone` | `Europe/Paris` | Where days, meal times and one-off feeds are cut. |
| `server-version` | `mock-0.1.0` | The `server_version` in `/info`. |

## What it implements

Every operation in `api/openapi.yaml`, with petkit-local's own rules. These were read from its
`app_api/` at the commit recorded in `api/SOURCE`.

- **Access**
  - No password set: 401 `password_not_set` on everything, unknown paths included.
  - `/info` and `/openapi.yaml` are public.
  - Everything else needs a bearer token, including unknown paths, so a caller without one cannot
    map the API. With a token, an unknown path is a JSON 404 and a wrong method a 405 with `Allow`.
- **Sessions**
  - One per phone, from `POST /auth/login`.
  - Ended by logout (that session only), by a password change (needs the current password; ends
    every other session) or after 90 days unused. Each use extends it. The clock can be advanced.
- **Login throttle:** wrong passwords lock the client's address out for 1 s, 2 s, 4 s and so on,
  capped. Answers are 401 `invalid_password`, then 429 `too_many_attempts`, both with `Retry-After`.
- **Devices and entities**
  - Entity values, units, `settable`, `min`/`max`/`step` and `options` follow petkit-local.
  - Writes are typed by component and refused with 422 `refused`, `not_settable` or
    `confirmation_required` exactly where petkit-local refuses them.
  - `delivered` is `mqtt` for a device online on MQTT, `queued` otherwise, and `local` for
    petkit-local's own settings.
- **Feeding:** plan read, write and pause; one-off feeds with `invalid_schedule` / `invalid_feed`.
  Due meals and one-off feeds are served at their time.
- **Timeline**
  - Cards newest first, with the server's opaque `(ts, id)` cursor.
  - Filters: `device`, `day` (cut at local midnight), `kind` and `pet`.
  - Steps, `GET /timeline/{event_id}` for a card or any of its steps.
- **Media and pets**
  - Media are generated placeholder JPEGs and one bundled 9 KB synthetic MP4. Both support `Range`
    (206).
  - Thumbnails, pets, pet detail with statistics and recent visits, and a pet photo for the first cat.
- **`/ws`**
  - Messages: `hello` first; `state` with only the entities that changed; `device` when the summary
    changes; `device_removed`; and `timeline` with `replaces`.
  - A visit opens an in-progress card that its summary then replaces. Its recording and its cleaning
    cycle later change the same card.
  - `{"type":"ping"}` gets a `pong`.
  - Close codes: 4401 when the session ends (logout, revocation, password change or expiry), 1013
    when the client reads too slowly, 1001 on shutdown.
- **Live video** (M4) answers the documented refusals: 503 `live_unavailable` for a camera, 404
  `stream_not_found` for any other device.

## The seeded household

| Id | Type | Model | Kind | Transport | Notes |
|---|---|---|---|---|---|
| 1001 | t3 | Pura X | litter box | http | An error and its clearing three days ago. |
| 1002 | t4 | Pura Max | litter box | http | The owner's model; cleans every night at 03:00. |
| 1003 | t5 | Purobot Max Pro 2 | litter box | mqtt | Camera; also logs cats passing by. |
| 1004 | t6 | Purobot Ultra | litter box | mqtt | Camera, waste bags, PTZ. |
| 1005 | t7 | Purobot Crystal Duo | litter box | mqtt | Camera. |
| 2001 | d4 | Fresh Element Solo | feeder | http | 3 meals a day; desiccant nearly spent. |
| 2002 | d4s | Fresh Element Gemini | feeder | http | Dual hopper; plan paused for two days. |
| 2003 | d4h | YumShare Solo | feeder | mqtt | Camera; weekday and weekend plans; a one-off feed tomorrow. |
| 2004 | d4sh | YumShare Dual-Hopper | feeder | mqtt | Camera; hopper 2 empty. |
| 3001 | w7h | EverSweet Ultra AI | fountain | mqtt | Camera; recognises the cats' drinks. |
| 3002 | w5 | EverSweet 3 Pro | fountain | http | Offline for three hours. |
| 3003 | ctw3 | EverSweet Max Cordless | fountain | http | Battery. |
| 4001 | k3 | Pura Air K3 | purifier | http | Liquid and battery only. |

- **Pets:** Mochi (id 1, 4.2 kg entered, learned weight in use, has a photo) and Tigrou (id 2,
  6 kg, heavier, no photo).
- **History:** seven days of it, about 170 cards. It holds visits with weights and durations,
  auto, periodic and manual cleanings, scheduled meals, drinks, cats seen by a camera, one fault
  episode and one visit flagged as a health alert. The history is generated from the seed, so a
  given seed always replays the same week relative to today.

Each model's entity keys and action keys are exactly the ones petkit-local defines for it.
`src/main/resources/catalog.json` is generated from petkit-local's own definitions
(`ha/categories.py`, `ha/entities/*.py`, `ha/commands.py`) by `tools/dump_catalog.py`. To refresh
it, run that script from a petkit-local checkout with its requirements installed. Its docstring
has the command.

**W5, CTW3 and K3 are Bluetooth accessories.** In petkit-local they reach the server through a
parent device and, at the pinned commit, the real `/app/v1` does **not** list them. The mock lists
them anyway, so the app can show every family, with the entities petkit-local's BLE definitions
give them. It offers no actions for them, because their buttons are not among the actions
petkit-local can send.

## Scenarios

The scenario engine works on two timers:

- **Every second:** it serves the planned meals and one-off feeds that fell due.
- **Every `scenario-interval` seconds:** it rolls one event:
  - a visit on a free litter box, usually by a cat that uses that box;
  - a drink at the W7H;
  - a consumable going down by one (N50/N60 days, desiccant, litter, filters, battery, K3 liquid);
  - a device dropping offline and coming back two intervals later;
  - a "bin full" error and its clearing;
  - a cat seen by a camera box;
  - a Wi-Fi signal change.

Actions behave like the device. For example, `cleaning_start` moves `device_status` to cleaning for
`busy` seconds, then back to idle, and lowers the litter level. Its card first appears as
"Mechanism started" and is then replaced by "Manual cleaning completed". A box that is already busy
ignores a new start.

## Control API

Under `/mock`, no authentication, JSON in and out. It is meant for the app's end-to-end tests, and
`--control=false` removes it.

| Route | Body | Effect |
|---|---|---|
| `GET /mock` | | Routes, clock offset, scenario state, open sockets. |
| `POST /mock/reset` | | Back to the seed: devices, pets, history, password. Sessions are dropped. |
| `POST /mock/devices/{id}/visit` | `pet_id?`, `weight_g?`, `duration_sec?`, `auto_clean?`, `health_alert?` | A live visit on a litter box. |
| `POST /mock/devices/{id}/meal` | `amount?`, `amount1?`, `amount2?` | A feeder serves a meal. |
| `POST /mock/devices/{id}/drink` | `pet_id?`, `duration_sec?` | A drink at a fountain. |
| `POST /mock/devices/{id}/error` | `message?` (default `bin full`) | Error state and a fault card. |
| `POST /mock/devices/{id}/error/clear` | | Clears it, with an "Error cleared" card. |
| `POST /mock/devices/{id}/offline`, `/online` | | Sends a `device` message; an MQTT device falls back to `http` while offline. |
| `POST /mock/devices/{id}/remove` | | Sends `device_removed`; a reset brings the device back. |
| `PUT /mock/devices/{id}/state` | `{"<value_path>": value, ...}` | Sets raw state behind any entity, for example `{"consumables.liquid": 5}`. |
| `POST /mock/clock/advance` | `seconds` | Moves the fake clock forward, for session expiry and lockouts. |
| `GET /mock/sessions` | | Live sessions. |
| `DELETE /mock/sessions/{id}`, `DELETE /mock/sessions` | | Revokes one or all; their sockets close with 4401. |
| `PUT /mock/password` | `password`: string or `null` | Sets it as the panel does and drops every session. `null` means no password. |
| `POST /mock/fail` | `method`, `path`, `status`, `error?`, `message?`, `retry_after?`, `times?` | The next `times` (default 1) requests to that route get this error. `path` is a spec template (`/devices/{id}`) or a concrete path. |
| `DELETE /mock/fail` | | Clears forced failures. |
| `POST /mock/ws/close` | `code?` (default 1001), `reason?` | Closes every `/ws` socket with that code. |
| `POST /mock/scenario/tick` | | Rolls one scenario event now. |
| `PUT /mock/scenario` | `interval_sec` | Restarts the engine at that interval; `0` stops it. |

## Tests

```sh
./gradlew check     # ktlint, detekt and the tests
```

The tests use JUnit 5 and Ktor `testApplication`. Every response a test receives is checked
against `api/openapi.yaml`:

- the status must be declared for the operation;
- the content type must be declared;
- the headers the app relies on (`Retry-After`, `WWW-Authenticate`) must be present;
- the body must pass the JSON Schema, checked with networknt json-schema-validator in its OpenAPI 3.1
  dialect;
- every key sent must be named by the schema.

Every `/ws` message is checked against `UpdateMessage`. One test fails if any operation of the spec
went uncalled, so a route added to petkit-local fails this suite until the mock serves it.

The other tests cover:

- the auth rules;
- paging;
- the `/ws` sequences and close codes;
- the control API;
- the write validation;
- the determinism of the seed.

To update the contract, run `scripts/update-openapi.sh <petkit-local ref>` from the repo root,
then run the tests.
