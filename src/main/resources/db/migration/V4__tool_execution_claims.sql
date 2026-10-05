CREATE TABLE tool_execution_claims (
  event_id TEXT NOT NULL,
  call_id TEXT NOT NULL,
  claimed_at TEXT NOT NULL,
  PRIMARY KEY(event_id, call_id)
);
