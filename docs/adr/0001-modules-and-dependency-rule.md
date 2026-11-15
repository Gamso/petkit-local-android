# ADR 0001: One module per layer and per feature, with a checked dependency rule

Status: accepted (M0)

## Context

The app has a pure domain, a data layer (REST, WebSocket, Room, DataStore) and seven screens
that change at different times. Without a boundary, a screen soon calls Retrofit directly, and
caching, error mapping and tests end up in every ViewModel.

## Decision

- Modules as in ARCHITECTURE.md §2: `:app`; `core:domain`, `data`, `network`, `database`,
  `datastore`, `designsystem`, `ui`, `testing`; `feature:auth`, `home`, `device`, `history`,
  `pets`, `live`, `settings`; `:push` (notifications, ADR 0007).
- A `feature:*` module sees `core:domain`, `core:designsystem` and `core:ui`, never `core:data`,
  `core:network`, `core:database` or `core:datastore`. `core:domain` sees no other module and
  has no Android dependency (it is a Kotlin/JVM module). Only `:app` sees everything and binds
  implementations with Hilt.
- Shared configuration lives in convention plugins (`build-logic/`), versions in
  `gradle/libs.versions.toml`.
- The rule is a build failure: `checkModuleRules` (part of `check`, run by CI's static job)
  walks each module's resolved compile classpath, so a transitive `api` leak is caught as
  surely as a direct dependency.

## Consequences

- A feature cannot import a data-layer class: the build fails and names the edge.
- More modules mean more build configuration; the convention plugins keep each module's build
  file to a few lines.
- Features do not depend on each other; `:app` wires navigation between them.
