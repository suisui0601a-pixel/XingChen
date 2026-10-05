ALTER TABLE token_usage ADD COLUMN provider TEXT NOT NULL DEFAULT 'unknown';
ALTER TABLE context_diagnostics ADD COLUMN tool_result_tokens INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_token_usage_provider_time ON token_usage(provider,usage_time DESC);

CREATE TABLE stickers (
  id TEXT PRIMARY KEY, path TEXT NOT NULL, sha256 TEXT NOT NULL UNIQUE,
  tags_json TEXT NOT NULL DEFAULT '[]', note TEXT NOT NULL DEFAULT '',
  usage_count INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL,
  created_at TEXT NOT NULL, last_used_at TEXT
);
CREATE INDEX idx_stickers_usage ON stickers(usage_count DESC,last_used_at DESC);

CREATE TABLE slang_entries (
  id TEXT PRIMARY KEY, term TEXT NOT NULL, meaning TEXT NOT NULL,
  usage_text TEXT NOT NULL DEFAULT '', example_text TEXT NOT NULL DEFAULT '', risk TEXT NOT NULL DEFAULT '',
  sources_json TEXT NOT NULL DEFAULT '[]', evidence_json TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL CHECK(status IN ('CANDIDATE','CONFIRMED','REJECTED')),
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE INDEX idx_slang_status_term ON slang_entries(status,term);

CREATE TABLE agent_traces (
  id TEXT PRIMARY KEY, conversation_id TEXT, event_id TEXT, actor_id TEXT,
  memory_ids_json TEXT NOT NULL DEFAULT '[]', context_tokens INTEGER NOT NULL DEFAULT 0,
  memory_tokens INTEGER NOT NULL DEFAULT 0, recent_tokens INTEGER NOT NULL DEFAULT 0,
  summary_tokens INTEGER NOT NULL DEFAULT 0, tool_result_tokens INTEGER NOT NULL DEFAULT 0,
  provider TEXT, model TEXT, tool_calls_json TEXT NOT NULL DEFAULT '[]',
  model_call_count INTEGER NOT NULL DEFAULT 0, decision TEXT NOT NULL, outgoing_actions_json TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL
);
CREATE INDEX idx_agent_traces_conversation ON agent_traces(conversation_id,created_at DESC);
