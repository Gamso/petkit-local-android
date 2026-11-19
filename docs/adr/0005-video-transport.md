# ADR 0005: Live video as fMP4 over a WebSocket first, HLS as the fallback, both in Media3

Status: accepted (M0; implemented in M4)

## Context

petkit-local serves a camera's live stream two ways: go2rtc's MSE stream (fragmented MP4 over a
WebSocket, low delay) and HLS (fMP4 segments over plain HTTP, a few seconds of delay). Both take
the bearer header. Viewers are capped per camera and per server.

## Decision

- One player: Media3 ExoPlayer.
- First choice: `GET /devices/{id}/live`. The app sends one `mse` message listing the codecs the
  phone's decoders support, then feeds the init segment and the fragments to ExoPlayer through a
  pipe-backed `DataSource`, on the shared OkHttp client.
- Fallback: HLS (`/devices/{id}/live/hls/playlist.m3u8`) through `DefaultHttpDataSource`, with
  the bearer header as a default request property, on close code `1013` (the app read too
  slowly) or after two failed starts. Close code `4409` (the same session opened the camera
  again) is silent; `4401` ends the session.

## Consequences

- Low delay when the network allows it, and still a picture when it does not.
- A custom `DataSource` to write and test (MockWebServer can serve a WebSocket); HLS needs none.
- No WebRTC: talking through a camera goes over a WebSocket of its own (`/devices/{id}/talk`,
  SPEC.md §7 and decision 5), not through the video transport.
