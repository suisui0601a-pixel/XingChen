# Changelog

## Unreleased

### Fixed

- Fix production participation wiring that supplied a constant zero RNG: every
  positive ordinary-message probability previously selected every eligible event.
  Use the default random source; keep deterministic RNGs confined to tests.
- Clarify that probability-based participation requires a new inbound message and
  is disabled by default. Idle proactive messaging has not been implemented.
- Keep final text dispatch independent of tool-result bookkeeping, while retaining
  per-tool and per-output ledger keys. Stop automatic continuation after UNKNOWN
  tool delivery rather than risking a second send.
- Record rate/burst rejections explicitly. Reserve shared send-attempt quota in a
  short critical section; transport IO and pacing no longer hold the shared lock.
  Failed and UNKNOWN attempts consume quota; durable replays do not consume it again.
- Scope OneBot reads and hydrated quotes by platform and private/group identity.
  Native tools use non-consuming reads with the persistent markRead cursor and
  reject expired cursor windows rather than silently losing or replaying messages.
- Serialize plain text and replies as typed OneBot segments. CQ-looking model text
  stays literal text; images use bounded, verified managed bytes and Base64 transport.
- Preserve unsaved gateway drafts during status polling, flag observed external
  configuration changes, and cancel superseded/unmounted status requests.
- Reload the current slang filter after moderation completes and ignore superseded
  list responses instead of overwriting the newly selected status.
- Bound frontend tool-version probes and disable Windows cmd AutoRun for npm
  invocations so build verification cannot wait indefinitely on inherited pipes.
- Update SQLite JDBC to 3.54.1.0 and compatible frontend security patches.

### Integration boundary

- Reserve a read-only MemoryRecallPort backed by the existing local retriever.
  No external memory service, SDK, network calls, or migration is enabled.

### Earlier changes

- Added provider-local, reversible function/tool-name mapping for DeepSeek. Core
  canonical dotted names and capability policy remain unchanged.
- Added collision detection, rejection of unknown provider tool names, historical
  tool-call mapping, stream-response decoding, and sanitized provider diagnostics.
- The operator reported production QQ message/tool round-trip and second-turn
  tool-history acceptance after deploying this adapter fix on 2026-10-05. This is
  a historical operator report for that integration path; it does not claim that
  the current native-OneBot Core production cutover has occurred. This repository
  sync does not independently repeat live production conversations.
- Added independent SecretStore-backed OneBot HTTP and forward-WebSocket
  credentials, including transport-specific save/clear, legacy fallback bounds,
  redacted status, and connection checks that avoid a second WS subscription.
- Fixed wake evaluation so configured speakers are contextual triggers, blank or
  invisible-only text does not spuriously wake, and ASCII bot names use
  whole-word matching. Explicit/contextual triggers remain independent of random
  participation probability.
- Decoupled ordinary SocialRuntime startup from DSH. Social, OneBot, and Model
  are its fail-closed prerequisites; DSH interactions have a separate gate.
- Clarified access-control, wake, and HTTP/WS credential settings in the Console.

## 0.1.0-SNAPSHOT — development / production infrastructure candidate

- Provider-neutral Java Agent/Tool contracts, OneBot transport and durable social
  runtime, identity, relationship, memory and bounded capability policies.
- Authenticated bilingual Console for runtime, people, prompts, media, models,
  access, usage, security and operations; unsupported voice features are explicit.
- Docker security and persistence tests, restart/recreate and offline recovery
  tooling with checksummed format-1 backups (schema 28).
- One-time loopback-only browser administrator initialization, atomic first-user
  claim, persisted-admin bind guard and reviewed public HTTPS deployment overlay.

Real QQ and Model account configuration is deferred to the operator. This is not
a claim that the public deployment has completed real QQ/model E2E.
