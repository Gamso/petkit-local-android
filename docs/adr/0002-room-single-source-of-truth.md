# ADR 0002: Room as the single source of truth, with no optimistic device commands

Status: accepted (M0; implemented from M1)

## Context

Data arrives three ways: REST refreshes, the `/ws` live socket, and not at all when the server
is unreachable. Screens must look the same in each case. Device commands are physical: a litter
box asked to clean may be busy, offline, or refuse.

## Decision

- Devices, pets and timeline pages are stored in Room. ViewModels observe Room flows only.
- REST refreshes write to Room; WebSocket messages patch it (`hello` replaces, `state` patches
  entity values, `timeline` upserts by id and drops the cards in `replaces`).
- Writes go to the server first. The cache changes only when the server's answer, the
  WebSocket or a refetch confirms the new state. The UI shows a command as sent (`delivered`:
  mqtt, queued or local), never the device as already in its new state.

## Consequences

- Offline, the last known data stays on screen with a banner, at no extra cost.
- A command shows its effect a moment later than an optimistic UI would; a litter box that did
  not start never looks as if it did.
- Room migrations become part of every schema change, and are tested.
