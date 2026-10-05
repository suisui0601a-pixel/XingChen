# Memory model and privacy

`Memory` stores type, optional subject/conversation/project, content, scope/scope key, confidence, importance, explicitness, timestamps, expiry and active/soft-deleted status. Types include episodic, semantic, person, relationship, project, task, preference and conversation summary. Every long-term save requires at least one `MemorySource` with platform, source message, actor, conversation and timestamp.

Scopes: `GLOBAL`, `OWNER_GLOBAL`, `PERSON_GLOBAL`, `CONVERSATION`, `PROJECT`, `PRIVATE`. Ordinary group facts default to conversation; person-global facts require promotion; owner personal facts use owner-global; decisions use project scope. Queries take `RequestContext(requestingPerson, conversation, ownerStatus, projectKey)` and pass `MemoryVisibility`; retriever candidate queries are re-filtered before ranking. Another group cannot see conversation facts. Personal/private facts are visible to the subject or owner; owner-global facts only to owner. Expired and soft-deleted records are excluded. SQLite FTS5 supplies text candidates; no embeddings are used.

Contract tests: MEM-001 subject attribution is explicit, MEM-002 provenance is required, MEM-003 conversation scope cannot cross groups, MEM-004 person-global access is by stable person identity.
