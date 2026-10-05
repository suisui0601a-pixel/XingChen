CREATE TABLE inbound_turn_executions (
  execution_key TEXT PRIMARY KEY,
  platform TEXT NOT NULL,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  incoming_event_id TEXT NOT NULL,
  turn_id TEXT NOT NULL,
  generation INTEGER,
  status TEXT NOT NULL CHECK(status IN ('PERSISTED','CLAIMED','COMPLETED','FAILED')),
  event_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(platform, conversation_id, incoming_event_id)
);
CREATE INDEX inbound_turn_status_created_idx ON inbound_turn_executions(status, created_at);
