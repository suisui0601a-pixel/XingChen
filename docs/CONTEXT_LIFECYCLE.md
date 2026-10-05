# Context and conversation lifecycle

## Transient versus durable context

Recent conversation window, summaries, handoffs, DSH session mapping and estimated token state are short-context/runtime artifacts. Reset first advances generation/signals cancellation in the control path and writes a durable `RESETTING` marker; serialized cleanup later retires those artifacts and WAIT. Startup requeues unfinished reset markers. WAIT continuation and old durable turn recovery are fenced by generation comparison. Conversation cursor resets to an empty baseline, so subsequent events establish a new transient sequence.

Person, stable platform identity, alias history, membership history, relationship terms, long-term memory with provenance, persona prompt, simulation prompt, and version history are not short context and must survive reset. Memory retrieval remains scoped by `RequestContext`/`MemoryVisibility`; PRIVATE and conversation-scoped data must not cross actor or conversation boundaries. Relationship addressing uses stable person IDs and current policy resolution, not display-name identity.

## Rollover

`ContextBuilder` constructs the prompt from policy/persona layers, identity/address, scoped memory, summary, task state, tools, recent messages, and current input. Soft/rollover/hard thresholds remain deterministic. Rollover writes a handoff and clears the bounded recent window; it does not delete long-term memory or prompt versions.

## Read cursor and late/duplicate input

Duplicate event IDs do not create a second logical turn. Numeric cursors may advance monotonically; an unorderable ID does not move the cursor. Late input is archived according to event ordering policy and is not allowed to leapfrog accepted work in its conversation lane. Cursor/generation behavior is validated through local SQLite contract tests and the current 24-event same-conversation stress; the final Phase 4A gate status is recorded in `PHASE4A_REPORT.md`.
