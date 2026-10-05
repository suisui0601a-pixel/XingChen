ALTER TABLE session_state ADD COLUMN generation INTEGER NOT NULL DEFAULT 0;
ALTER TABLE session_state ADD COLUMN current_summary_id TEXT;
ALTER TABLE session_state ADD COLUMN last_rollover_at TEXT;
ALTER TABLE session_state ADD COLUMN estimated_context_tokens INTEGER NOT NULL DEFAULT 0;

CREATE TABLE session_handoffs (
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  generation INTEGER NOT NULL,
  summary_id TEXT NOT NULL UNIQUE,
  handoff_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY(conversation_id,generation)
);
