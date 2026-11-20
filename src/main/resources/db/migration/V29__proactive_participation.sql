-- Timer admission/output journal. No user-message, prompt or credential copies.
CREATE TABLE proactive_attempts (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  source_message_id TEXT NOT NULL,
  turn_id TEXT NOT NULL UNIQUE,
  checked_at TEXT NOT NULL,
  local_day TEXT NOT NULL,
  admitted INTEGER NOT NULL CHECK(admitted IN (0,1)),
  status TEXT NOT NULL CHECK(status IN ('SKIPPED_RANDOM','CLAIMED','SENT','NO_REPLY','CANCELLED','FAILED','UNKNOWN','INTERRUPTED','RATE_LIMITED')),
  updated_at TEXT NOT NULL,
  output_json TEXT
);
CREATE INDEX proactive_conversation_time_idx ON proactive_attempts(conversation_id,checked_at DESC);
CREATE INDEX proactive_budget_time_idx ON proactive_attempts(admitted,checked_at DESC);
CREATE INDEX proactive_status_idx ON proactive_attempts(status);
CREATE INDEX proactive_recent_idx ON proactive_attempts(checked_at DESC);
