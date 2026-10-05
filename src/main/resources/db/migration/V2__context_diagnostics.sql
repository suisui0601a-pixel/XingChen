CREATE TABLE context_diagnostics (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id),
  total_tokens INTEGER NOT NULL, persona_tokens INTEGER NOT NULL, simulation_tokens INTEGER NOT NULL,
  memory_tokens INTEGER NOT NULL, recent_tokens INTEGER NOT NULL, summary_tokens INTEGER NOT NULL,
  included_memory_count INTEGER NOT NULL, excluded_memory_count INTEGER NOT NULL,
  lifecycle_state TEXT NOT NULL, updated_at TEXT NOT NULL
);
