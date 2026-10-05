CREATE TABLE durable_turn_executions (
  turn_id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  inbound_execution_key TEXT NOT NULL UNIQUE REFERENCES inbound_turn_executions(execution_key),
  incoming_event_id TEXT NOT NULL,
  generation INTEGER NOT NULL CHECK(generation >= 0),
  model_turn_state TEXT NOT NULL CHECK(model_turn_state IN ('NOT_STARTED','STREAMING_NO_EFFECT','MODEL_OUTPUT_RECEIVED','TOOL_PROPOSED','TOOL_EXECUTED','OUTPUT_EMITTED','OUTBOUND_OUTPUT_SENT','UNCERTAIN')),
  status TEXT NOT NULL CHECK(status IN ('RUNNING','RECOVERABLE','RECOVERING','INTERRUPTED','COMPLETED','STALE','FAILED')),
  wake_reasons_json TEXT NOT NULL DEFAULT '[]',
  output_type TEXT,
  output_json TEXT,
  recovery_attempts INTEGER NOT NULL DEFAULT 0 CHECK(recovery_attempts >= 0),
  claimed_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX durable_turn_recovery_idx ON durable_turn_executions(status, model_turn_state, updated_at);
CREATE INDEX durable_turn_conversation_idx ON durable_turn_executions(conversation_id, generation, status);
