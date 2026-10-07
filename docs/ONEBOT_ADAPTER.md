# OneBot v11 adapter

`OneBotV11Gateway` is a client for an independently operated OneBot v11 gateway. It uses two distinct transports:

- HTTP action client → HTTP credential (`onebot-http-access-token`)
- Forward WebSocket client → WS credential (`onebot-ws-access-token`)

Both credentials are stored through the protected SecretStore, independently saved and cleared, and never returned by status/configuration APIs. The Console reports configured state only and requires an explicit `transport` on writes. Clearing one transport does not clear the other. The legacy shared `onebot-access-token` is a read-only fallback only until that transport has an explicit initialized value (including a durable clear tombstone); it is not copied into both new credentials.

The HTTP client attaches only the HTTP credential as `Authorization: Bearer …`. The forward WebSocket client attaches only the WS credential. Configuration hot-apply replaces the active WebSocket connection using a fresh credential snapshot; in-flight HTTP requests keep their captured configuration. Errors and logs do not include credentials or raw OneBot payloads.

## Connection checks

The HTTP connection check uses the read-only `get_login_info` action and reports a sanitized status. It does not return the gateway response body or credential. WS status reflects the already-running forward subscription. The status check must not open a second production WebSocket, which could cause duplicate event consumption. A disconnected socket alone is insufficient to distinguish bad credentials from a network or service failure.

The adapter normalizes private/group messages, text, image/face/forward metadata, mentions, reply references and poke notices into `PlatformEvent`. HTTP actions cover private/group send, reply, history, message lookup, group member listing and poke. Event buffering, unread cursors and bounded wait are local and bounded. WebSocket reconnect uses capped exponential backoff and exposes connected/heartbeat/retry status.

Endpoint validation rejects non-loopback addresses by default. A deployment that uses a gateway on another Docker service must explicitly authorize that non-loopback endpoint; use the service DNS name and keep the gateway on a trusted, isolated network. Inside the Core container `127.0.0.1` means Core itself. The independent gateway is not part of the Core image or application lifecycle.

Adapter contract tests use local `FakeOneBotServer` instances. They validate separate transport credentials and do not contact a live QQ account or gateway.
