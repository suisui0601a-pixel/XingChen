# Phase 2 context pipeline

`ContextBuildInput` carries prompt layers, actor/display identity, assistant address, scoped memory candidates, summary, recent messages, task state, tool description, current message, and a `RequestContext`. `ContextBuilder` rechecks memory visibility and applies a deterministic approximate token estimator.

Rendered order: simulation policy, persona, identity, relationship/address, relevant memories, conversation summary, task state, tools, recent messages, current message. The builder reserves hard-limit space for fixed prompt and current-turn sections, then applies summary/memory/recent budgets. Memory ranking and recent-message truncation are deterministic. Diagnostics expose estimated counts, included-memory IDs, excluded count, and lifecycle (`NORMAL`, soft threshold, rollover-required, hard limit); V2 SQLite stores aggregate counts and lifecycle, not message text or secrets.

On rollover, the orchestrator asks only the injected `SummaryProvider` abstraction (the Phase 2 implementation is deterministic mock summarization), saves a handoff via `SessionLifecycleManager`, clears the recent window, and rebuilds context with stable actor/address/memories and summary retained. No live model call is made for summarization.

## Phase 4A persistence boundary

`SocialRuntime` composes persistent simulation wake/sleep/cursor/generation state with accepted OneBot events, Agent decisions, side effects, WAIT continuation and durable turn recovery. WAIT and outbound ledgers survive restart; late/duplicate events remain fenced by ordering/idempotency. Exact mixed concurrency/reset-race closure remains pending; see [PHASE4A_REPORT.md](PHASE4A_REPORT.md).
