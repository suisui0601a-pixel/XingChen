# Phase 2 implementation report

## Scope and safety boundary

Phase 2 adds a local-only social-agent pipeline using deterministic mocks. It does not connect to QQ, SnowLuma, DSH, DeepSeek, or production services. `DeepSeekProvider` is deliberately non-networked. The application binds loopback by default; the Console endpoints are read-only and are not an authenticated public administration UI.

## Implemented foundations

- OneBot v11 message/notice normalization preserves stable platform IDs, group/private identity, roles, reply/mention/media/forward/poke metadata. `OneBotGateway` defines connection, send/reply, recent/unread, member lookup and poke operations; only `MockOneBotGateway` is implemented.
- Identity IDs are deterministic from platform identifiers. Incoming events transactionally persist people, conversations, membership snapshots, aliases, raw event metadata and message IDs; repeated platform/message IDs are idempotent.
- Retrieval enforces request scope and budgets. Candidate policy binds memory to the actor, restricts owner/project scope, downgrades member project decisions, and requires source provenance.
- Context composition has stable section ordering, approximate token budgets, memory/recent clipping, diagnostics, and mock rollover/handoff.
- Social agent decisions, mock model/stream/usage and an explicit safe tool catalog. Unknown/disallowed tool calls fail closed.
- Interface-only DeepSeek adapter; no credential loading or HTTP behavior.
- Read-only `/api/status`, `/api/people`, `/api/people/{id}`, `/api/conversations`, `/api/memories`, `/api/prompts`, `/api/context/diagnostics`; `/health` reports database/memory/agent state.
- Flyway V2 adds aggregate context diagnostics.

## Reserved-mode decision

The pinned legacy v0.1.7 reserved mode is a selectable V1 social simulation mode, not an unknown protocol. Its timing/participation state machine, message segmentation, and `[SILENT]` behavior are explicitly left for the later social-feature phase. Phase 2's generic orchestrator is not claimed as V1 behavior parity. `reserved2` remains a separate future feature.

## Coverage mapping

| Capability | Contract tests |
|---|---|
| OneBot event normalization and identity metadata | `OneBotEventNormalizerTest` EVT-001..010 |
| Actor/owner/self resolution and address selection | `IdentityResolverContractTest` IDR-* |
| Memory ranking, visibility and candidate policy | `MemoryPipelineContractTest` MEM-* |
| Context order, budgets and diagnostics | `ContextPipelineContractTest` CTX-* |
| Agent decisions, tool allowlist and mock streaming | `SocialAgentContractTest` AGENT-/TOOL-* |
| Event-to-reply orchestration, duplicate, scope, rollover, ownership | `ConversationOrchestratorE2ETest` E2E-/MSG-/SCOPE-/ROLLOVER-/OWNER-/TOOL-* |
| Read-only Console and health APIs | `ConsoleReadApiContractTest` API-* |
| Phase 1 foundation contracts | existing 20 Phase 1 tests |

Feature matrix status vocabulary is normalized to `PLANNED`, `FOUNDATION`, `IMPLEMENTED`, `TESTED`, `NEEDS_REVIEW`. `FOUNDATION` means a reusable primitive exists but the end-user feature/integration is incomplete. No Phase 3 work is included.
