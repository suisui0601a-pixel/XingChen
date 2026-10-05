# Social runtime

The runtime boundary subscribes to `OneBotGateway`, classifies each event with `SocialTriggerPolicy`, applies the independent `ParticipationPolicy`, persists even silently-gated events through the orchestrator, and dispatches accepted work through a bounded fixed worker pool with FIFO lanes per conversation. Conversations can run concurrently; one conversation cannot overtake itself. Saturation fails closed. It owns connection lifecycle only; identity resolution, memory policy, context construction, model/tool execution and outgoing sends remain in their respective services.

`ConversationOrchestrator` supports the existing deterministic mock pipeline and the provider-neutral `AgentExecutor` pipeline. The latter rebuilds context before each model round, records model usage and metadata-only traces, applies tool capabilities and returns final decisions. Message IDs remain idempotency keys. `NO_REPLY` is an ordinary decision.

The V1 reserved state machine uses injected `Clock` and `Scheduler`; it has observing, active, idle and exited states. Runtime wiring is deliberately opt-in; `xingchen.social.enabled=false` by default and no OneBot connection is attempted by application startup.

The durable Phase 4A journal distinguishes safe pre-effect recovery, effect-bearing/interrupted turns, emitted output, and outbound certainty; UNKNOWN is never resent. WAIT continuation uses a persisted generation claim, while NO_REPLY advances the read cursor without output. Owner-private `/reset` runs in the conversation lane and retires transient state. Final same-conversation stress and active-model reset-race tests are still required before Phase 4A can close; the matrix is not a claim of full legacy parity.
