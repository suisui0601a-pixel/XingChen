# Context and session lifecycle

The pure `ContextBuilder` composes separate persona/simulation instructions with actor, conversation, resolved relationship address, scope-filtered memory candidates, bounded recent messages, task state and current message. It does not call a model. All returned memory candidates are checked using the supplied request context.

`ContextBudget` is typed and configurable: defaults are soft=60,000, rollover=80,000, hard=120,000 tokens; recent-message, memory and summary budgets are separate. State transitions are NORMAL below soft, SOFT_LIMIT at soft, ROLLOVER_REQUIRED at rollover and HARD_LIMIT above hard. `SessionHandoff` carries summary, active topics, people, relationships, open tasks, decisions, important facts and recent context. The lifecycle interface defines load/save/evaluate; summary generation is future work.
