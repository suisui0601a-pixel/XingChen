# XingChen Core — Phase 1 architecture

## Scope and boundaries

Phase 1 is a local Java domain foundation. It does not connect to QQ, SnowLuma, DSH, DeepSeek, or any production service. `core` contains platform-neutral identity, relationship, memory, context, prompt, and capability policy. `storage` is an infrastructure adapter; `console` currently exposes only health. Future platform integrations belong under `adapter/onebot`, `adapter/dsh`, and `adapter/deepseek`; product features belong under `feature/`.

The legacy behavior reference is Derpyu520/qq-bridge `0.1.7`, commit `61b7e2e6905ec60489ec3985307b2251e37087bf`, with DSH compatibility baseline `0.1.7-rc.2`. See the audited inventory in [LEGACY_FEATURE_MATRIX.md](LEGACY_FEATURE_MATRIX.md); partial and future behavior remains explicitly `FOUNDATION` or `PLANNED`, rather than being promoted to parity.

## Event and identity flow

An eventual OneBot adapter normalizes transport payloads into `PlatformEvent`: platform, conversation identity, actor identity, message identity, reply/reference, timestamp, text, and opaque raw metadata. Core decisions use stable `(platform, platformUserId)` and `(platform, conversationType, platformConversationId)` keys; display names and cards are aliases only. Each person has per-conversation `Membership`, preserving role, current display name/card, and observation timestamps. `SELF`, `OWNER`, `MEMBER`, `GROUP_ADMIN`, and `GROUP_OWNER` remain explicit identity roles/flags; configured owner IDs are not embedded in domain logic.

## Relationship/address resolution

`RelationshipTerm` relates stable person IDs, never labels. `AddressResolver` precedence is conversation-explicit, global-explicit, conversation-inferred, global-inferred, then display name. A conversation-scoped term cannot escape its conversation. `PRIVATE` terms are part of the storage vocabulary and must be restricted to private conversation access in the later persistence/service layer.

## Memory and privacy

Long-term memory records have typed scope, confidence, importance, explicitness, expiry/status and a required `MemorySource` provenance record (actor, source message and conversation). Stable facts about a person use `PERSON_GLOBAL` only after explicit promotion; ordinary group facts remain `CONVERSATION`. Owner facts use `OWNER_GLOBAL`; project decisions use `PROJECT`. Every retrieval API requires `RequestContext` and passes through `MemoryVisibility`; there is intentionally no unscoped `findAll` retrieval. SQLite text lookup uses FTS5, not embeddings. Soft deletion preserves auditability. Raw platform metadata is archival/debugging context, never an authorization or identity key.

## Context and lifecycle

`ContextBuilder` accepts separate persona and simulation prompts, actor/conversation/address context, scope-filtered memories, recent messages, task state and current message. It applies a configurable recent-message cap and lifecycle thresholds but never calls a model. Defaults are soft 60k, rollover 80k and hard 120k tokens; memory, summary and recent-message budgets remain independently configurable. `SessionLifecycleManager` and `SessionHandoff` define persistence boundaries for summaries, people, relationships, open tasks, decisions, facts and recent context; model-generated summary work is out of scope.

## Prompt layers

`PromptProfile` stores two independent fields: `simulationPrompt` (behavior, safety, tool and group-chat rules) and `personaPrompt` (identity, voice and style). Each layer receives its own numbered, checksummed version history. Rollback restores only its selected layer and creates a new history entry; it does not overwrite the other layer. The current repository is an in-memory domain implementation; durable repository/service and UI remain later work.

## Agent capability boundary

The ordinary Social role receives only explicitly enumerated QQ, memory, sticker and slang capabilities. Host/system capabilities (`SHELL`, `SSH`, `DOCKER`, arbitrary filesystem and process control) are absent. A closed-agent role receives no elevated capability absent an explicit future owner-authorized path; Phase 1 does not connect this policy to DSH or execute tools.

## Persistence and application

Spring Boot provides typed configuration and the health endpoint. SQLite + JDBC and Flyway V1 define the first schema; FTS5 holds searchable memory text. WAL and foreign-key enforcement are required storage invariants. Secret material is absent from ordinary typed configuration and repository files. The first application is intentionally small and single-process; Redis, PostgreSQL, document/vector stores, queues and microservices are excluded.

## Phase 4A implementation status

Phase 4A builds a local SQLite-backed social/DSH runtime: stable session mappings, interaction correlation, simulation/wake state, per-conversation FIFO dispatch, durable turn journal and effect ledgers. Production adapter construction stays opt-in; DSH, OneBot, model, and social switches default false. Nothing in this phase authorizes a real external connector. DSH contract evidence is in [DSH_RC2_CONTRACT.md](research/DSH_RC2_CONTRACT.md). Phase 4A final gate status and stress evidence are in [PHASE4A_REPORT.md](PHASE4A_REPORT.md).

### Social runtime concurrency and identity

One conversation key is processed by one FIFO lane; a bounded four-worker pool allows distinct conversations to proceed concurrently while a model/tool call is blocked. SQLite uses WAL with immediate transaction acquisition so concurrent conversation transactions serialize only at the database write boundary rather than upgrading stale deferred snapshots. Wake policy consumes the resolved stable identity flags (including configured owner/self), not transport-supplied display metadata. Runtime-level stress and regression coverage are listed in the Phase 4A report.

Conversation reset is a two-phase operation: (1) a control interrupt advances the durable generation, cancels the active turn token and fences stale callbacks; (2) WAIT/transient cleanup, DSH session stop/archive and mapping retirement run on the same-conversation serialized lane. Reset does not wait for an in-flight model call to return before invalidating its generation. Cleanup is protected by a durable `RESETTING` ticket so a process restart can retry it.

### Runtime ownership

`ProductionRuntimeConfiguration` owns the real `DeepSeekProvider`, `OneBotV11Gateway`, and `DshRc2Adapter`; `RuntimeGraphConfiguration` owns shared domain/use-case composition and uses SQLite repositories for memory, identity, session lifecycle, simulation state, session mappings, tool claims, usage, and traces. `TestRuntimeConfiguration` is test-source-only and owns loopback Fake DSH/OneBot servers, a fixture `ModelProvider`, and controllable `Clock`/`Scheduler`. The integration graph test confirms the production types are constructed without network requests when switches are false. The default model is never invoked on startup.

Closed-agent session creation and prompt submission require `ClosedAgentAccessPolicy` through `DshSessionService.ensureClosedAgent` / `promptClosedAgent`: only a certain identity, owner, private conversation and `CLOSED_AGENT` mode pass. The low-level session creation function is private. This does not grant additional `SocialAgent` capabilities.

## Future single Java Web Console requirements

The eventual console is inside the XingChen Java application, simple, light/dark capable and mobile usable without reducing legacy administration capability. Sections: overview; sessions; people; address/relationships; memory search/edit/forget/source/scope/person links; persona and simulation prompt editing/history/diff/rollback; group/social controls; slang; stickers; voice; agent; model; token/cost; access control; security; logs; operations. It must expose QQ IDs separately from display-name history, membership/group relations and relationship terms. The full legacy matrix remains the coverage contract; ambiguous controls remain `NEEDS_REVIEW` until examined.

Phase 4B-1 establishes the authenticated single-JAR Console shell, session/CSRF boundary, configuration/audit foundation, overview and design system. Implementation details and non-goals are in [CONSOLE_ARCHITECTURE.md](CONSOLE_ARCHITECTURE.md).

Phase 4B-3a adds the authenticated `PeopleMemoryConsoleService` read/write facade and management routes for stable People identity/history, explicit relationship terms, and admin memory. Relationship mutation uses scope validation, optimistic concurrency, deactivation audit, and the runtime's authoritative address priority for effective previews. Memory administration applies bounded, parameterized filters (including UTC `[from,to)` time bounds), no-store detail, provenance, scope-expansion confirmation, and soft forget. Retrieval preview delegates to the existing `MemoryRetriever`, `RequestContext`, and `MemoryVisibility` path; admin FTS search remains a separate cross-person surface and is not exposed as Agent retrieval. Reset tests verify long-term People/relationship/memory persistence. Both `zh-CN` and `en-US` labels and a 375px drawer/logout workflow are covered by fake-profile browser tests.

## Current runtime integration gates

The ordinary QQ social path is `Social enabled AND OneBot enabled AND Model enabled`. DSH is not a prerequisite for `SocialRuntime`; its interaction integration is controlled independently and remains off when DSH is disabled. This is a runtime capability statement, not a claim that a particular production deployment has completed its OneBot cutover. Startup logs report the four integration switches separately and state whether `SocialRuntime` started or which required dependency is disabled.

For Docker deployments, Core reaches an independently operated OneBot gateway through its service DNS name, not `127.0.0.1` (which refers to Core itself). See [OneBot adapter](ONEBOT_ADAPTER.md) and [Deployment](DEPLOYMENT.md) for the transport and network boundary.

## Quality and security invariants

- Never identify a person by nickname/card.
- Never promote conversation memory to person-global implicitly.
- Never retrieve memory without scope checks and request identity.
- Never store long-term memory without provenance.
- Never hard-code a real QQ ID, credential or secret in the domain/repository.
- Keep controllers transport-only; keep SQL inside storage adapters.
- No production integration or deployment in Phase 1.
