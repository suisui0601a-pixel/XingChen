# Changelog

## Unreleased

### Fixed

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
