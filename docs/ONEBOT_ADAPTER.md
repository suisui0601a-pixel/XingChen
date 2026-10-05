# OneBot v11 adapter

`OneBotV11Gateway` is a forward-WebSocket client with a separate HTTP action client. WS and HTTP URLs are explicit configuration; access token is read by environment-variable name and attached as `Authorization: Bearer …` to both transports. URI validation rejects non-loopback endpoints by default; opting into a non-loopback host is an explicit configuration change.

The transport normalizer maps private/group messages, text, image/face/forward metadata, @ mentions, reply references and poke notices into `PlatformEvent`. HTTP actions cover private/group send, reply, history, message lookup, group member listing, poke, and documented `get_login_info` account identity. Event buffer, unread cursors and bounded wait are local and bounded. The WebSocket reconnects with capped exponential backoff and exposes connected/heartbeat/retry status. `OneBotConfigurationApplier` applies the shared Console settings to the live client without restarting the JVM. Errors and logs do not contain credentials or raw payloads.

All adapter contract tests use the local `FakeOneBotServer`; no SnowLuma or QQ account is contacted. The Spring application remains social-runtime-disabled by default.
