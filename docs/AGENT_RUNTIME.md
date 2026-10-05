# Agent runtime lifecycle

## Event ownership and concurrency

`SocialRuntime` hands accepted input to a bounded dispatcher. A conversation key has one FIFO lane; a fixed worker pool allows distinct conversations to execute concurrently. This is correctness isolation rather than a throughput promise. Queue saturation fails closed by dropping the newly submitted event with a warning; diagnostics report aggregate pending depth and active lane count. Duplicate inbound execution keys are rejected by persistent claims before Agent work.

## Wake and reserved2

Incoming triggers are collected into a set of wake reasons (mention, bot-name, question, poke, configured speaker, manual wake, or WAIT continuation). The trigger policy evaluates an event view carrying `IdentityRegistry`-resolved self/owner flags; OneBot transport actor metadata alone is not authoritative for owner wake. Reasons are persisted as metadata, while one accepted input owns at most one logical turn. Sleeping ordinary input is archived/read without an Agent call; directed wake transitions to AWAKE. `NO_REPLY` advances the read cursor and records no action without sending. `WAIT` persists the origin turn and advances generation; a later accepted message claims the WAIT continuation by compare-and-set, exactly once.

## Durable turn recovery

The journal records generation, model state, output and recovery status. `NOT_STARTED`, `STREAMING_NO_EFFECT`, and safe `TOOL_PROPOSED` may be recovered only while the generation matches and no effect ledger claim exists. `TOOL_EXECUTED` is fail-closed and becomes interrupted; no whole-turn replay is attempted. `OUTPUT_EMITTED` resumes only the serialized output through the outbound idempotency ledger. Confirmed send is not repeated; `UNKNOWN` is never automatically resent. Unknown outcomes are retained for audit.

## Reset boundary

Reset has two ordered phases. **Control interrupt:** an authenticated owner-private `/reset` increments generation under the active-turn registry fence, cancels the active turn token, advances an in-process lane epoch so earlier queued callbacks are discarded, and persists a `RESETTING` marker. **Serialized cleanup:** reset work is queued into the same conversation lane; it retires WAIT and pending DSH interactions, marks prior-generation turns stale, stops/archives the DSH session, retires its mapping, and clears only transient context/cursor state. A durable pending marker is replayed into cleanup on restart. Stable people, alias/membership history, relationships, long-term memories, and prompt versions survive. Current barrier and Fake OneBot/SocialRuntime reset coverage is indexed in `docs/PHASE4A_REPORT.md`; the feature matrix distinguishes runtime reset behavior from future Console maintenance UI.

## Scheduler applicability

There is currently no scheduled social wake callback in this runtime. WAIT continuation is event-driven by a later inbound message and fenced by the persisted conversation generation. The reserved-mode timer changes mode only; it does not schedule or resume a social turn. Therefore RESET-SCHED-001 is **NOT APPLICABLE** to the current runtime graph, rather than an untested callback. If a scheduled wake is introduced later, its callback must capture its origin generation and revalidate that generation at execution time before changing state or starting work.

## Diagnostics

`/api/runtime` is read-only and returns bounded counts and adapter enabled flags only. It must not serialize environment values, API keys, cookies, OneBot tokens, full prompts, memory contents, or raw platform payloads. Counts include active/waiting/queued work, unknown outbound results and interrupted/recoverable turns. Stress drain evidence and current closure gate state are maintained in `docs/PHASE4A_REPORT.md`.

## Prompt version snapshot

At the start of each accepted Agent turn, `PromptSnapshotProvider` reads the active Persona and Simulation version IDs, sequence numbers and bodies in one query. `ConversationOrchestrator` retains that immutable snapshot through initial context construction and any same-turn rebuild after rollover/tool results. Saving or rolling back an admin prompt changes persisted active pointers atomically; it affects the next turn and cannot change an in-flight turn. The per-turn context metadata carries the captured version IDs. The hard security policy remains a separate first system message and is not an editable prompt version.

## Social and model configuration snapshot

`SocialSettingsService.capture` resolves global defaults plus conversation overrides before trigger evaluation. One immutable `SocialSettingsSnapshot` is reused for trigger policy, ordinary-message participation and reply burst/rate/gap controls through the accepted turn; hot-applied settings are visible to the next turn. `AgentExecutor` similarly captures `ModelProvider.snapshot()` once before model/tool rounds, so provider/model/options/credential generation cannot change midway through a turn. Missing or disabled provider state produces a safe unavailable result. These settings do not create a proactive scheduler or automatic idle timer, and they do not change WAIT, CLOSED_AGENT, or owner-private safety transitions.
