CREATE TABLE dsh_pending_interactions (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK(kind IN ('QUESTION','APPROVAL')),
  dsh_session_id TEXT NOT NULL,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  client_id TEXT NOT NULL,
  client_generation INTEGER NOT NULL CHECK(client_generation > 0),
  event_id TEXT NOT NULL,
  required_actor_id TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('PENDING','ANSWERING','APPROVED','REJECTED','ANSWERED','EXPIRED','CANCELLED','INTERRUPTED','UNKNOWN')),
  questions_json TEXT,
  tool_name TEXT,
  call_id TEXT,
  reason TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(dsh_session_id,event_id)
);
CREATE INDEX idx_dsh_pending_interactions_actor ON dsh_pending_interactions(required_actor_id,status,expires_at);

CREATE TABLE dsh_event_result_ledger (
  execution_key TEXT PRIMARY KEY,
  dsh_session_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  logical_outcome_version TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('RESERVED','SUCCESS','FAILED','UNKNOWN')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
