CREATE TABLE conversation_reset_controls (
  conversation_id TEXT PRIMARY KEY REFERENCES conversations(id),
  reset_epoch INTEGER NOT NULL CHECK(reset_epoch >= 1),
  generation INTEGER NOT NULL CHECK(generation >= 1),
  status TEXT NOT NULL CHECK(status IN ('RESETTING','COMPLETE')),
  updated_at TEXT NOT NULL
);
CREATE INDEX conversation_reset_status_idx ON conversation_reset_controls(status,updated_at);
