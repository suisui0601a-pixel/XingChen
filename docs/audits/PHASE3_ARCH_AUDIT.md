# Phase 3 Architecture & Safety Audit

Audit baseline: `e45553a` (`feat: integrate model and onebot social runtime`). Scope was local source, fixtures, FakeOneBot, and loopback test servers only. No SSH, production QQ/OneBot/DSH, SnowLuma, DeepSeek API, domain, or Zetu access was performed.

## CRITICAL

None identified in the reviewed Phase 1–3 code paths.

## HIGH

### H-1 — Memory scope checks let owner status bypass project/person isolation — fixed

- **Issue:** `MemoryVisibility` allowed an owner request to read any `PROJECT` memory, regardless of `projectKey`, and any `PERSON_GLOBAL` memory regardless of subject.
- **Impact / trigger:** a request marked owner could include another project's or another person's private memory in context/search.
- **Fix:** `PROJECT` now requires a non-null exact `projectKey`; `PERSON_GLOBAL` is visible only to its subject. `OWNER_GLOBAL` remains owner-only; `PRIVATE` retains its explicit owner-or-subject rule.
- **Regression:** `audit_memoryProjectScopeMustMatchEvenForOwner`, `audit_personGlobalIsPrivateToSubjectEvenFromOwner`, plus existing conversation-scope tests.

### H-2 — Memory provenance could point at a missing or unrelated message — fixed

- **Issue:** `MemorySqliteRepository.save` accepted any non-empty `MemorySource`, without verifying the claimed platform/message/conversation/actor existed together.
- **Impact / trigger:** bad or fabricated provenance could be persisted with otherwise valid memory.
- **Fix:** sources must match a persisted message's platform ID, conversation and actor before the memory and its sources are inserted; the write remains transactional.
- **Regression:** `sqliteMemoryRepositoryPersistsProvenanceAndUsesFts`, `audit_memoryAndProvenanceWriteRollBackTogether`.

### H-3 — Tool call replay had no claim/idempotency boundary — fixed with wiring caveat

- **Issue:** provider call IDs were discarded when converting `ModelToolCall` to `ToolCall`; replaying the same event/call could re-run a side effect.
- **Impact / trigger:** duplicate event processing or a caller replaying an Agent request could send/reply/poke or write memory again.
- **Fix:** `AgentExecutor` claims `(eventId, callId)` before execution and safely stops on duplicate. Added atomic SQLite `tool_execution_claims` storage (`SqliteToolExecutionLedger`) and a process-local implementation for isolated tests. Existing constructors use the in-memory ledger; production composition must inject the SQLite implementation before any deployment.
- **Regression:** `audit_sameEventAndCallIdCannotExecuteToolTwice`, `audit_toolExecutionClaimIsAtomicAndPersistent`.

### H-4 — Social runtime globally serialized all conversations — fixed

- **Issue:** one unbounded single-thread executor serialized unrelated conversations and had no queue cap.
- **Impact / trigger:** one slow model call blocked all chats; event bursts could grow memory without bound.
- **Fix:** bounded (1024 pending events) per-conversation FIFO lanes over four workers. Same-conversation work remains ordered; other conversations can proceed in parallel. Overload fails closed and logs a generic warning without message content.
- **Regression:** `audit_serializesWithinConversationButRunsOtherConversationsInParallel`.

### H-5 — OneBot send failures collapsed ambiguous outcomes into ordinary rejection — fixed

- **Issue:** a timeout, transport failure, or server error after a send request could mean the message was delivered, but the adapter returned only `accepted=false`.
- **Impact / trigger:** a caller could misinterpret an uncertain send and retry, duplicating user-visible side effects.
- **Fix:** `SendReceipt` now distinguishes `ACCEPTED`, `REJECTED`, and `UNKNOWN`. Timeout/transport/5xx map to `UNKNOWN`; tool output explicitly says not to retry automatically. The gateway does not retry sends.
- **Regression:** `audit_httpTimeoutIsUnknownAndMustNotBeRetriedBlindly`, `ob106_httpServerErrorReturnsUnknownReceipt`.

### H-6 — WebSocket liveness and event accumulation were not bounded — fixed

- **Issue:** no heartbeat timeout closed/reconnected a silent socket, and fragmented event text accumulated without a size limit.
- **Impact / trigger:** a dead connection could remain reported connected; a peer could accumulate arbitrarily large frame fragments.
- **Fix:** configurable heartbeat timeout (default 90 seconds; `ONEBOT_HEARTBEAT_TIMEOUT_MS`) aborts and reconnects stale sockets; event text is capped at 1 MiB and oversized events are closed with status 1009.
- **Regression:** `audit_missingWebSocketHeartbeatTriggersReconnect`. The 1 MiB rejection path is bounded in code; no large-frame integration case was added because the local fake server does not emit fragmented 1 MiB frames.

### H-7 — Private read-only Console could be rebound to a public interface without an explicit gate — fixed

- **Issue:** Console API returns identity and memory data. Its default bind is loopback, but an environment/config override could silently bind `0.0.0.0`.
- **Impact / trigger:** a deployment operator changing the bind variable could expose private records beyond the host.
- **Fix:** startup now rejects any non-loopback resolution unless `XINGCHEN_CONSOLE_ALLOW_NON_LOOPBACK=true` is explicitly set. The default remains loopback.
- **Regression:** `consoleBindDefaultsToLoopbackSafeAddresses`, `consoleBindRejectsNonLoopbackUnlessExplicitlyOptedIn`.

## MEDIUM

### M-1 — Context token estimate is approximate, not a provider tokenizer — mitigated, not fully resolved

- **Impact / trigger:** large or unusual Unicode/tool payloads may cause estimation error. `AgentExecutor` rejects contexts above its configured estimate before a call, and records provider usage after a response, but cannot guarantee provider-side context acceptance based on an approximate estimator.
- **Current behavior:** each content category and tool-result budget is tracked independently; actual provider usage is separate from context estimate.
- **Phase 4 change:** `ProviderTokenEstimator` keeps the approximate fallback, can calibrate per provider/model from reported input usage, and `ContextBudget` applies a configurable conservative margin. Diagnostics distinguish calibrated estimate from the added margin.
- **Remaining:** calibration is process-local and is not a tokenizer guarantee. Real provider boundary behavior still requires provider-specific fixtures/usage and should not be described as exact tokenization.

### M-2 — Session handoff/summary lifecycle is in-memory — resolved for SQLite composition

- **Impact / trigger:** process restart could lose rollover summary/task handoff state.
- **Phase 4 change:** `SqliteSessionLifecycleManager` persists generation, summary handoff, last rollover, and context estimate transactionally. Re-saving an identical current handoff is idempotent; a recreation test verifies restart continuity.
- **Remaining:** callers must compose the SQLite implementation rather than the in-memory test implementation.

### M-3 — Durable idempotency depends on composition — hardened; full runtime composition pending

- **Impact / trigger:** constructors intended for tests/legacy use default to `InMemoryToolExecutionLedger`; it protects only the current process. The SQLite ledger is implemented and tested but must be explicitly supplied to the production `AgentExecutor` composition. Requests without stable event IDs cannot receive cross-run deduplication.
- **Phase 4 change:** `AgentExecutor.production(...)` rejects a non-durable ledger; `SqliteToolExecutionLedger` declares stable event IDs mandatory, and the executor refuses tool calls when one is missing.
- **Remaining:** the application does not yet have a complete production Agent composition root, so deployment-level injection is not verified.

### M-4 — Event order is transport-arrival order — resolved with bounded watermark policy

- **Impact / trigger:** the conversation lane preserves callback arrival order, not timestamps. A transport that replays events out of order is not reordered by the adapter.
- **Current protection:** duplicate platform/message IDs are constrained in SQLite and existing E2E tests ensure duplicate events do not trigger a second response.
- **Phase 4 change:** `IncomingMessageStore` archives duplicates/late/unorderable events without invoking the Agent for them; it tracks per-conversation timestamp, comparable numeric platform message ID, and optional OneBot sequence watermark. A late archived event does not advance the watermark or Agent/memory processing; membership `last_seen_at` is monotonic. Equal second-resolution timestamps with non-comparable IDs deliberately retain arrival order.
- **Regression:** `event102_lateOneBotEventIsArchivedButDoesNotAdvanceConversation`.

### M-5 — Streaming retry is bounded to effect-free turns

- **Impact / trigger:** a mid-stream network failure is surfaced as a sanitized provider exception; it is not transparently resumed. This avoids replay ambiguity and does not execute partial tool calls, but may lose a response.
- **Phase 4A update:** `ModelTurnExecutionState` now distinguishes buffered `MODEL_OUTPUT_RECEIVED` from confirmed `OUTBOUND_OUTPUT_SENT`. Buffered partial text and incomplete tool arguments may be retried within the configured bound; executed tools, confirmed sends, and `UNKNOWN` delivery fail closed. The orchestrator records confirmed/unknown final-reply delivery in the turn state. Local HTTP fixtures cover partial-output retry and state transitions.
- **Remaining:** there is no provider resumable-stream ID, and only local fixtures verify this policy. The stream cannot resume at a token offset; a safe retry is a fresh generation. Durable outbound execution records/reconciliation and end-to-end failure injection remain open.

### M-6 — FTS soft-deleted rows remain physically indexed — resolved

- **Impact / trigger:** soft deletion leaves an FTS entry on disk, though the repository query now filters status and expiry before returning results. It is not recallable through repository search but remains in the index until physical cleanup/rebuild.
- **Phase 4 change:** migration V6 now removes FTS entries on soft delete/status change and only indexes ACTIVE rows; the SQLite repository contract verifies that the FTS row is physically absent after delete.

## LOW

- `IdentityRegistry` primary keys are `(platform, platformUserId)`; nickname/card are only membership/alias display data. Membership keys are person + conversation. Registry access is now synchronized around short in-memory operations; no bot/model/network work occurs under that monitor.
- Quote/reply attribution uses platform user IDs and stable actor fields; display names remain descriptive only. No display-name/nickname/card identity comparison was found in the reviewed identity path.
- Stable UUID relationship targets and scoped resolution prevent same-name collisions. There is no supported person-delete/merge workflow yet; current SQL foreign keys prevent deleting referenced identities rather than cleaning/migrating their relationships.
- Social capabilities are a closed allowlist and do not include SHELL, SSH, DOCKER, ARBITRARY_FILESYSTEM, or PROCESS_CONTROL. Tool names use exact catalog equality; no reflective or fuzzy fallback is present.
- Largest Java files by line count are `DeepSeekProvider` (72), `ConversationOrchestrator` (72), `SocialToolExecutor` (70), `SocialRuntime` (69), and `OneBotEventNormalizer` (66). Raw line count is low, but several files compress multiple methods onto long lines; readability/diagnostic cost is a maintenance debt. No broad refactor was made in this safety pass.

## INFO

- DeepSeek retry is confined to the provider HTTP request before a `ModelResponse` reaches `AgentExecutor`. Partial SSE tool calls are accumulated by index/name/argument fragments; mid-stream errors do not return a tool call for execution. Tool execution itself is not retried by the provider.
- SQLite uses WAL, foreign-key enforcement, and now a 5-second busy timeout. Memory + provenance writes are transactional; rollback is covered by a duplicate-provenance failure after the first source insert.
- Hard security guidance is sent as the first system message. It is guidance, not an authorization boundary: `CapabilityPolicy`, exact tool catalog checks, `MemoryPolicy`, and executor-side target checks remain authoritative.
- Prompt Simulation and Persona remain distinct fields and versioned layers. The hard policy is not editable through prompt APIs.
- Wrapper configuration is pinned to `gradle-9.8.0-bin.zip` with the committed SHA-256; wrapper JAR is tracked. `.toolchain/` is ignored and is not a clone prerequisite. README now states that a clean clone needs JDK 21 and network access for first Wrapper/dependency downloads; offline builds require both Wrapper and dependency caches. In this environment the wrapper distribution was seeded from the existing local archive after its SHA-256 matched the committed checksum; the first direct online fetch was blocked/slow, so a clean-machine online fetch was not independently demonstrated.
- Feature-matrix status counts remain 36 TESTED / 17 FOUNDATION / 57 PLANNED (110 rows). This audit did not promote rows; `TESTED` remains local contract/mock coverage, not production integration.

## Verification boundary

The final audit command `gradlew.bat clean test bootJar` completed successfully with 149 tests, 0 failures, 0 errors, and 0 skipped tests, using JDK 21 and the pinned wrapper. A local JAR smoke test bound only to `127.0.0.1:3200`; `/health`, `/api/status`, `/api/runtime`, and `/api/usage` each returned HTTP 200. The runtime reported OneBot disconnected, agent disabled, and live API disabled. The process was stopped and port 3200 was released. No production service was touched.
