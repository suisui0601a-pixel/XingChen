CREATE TABLE pending_questions (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  session_id TEXT NOT NULL,
  requesting_actor TEXT NOT NULL,
  preset TEXT NOT NULL,
  expected_responder TEXT NOT NULL,
  question TEXT NOT NULL,
  options_json TEXT NOT NULL DEFAULT '[]',
  expires_at TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('PENDING','ANSWERED','EXPIRED','CANCELLED')),
  response TEXT,
  responded_by TEXT,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_pending_question_lookup ON pending_questions(conversation_id,status,expires_at);

CREATE TABLE pending_approvals (
  approval_id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  tool_name TEXT NOT NULL,
  reason TEXT NOT NULL,
  required_actor TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('PENDING','APPROVED','DENIED','EXPIRED')),
  decided_by TEXT,
  decision TEXT,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_pending_approval_lookup ON pending_approvals(conversation_id,status,expires_at);
