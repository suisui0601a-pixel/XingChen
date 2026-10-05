CREATE TABLE onebot_outbound_executions (
  execution_key TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  turn_id TEXT NOT NULL,
  generation INTEGER NOT NULL CHECK(generation >= 0),
  logical_message_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('PENDING','SUCCESS','FAILED','UNKNOWN')),
  provider_message_id TEXT,
  failure_reason TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  dispatch_started_at TEXT
);
CREATE INDEX onebot_outbound_conversation_status_idx
  ON onebot_outbound_executions(conversation_id,status,updated_at);
