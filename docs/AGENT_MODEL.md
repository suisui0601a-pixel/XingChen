# Phase 2 agent boundary

`SocialAgent` consumes an `AgentRequest` and a `ModelProvider`; it can return no reply, a single reply, an ordered multi-reply, or a typed tool call. The deterministic `MockModelProvider` is the only enabled provider in this phase. Streaming is represented by `ModelStreamEvent` and mock usage records; the `DeepSeekProvider` is interface/configuration scaffolding only and intentionally performs no HTTP request and reads no key.

`AgentToolCatalog` is an explicit fixed allowlist: `qq.readRecent`, `qq.readUnread`, `qq.send`, `qq.reply`, `qq.wait`, `memory.search`, `memory.remember`, `memory.setAddress`, and `memory.getPerson`. A tool call must be both catalogued and allowed by the request's capability set. Unknown tools (including shell, filesystem, process, SSH, or Docker operations) fail closed. `MockToolExecutor` uses only `MockOneBotGateway`; it does not connect to a platform.

Conversation orchestration is `ConversationOrchestrator`: normalize upstream event, resolve actor/conversation, persist idempotently, build scoped memory/context, run a decision, apply memory policy, and route output to the gateway abstraction. Group lifecycle notices are persisted but do not invoke the social model. Duplicate platform/message IDs do not invoke the model or send twice.

This local pipeline is not live QQ/DSH/DeepSeek production parity. Phase 4A composes the agent into the local reserved2 runtime; detailed lifecycle and recovery boundaries are in [AGENT_RUNTIME.md](AGENT_RUNTIME.md).

## Phase 4A retry safety note

`ModelTurnExecutionState` treats provider deltas as buffered model output until an external send is confirmed. Once a tool executes, an outbound reply is confirmed, or delivery is `UNKNOWN`, replay is unsafe and the turn fails closed. Durable turn, tool and outbound ledgers enforce that policy locally; UNKNOWN is not automatically resent.
