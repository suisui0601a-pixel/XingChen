# OneBot HTTP / WebSocket token separation

## Contract

The Console accepts only an explicit transport for credential writes:

```json
{"transport":"http","action":"replace","token":"<provided by administrator>"}
```

```json
{"transport":"ws","action":"replace","token":"<provided by administrator>"}
```

`transport` is required. Old writes that omit it are rejected; they cannot modify a shared or either transport credential. `GET /api/gateway/status` and the security posture expose only configured/initialized booleans, never token values. All `/api/**` endpoints retain Console authentication and CSRF protection.

## Storage and runtime

- HTTP: SecretStore key `onebot-http-access-token`, file `onebot-http-access-token.secret`, environment bootstrap `XINGCHEN_ONEBOT_HTTP_ACCESS_TOKEN`.
- WebSocket: SecretStore key `onebot-ws-access-token`, file `onebot-ws-access-token.secret`, environment bootstrap `XINGCHEN_ONEBOT_WS_ACCESS_TOKEN`.
- The HTTP adapter reads only the HTTP key; the active WebSocket adapter reads only the WS key. Console writes hot-apply the new pair and reconnect the active WebSocket using its own credential.
- SecretStore files use its existing atomic owner-only writes and clear tombstones. Per-transport clear persists across restart.

## Legacy compatibility and safe rollout

`onebot-access-token` (including the old `XINGCHEN_ONEBOT_ACCESS_TOKEN` / `ONEBOT_ACCESS_TOKEN` bootstrap aliases) remains a shared **read-only fallback** only while that transport has never been explicitly initialized. A transport-specific save or clear creates its own initialized state; that transport then stops using the fallback. Clearing one transport does not clear the legacy key or affect the other transport. The UI identifies fallback use. Legacy material is not automatically copied to both new keys, and the legacy source is not automatically deleted.

For deployment, back up the SecretStore directory and inspect only file metadata/permissions before rollout. Do not print or copy credential contents into logs, shell history, ordinary config, or support reports. Confirm existing transport-specific files are owner-only and preserved. Validate the isolated build first; after an authorized deployment, use the protected UI to save or clear each transport separately. Do not treat a configured flag as proof the remote endpoint accepts that credential.

## Connection checks

- `POST /api/gateway/test/http` performs the read-only OneBot `get_login_info` request and reports a sanitized status (`DISABLED`, `CONFIG_INCOMPLETE`, `CONNECTED`, `CREDENTIAL_REJECTED`, `UNAVAILABLE`, or `SERVICE_ERROR`). No response body or token is returned.
- `POST /api/gateway/test/ws` reports the already-running WebSocket state. It intentionally does not open a second event subscription, avoiding duplicate event delivery or interference. A connected active socket proves the current WS handshake succeeded; a disconnected status alone cannot safely distinguish bad credentials from network/service failure.
- The UI renders these statuses in plain language. A WS disconnection is intentionally reported as “not connected” rather than guessing whether the cause is the credential, network, timeout, or remote service.

## Access and wake UI notes

Access is evaluated before wake behavior. Owner recovery remains separate and read-only; the access evaluator retains owner recovery → explicit deny → explicit allow → default deny and fails closed. Private QQ user IDs and group IDs have distinct labels and help text; `configuredSpeakerIds` remains a wake condition, not an access allowlist.

Wake probability remains a persisted decimal in `[0, 1]`; the Console presents whole percentages and converts `25%` to `0.25`. Explicit mention/reply/poke/name/question triggers remain independent of random probability. The active runtime currently has no proactive timer; probability is evaluated only for a received ordinary event after access checks, and sleeping/WAIT lifecycle rules remain in force.

## Verification boundary

Tests exercise separate storage, adapter selection, clear/restart behavior, unauthenticated-write rejection, no secret response echo, access fail-closed behavior, and deterministic probability boundaries. These tests do not modify or validate production state. Production deployment and credential acceptance require a separate authorized rollout and real endpoint verification.
