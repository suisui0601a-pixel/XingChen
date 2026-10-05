CREATE TABLE conversation_console_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  occurred_at TEXT NOT NULL,
  actor TEXT NOT NULL,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  operation TEXT NOT NULL CHECK(operation IN ('MODE_CHANGED','MANUAL_WAKE','RESET')),
  old_summary TEXT NOT NULL DEFAULT '',
  new_summary TEXT NOT NULL DEFAULT '',
  result TEXT NOT NULL
);
CREATE INDEX conversation_console_audit_time_idx ON conversation_console_audit(occurred_at DESC);
