CREATE TABLE dsh_session_mappings (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  workspace_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  mode TEXT NOT NULL,
  preset TEXT NOT NULL,
  model TEXT NOT NULL,
  policy_hash TEXT NOT NULL,
  generation INTEGER NOT NULL CHECK(generation > 0),
  status TEXT NOT NULL CHECK(status IN ('CURRENT','RETIRED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(conversation_id,generation)
);
CREATE UNIQUE INDEX idx_dsh_one_current_session ON dsh_session_mappings(conversation_id) WHERE status='CURRENT';
