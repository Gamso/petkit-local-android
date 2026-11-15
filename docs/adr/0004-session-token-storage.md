# ADR 0004: The session token is encrypted with an Android Keystore key and kept in DataStore

Status: accepted (M0; implemented in M1)

## Context

Signing in yields a bearer token, valid until 90 days without use. Whoever holds it controls the
user's devices. The password itself must never be stored. Jetpack Security's
`EncryptedSharedPreferences` is deprecated.

## Decision

- An AES-256-GCM key is generated in the Android Keystore: not exportable, hardware-backed where
  the phone has the hardware. The token is encrypted with it; the ciphertext and IV are stored
  in DataStore (`core:datastore`). The password is used once, for the login call, and dropped.
- `core:network` never stores the token. It reads it through `SessionTokenProvider`, which the
  data layer implements on top of this store.
- The token travels only in the `Authorization` header, only to the configured server's origin
  (`AuthInterceptor`), never in a URL.
- Backup and device transfer exclude the app's data (`data_extraction_rules.xml`): the
  ciphertext is useless without the Keystore key, which never leaves the phone.

## Consequences

- A lost or invalidated Keystore key (a lock-screen reset, on some phones) means signing in
  again; the app treats a failed decryption as "signed out", not as an error.
- No deprecated crypto library; the code to own is small and testable with Robolectric.
