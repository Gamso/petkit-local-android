# ADR 0008: A CTW3's drink history over the phone's own Bluetooth

Status: accepted (2026-11-22; issue #104)

## Context

The EverSweet Max 2 (CTW3) keeps its drink history in its own memory. petkit-local reaches it only
through a parent device's Bluetooth relay, and through a relay the fountain answers reads but
never the history request, its security check or its clock (Gamso/petkit-local#140: 213, 86, 84
and 212 silent, on the owner's hardware). PetKit's own app syncs that history only over a direct
link from the phone, and fails with the phone's Bluetooth off.

So the history can reach petkit-local only through a phone standing next to the fountain. The app
otherwise talks to nothing but the user's server (SPEC.md §1).

## Decision

- The app reads the history itself, over a direct GATT link, exactly as PetKit's app 13.8.1 does
  (`PetkitBleClient`, `CTW3BleClient`): the PetKit service `0000aaa0-…`, writes on `aaa1`,
  notifications on `aaa2`, MTU 255 asked; then 213, 86 (the pairing secret), 84 (the clock),
  200, 201, 66, 210, 211, 215, 216, 212, and the stream (80, 68/82, 67, 69), one frame in flight.
- petkit-local supplies what the session needs and stores what it reads (petkit-local#141): the
  fountain's address and secret (`GET /accessories/{id}/ble-sync`), and the records
  (`POST /accessories/{id}/drinks`), which become drinking cards like the relay's. The app keeps
  nothing but the time of its last sync.
- Only on the owner's press (Sync drinks on the fountain page), never in the background: no
  scanning, no Bluetooth permission until then.
- Android's own `BluetoothGatt` API, no library. The radio is behind `BleTransport` (core:data), so
  the session is replayed against a scripted fountain in unit tests, and a debug build's
  end-to-end tests can put one in place of the radio (`EndToEndBleTransport`, never set by the
  app itself). A release build binds the radio directly.
- The secret is a credential: it lives in memory for one sync, is zeroed after it, and is never
  logged, cached or written (`FountainSecret` prints nothing of it).

## Consequences

- The app needs the nearby-devices permissions from Android 12 (`BLUETOOTH_SCAN` with
  `neverForLocation`, `BLUETOOTH_CONNECT`), and precise location on Android 10 and 11, where a scan
  requires it. Bluetooth LE is declared optional: a phone without it installs the app and says so
  when Sync drinks is pressed.
- Nothing of the session can be verified on an emulator: the protocol is pinned by unit tests built
  from the bytecode, and confirmed only on a phone beside a real fountain.
- A second channel besides the server exists, limited to one fountain the owner stands next to,
  and carrying nothing to or from anything but that fountain and petkit-local.
