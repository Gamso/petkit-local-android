# ADR 0003: Hand-written DTOs, checked by OpenAPI contract tests

Status: accepted (M0)

## Context

petkit-local publishes its `/app/v1` contract as an OpenAPI 3.1 document. A generated client
would follow it automatically, but brings a generator, its templates and code no one reads, and
OpenAPI 3.1 support in Kotlin generators is still uneven (type unions such as
`[string, "null"]`, `const`, `oneOf` with a discriminator).

## Decision

- DTOs are written by hand with kotlinx.serialization, one class per schema, field for field
  (`core:network`, `dto/`). The Retrofit service declares the REST routes by hand.
- The document is vendored at a pinned ref (`api/openapi.yaml`, ref in `api/SOURCE`) by
  `scripts/update-openapi.sh`, and refreshed by a weekly pull request (`contract-refresh.yml`).
- Contract tests read it with a small OpenAPI reader and fail when:
  - a schema has no DTO, or a DTO no schema;
  - a DTO and its schema differ in a property, its required-ness, its nullability or its type;
  - a DTO cannot read a document its schema allows (built from the schema, plus any examples
    the document gives), or cannot write it back unchanged;
  - a Retrofit route is not in the document with the same method, path and query parameters,
    request body and response body;
  - a route of the document is neither in Retrofit nor one of the known WebSocket/HLS routes.
- Enums stay Strings in DTOs, their known values checked against the schema. The data layer
  maps them with a fallback, so a value the server adds later cannot fail a whole response.

## Consequences

- A server change breaks a test in a reviewed pull request, not the app on a user's phone.
- Each new field is typed once by hand; the failing test names it.
- The prose-only parts of the contract (the live-video socket's messages) are pinned by unit
  tests instead.
